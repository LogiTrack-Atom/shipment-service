"""Comprueba el broker real en un laboratorio DESCARTABLE de loopback.

prepare publica un mensaje persistente con confirm; reiniciar el broker conservando
volumen; verify consume ese mensaje, verifica permisos, retry y DLQ. No usar en una
instalación compartida: el ensayo usa las colas reales del contrato.
"""
import argparse
import json
from pathlib import Path
import sys
import time
import uuid

import pika

ROOT = Path(__file__).resolve().parents[2]


def main():
    """Ejecuta una fase sin mostrar credenciales, payloads ni excepciones remotas."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['prepare', 'verify'])
    parser.add_argument('--credentials', type=Path, required=True)
    parser.add_argument('--state', type=Path, required=True)
    parser.add_argument('--host', choices=['127.0.0.1', 'localhost'], default='127.0.0.1')
    parser.add_argument('--port', type=int, default=5672)
    args = parser.parse_args()
    passwords = json.loads(args.credentials.read_text())
    topology = json.loads((ROOT / 'docs/sprint04/asignacion-asincrona/topology.json').read_text())
    flow = topology['flows']['commands']
    counts = {'assertions': 0, 'publisher_confirms': 0}

    def check(value):
        counts['assertions'] += 1
        if not value:
            raise AssertionError('Verificación de broker incompleta.')

    def connect(user):
        return pika.BlockingConnection(pika.ConnectionParameters(
            host=args.host, port=args.port, virtual_host=topology['vhost'],
            credentials=pika.PlainCredentials(user, passwords[user]),
            connection_attempts=1, socket_timeout=10, blocked_connection_timeout=10, heartbeat=30))

    def publish(user, exchange, key, body):
        with connect(user) as connection:
            channel = connection.channel()
            channel.confirm_delivery()
            channel.basic_publish(exchange, key, body,
                                  pika.BasicProperties(content_type='application/json', delivery_mode=2,
                                                       message_id=json.loads(body)['messageId'],
                                                       correlation_id=json.loads(body)['operationId']), mandatory=True)
            counts['publisher_confirms'] += 1

    def receive(user, queue, expected_id, reject=False):
        with connect(user) as connection:
            channel = connection.channel()
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                method, properties, body = channel.basic_get(queue, auto_ack=False)
                if method:
                    if properties.message_id != expected_id:
                        channel.basic_nack(method.delivery_tag, requeue=True)
                        raise AssertionError('Cola con datos ajenos; detener ensayo sin consumirlos.')
                    check(properties.delivery_mode == 2)
                    check(json.loads(body)['messageId'] == expected_id
                          and properties.correlation_id == json.loads(body)['operationId']
                          and properties.content_type == 'application/json')
                    if reject:
                        channel.basic_reject(method.delivery_tag, requeue=False)
                    else:
                        channel.basic_ack(method.delivery_tag)
                    return
                connection.sleep(0.2)
            raise AssertionError('El mensaje de prueba no llegó dentro del límite.')

    if args.phase == 'prepare':
        check(not args.state.exists())
        command = json.loads((ROOT / 'docs/sprint04/asignacion-asincrona/examples/command.json').read_text())
        command['messageId'] = str(uuid.uuid4())
        # Este ensayo prueba transporte, no ejecuta el consumidor de negocio.
        publish('lt_shipment', topology['exchanges']['commands'], flow['routing_key'], json.dumps(command))
        args.state.write_text(json.dumps({'messageId': command['messageId']}))
        args.state.chmod(0o600)
    else:
        state = json.loads(args.state.read_text())
        receive('lt_courier', flow['queue'], state['messageId'])
        # Reconexión tras reinicio y publicación con los usuarios restringidos.
        message = json.loads((ROOT / 'docs/sprint04/asignacion-asincrona/examples/command.json').read_text())
        message['messageId'] = str(uuid.uuid4())
        started = time.monotonic()
        publish('lt_courier', topology['exchanges']['retry'], flow['retry_routing_key'], json.dumps(message))
        receive('lt_courier', flow['queue'], message['messageId'], reject=True)
        check(time.monotonic() - started >= 4.5)
        receive('lt_operator', flow['dead_queue'], message['messageId'])
        for action in ('publish_result', 'consume_result', 'declare_queue'):
            with connect('lt_shipment') as connection:
                channel = connection.channel()
                denied = False
                try:
                    if action == 'publish_result':
                        channel.confirm_delivery()
                        channel.basic_publish(topology['exchanges']['results'], 'availability.result', b'{}', mandatory=True)
                    elif action == 'consume_result':
                        channel.basic_get(flow['queue'])  # Shipment no puede consumir la cola de Courier.
                    else:
                        channel.queue_declare('lt.forbidden.test.v1', durable=True)
                except pika.exceptions.ChannelClosedByBroker as error:
                    denied = error.reply_code == 403
                check(denied)
        with connect('lt_operator') as connection:
            channel = connection.channel()
            check(channel.queue_declare(flow['queue'], passive=True).method.message_count == 0)
            check(channel.queue_declare(flow['dead_queue'], passive=True).method.message_count == 0)
    print(json.dumps({'ok': True, 'phase': args.phase, **counts}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Ensayo de broker incompleto: ' + type(error).__name__ + '. No se muestran datos privados.', file=sys.stderr)
        sys.exit(1)
