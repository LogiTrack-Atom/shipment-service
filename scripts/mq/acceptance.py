#!/usr/bin/env python3
"""Aceptación destructiva SOLO de un proyecto Compose local descartable de RabbitMQ.

Usar --project lt-s04-mq<identificador> --private-dir /directorio/externo.
El directorio debe contener private.env y broker/{broker.env,application.env,credentials.json}.
Detiene broker/Courier, agrega datos ficticios y un trigger temporal; no sirve para producción.
Nunca imprime contraseñas, JWT, cuerpos AMQP ni el estado privado del ensayo.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import time
import uuid
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    """Impide reenviar autenticación a otro destino."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class UnexpectedStatus(AssertionError):
    """Distingue indisponibilidad de Gateway de una respuesta de contrato inesperada."""

    def __init__(self, status, method, path, expected):
        super().__init__(f'{method} {path}: HTTP {status}, esperado {expected}')
        self.status = status


def main():
    """Ejecuta escenarios sobre servicios reales y emite únicamente un resumen público."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    parser.add_argument('--private-dir', type=Path, required=True)
    args = parser.parse_args()
    if not args.project.startswith('lt-s04-mq') or not args.project.replace('-', '').isalnum():
        parser.error('El nombre debe identificar el laboratorio descartable lt-s04-mq*.')
    root = Path(__file__).resolve().parents[2]
    private = args.private_dir.resolve()
    if private.is_relative_to(root) or private.stat().st_mode & 0o077:
        parser.error('Directorio privado externo requerido, con modo 0700.')

    def envfile(path):
        return dict(line.split('=', 1) for line in path.read_text().splitlines()
                    if line and not line.startswith('#') and '=' in line)

    values = envfile(private / 'private.env')
    broker = envfile(private / 'broker/broker.env')
    app = envfile(private / 'broker/application.env')
    if values['COMPOSE_PROJECT_NAME'] != args.project or broker['COMPOSE_PROJECT_NAME'] != args.project:
        parser.error('Los archivos deben corresponder al mismo laboratorio.')
    if app.get('ASSIGNMENT_ENABLED') != 'true':
        parser.error('Aplicar las migraciones y activar el módulo antes de probar.')
    base = values['SESSION_ORIGIN']
    url = urlsplit(base)
    if url.scheme != 'http' or url.hostname not in ('localhost', '127.0.0.1') or url.path or url.query or url.fragment or url.username:
        parser.error('SESSION_ORIGIN debe ser un origen de loopback HTTP.')
    management = 'http://127.0.0.1:' + broker['RABBITMQ_MANAGEMENT_PORT']
    credentials = json.loads((private / 'broker/credentials.json').read_text())
    basic = 'Basic ' + base64.b64encode(('lt_operator:' + credentials['lt_operator']).encode()).decode()
    compose = ['docker', 'compose', '--project-name', args.project]
    for file in ['private.env', 'broker/broker.env', 'broker/application.env']:
        compose += ['--env-file', str(private / file)]
    for file in ['compose.sprint04.yaml', 'compose.sprint04-mq.yaml', 'compose.sprint04-assignment.yaml']:
        compose += ['-f', str(root / file)]
    compose += ['--profile', 'mq']
    opener = build_opener(NoRedirect())
    stats = {'http_requests': 0, 'assertions': 0, 'scenarios': []}

    def check(condition, text):
        stats['assertions'] += 1
        if not condition:
            raise AssertionError(text)

    def request(origin, path, method='GET', body=None, headers=None, expected=200):
        stats['http_requests'] += 1
        h = {'Accept': 'application/json', **(headers or {})}
        if body is not None:
            h['Content-Type'] = 'application/json'
        req = Request(origin + path, method=method, headers=h,
                      data=None if body is None else json.dumps(body).encode())
        try:
            response = opener.open(req, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            stats['assertions'] += 1
            if response.code != expected:
                raise UnexpectedStatus(response.code, method, path, expected)
            raw = response.read()
            return (json.loads(raw) if raw else None), response.headers

    def control(*command):
        return subprocess.check_output(compose + list(command), text=True).strip()

    def sql(service, statement, admin=False):
        command = ('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root' if admin else
                   'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER"')
        result = subprocess.run(compose + ['exec', '-T', service + '-mysql', 'sh', '-c',
            command + ' --database="$MYSQL_DATABASE" --batch --skip-column-names'],
            input=statement, text=True, capture_output=True, check=True)
        return result.stdout.strip()

    def eventually(action, predicate, timeout=75):
        deadline = time.monotonic() + timeout
        while True:
            value = action()
            if predicate(value):
                return value
            if time.monotonic() > deadline:
                raise AssertionError('Tiempo agotado esperando recuperación o efecto confirmado.')
            time.sleep(1)

    token = None
    security = '/applications/apisecurity/api/v1/'
    shipments = '/applications/apishipment/api/v1/envios'
    couriers = '/applications/apicourier/api/v1/repartidores'

    def api(path, method='GET', body=None, expected=200, key=None):
        headers = {'Authorization': 'Bearer ' + token} if token else {}
        if key:
            headers['Idempotency-Key'] = key
        return request(base, path, method, body, headers, expected)

    def gateway_instance():
        container = control('ps', '-q', 'api-gateway')
        check(bool(container), 'Gateway debe estar en ejecución')
        started = subprocess.check_output(
            ['docker', 'inspect', '--format', '{{.State.StartedAt}}', container], text=True
        ).strip()
        return container, started

    def gateway_read_after_restart(path):
        # Reintentar solo GET ante indisponibilidad temporal. Los 4xx y otros
        # errores de contrato siguen fallando inmediatamente.
        def probe():
            try:
                return api(path)
            except UnexpectedStatus as error:
                if error.status not in (500, 502, 503, 504):
                    raise
                return None
        return eventually(probe, lambda result: result is not None)

    def health():
        # GET autenticado verifica el núcleo completo durante la caída AMQP.
        api(shipments)

    def mgmt(path, method='GET', body=None):
        return request(management, '/api/' + path, method, body, {'Authorization': basic})[0]

    def queue(name):
        return mgmt('queues/logitrack-s04/' + quote(name, safe=''))

    def create():
        address = {'calle': 'Ensayo MQ', 'numero': '80', 'ciudad': 'Jujuy', 'codigoPostal': '4600'}
        return api(shipments, 'POST', {'cliente': {'nombre': 'Ensayo MQ', 'email': values['BOOTSTRAP_ADMIN_EMAIL']},
            'origen': address, 'destino': address, 'pesoKg': 1, 'anchoCm': 10, 'altoCm': 10, 'largoCm': 10}, 201)[0]['id']

    def accept(envio, rider, key=None):
        result, headers = api(shipments + '/' + envio + '/solicitudes-asignacion', 'POST',
                              {'repartidorId': rider}, 202, key or str(uuid.uuid4()))
        check(headers['Location'] == '/applications/apishipment/api/v1/operaciones-asignacion/' + result['operationId'], 'Location público de operación')
        return result

    def operation(op):
        return api('/applications/apishipment/api/v1/operaciones-asignacion/' + op['operationId'])[0]

    def settled(op, status='APPLIED', reason=None):
        result = eventually(lambda: operation(op), lambda r: r['status'] != 'PENDING')
        check(result['status'] == status and result['reason'] == reason, 'Estado terminal y motivo esperados')
        return result

    def rider_of(envio):
        return api(shipments + '/' + envio)[0]['repartidorId']

    def snapshot(service, operation_id):
        # Solo UUID generados/validados, sin parámetros aportados por terceros.
        uuid.UUID(operation_id)
        return sql(service, "SELECT payload FROM amqp_outbox WHERE operation_id=UNHEX(REPLACE('" + operation_id + "','-',''))")

    def publish(raw, direction):
        payload = json.loads(raw) if isinstance(raw, str) else raw
        wire = raw if isinstance(raw, str) else json.dumps(raw, separators=(',', ':'))
        name = 'lt.assignment.' + direction + '.v1'
        routed = mgmt('exchanges/logitrack-s04/' + name + '/publish', 'POST', {
            'properties': {'content_type': 'application/json', 'delivery_mode': 2,
                           'message_id': payload['messageId'], 'correlation_id': payload['operationId']},
            'routing_key': 'availability.request' if direction == 'commands' else 'availability.result',
            'payload': wire, 'payload_encoding': 'string'})
        check(routed['routed'], 'El mensaje se enruta a una cola real')

    def scenario(name):
        stats['scenarios'].append(name)
        print('PASS ' + name, flush=True)

    try:
        # Descubrimiento puede propagarse después de health=UP.
        deadline = time.monotonic() + 120
        while True:
            try:
                token = api(security + 'auth/login', 'POST', {'email': values['BOOTSTRAP_ADMIN_EMAIL'],
                            'password': values['BOOTSTRAP_ADMIN_PASSWORD']})[0]['accessToken']
                api(shipments); api(couriers)
                break
            except (AssertionError, OSError):
                if time.monotonic() > deadline:
                    raise RuntimeError('API no lista; no se iniciaron mutaciones.') from None
                time.sleep(4)
        rider = api(couriers, 'POST', {'nombre': 'Courier ficticio MQ80', 'tipoVehiculo': 'Moto'}, 201)[0]['id']
        envio = create(); key = str(uuid.uuid4()); op = accept(envio, rider, key)
        settled(op)
        check(rider_of(envio) == rider, 'Asignación aplicada')
        check(len(api(shipments + '/' + envio + '/historial')[0]) == 1, 'Sin evento de negocio por asignar')
        check(accept(envio, rider, key)['operationId'] == op['operationId'], 'Replay conserva operación')
        api(shipments + '/' + envio + '/solicitudes-asignacion', 'POST', {'repartidorId': str(uuid.uuid4())}, 409, key)
        scenario('202, pipeline real, idempotencia HTTP e historial')

        command = snapshot('shipment', op['operationId']); result = snapshot('courier', op['operationId'])
        for _ in range(2):
            publish(command, 'commands'); publish(result, 'results')
        time.sleep(3)
        for service in ['shipment', 'courier']:
            check(sql(service, "SELECT COUNT(*) FROM assignment_inbox WHERE operation_id=UNHEX(REPLACE('" + op['operationId'] + "','-',''))") == '1', 'Un efecto inbox por operación')
        check(rider_of(envio) == rider, 'Duplicados conservan asignación')
        scenario('Duplicados de comando y resultado: efecto SQL único')

        for target, reason in [(str(uuid.uuid4()), 'NOT_FOUND'), (rider, 'INACTIVE')]:
            if reason == 'INACTIVE': api(couriers + '/' + rider + '/estado', 'PATCH', {'estado': 'INACTIVO'})
            negative_envio = create(); settled(accept(negative_envio, target), 'REJECTED', reason)
            check(rider_of(negative_envio) is None, 'Negativa sin asignación')
        api(couriers + '/' + rider + '/estado', 'PATCH', {'estado': 'ACTIVO'})
        scenario('Courier inexistente e inactivo')

        control('stop', 'rabbitmq')
        offline_envio = create(); offline = accept(offline_envio, rider)
        check(operation(offline)['status'] == 'PENDING', '202 independiente del broker')
        check(sql('shipment', "SELECT COUNT(*) FROM amqp_outbox WHERE published_at IS NULL") != '0', 'Outbox retenido')
        health(); control('start', 'rabbitmq'); settled(offline)
        check(rider_of(offline_envio) == rider, 'Recuperación automática sin reiniciar apps')
        scenario('Broker caído: núcleo disponible, outbox y recuperación')

        control('stop', 'courier-service')
        queued_envio = create(); queued = accept(queued_envio, rider)
        eventually(lambda: queue('lt.courier.assignment.v1')['messages_ready'], lambda n: n > 0)
        check(operation(queued)['status'] == 'PENDING', 'Courier detenido deja pendiente')
        control('start', 'courier-service'); settled(queued)
        scenario('Courier caído: comando durable y recuperación')

        control('stop', 'courier-service')
        cancelled_envio = create(); cancelled = accept(cancelled_envio, rider)
        api(shipments + '/' + cancelled_envio + '/cancelacion', 'PATCH')
        replaced_envio = create(); older = accept(replaced_envio, rider); newer = accept(replaced_envio, rider)
        control('start', 'courier-service')
        settled(cancelled, 'SUPERSEDED', 'STATE_CHANGED'); settled(older, 'SUPERSEDED', 'REPLACED'); settled(newer)
        eventually(lambda: sql('shipment', "SELECT COUNT(*) FROM assignment_inbox WHERE operation_id=UNHEX(REPLACE('" + cancelled['operationId'] + "','-',''))"), lambda n: n == '1')
        check(rider_of(cancelled_envio) is None, 'Resultado tardío no asigna cancelado')
        check(rider_of(replaced_envio) == rider, 'Generación vigente aplicada')
        scenario('Resultados tardíos tras cancelación y nueva generación')

        dead_before = queue('lt.courier.assignment.dead.v1')['messages']
        invalid = dict(json.loads(command), messageId=str(uuid.uuid4()), operationId=str(uuid.uuid4()), schemaVersion=99)
        publish(invalid, 'commands')
        eventually(lambda: queue('lt.courier.assignment.dead.v1')['messages'], lambda n: n > dead_before)
        scenario('Mensaje inválido aislado en DLQ')

        # Error transitorio real de SQL: la copia retry se confirma antes de ACK del original.
        sql('courier', "CREATE TRIGGER mq80_fail BEFORE INSERT ON assignment_inbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='mq80 controlled transient failure'", admin=True)
        failed = accept(create(), rider)
        eventually(lambda: queue('lt.courier.assignment.dead.v1')['messages'], lambda n: n > dead_before + 1, timeout=90)
        check(operation(failed)['status'] == 'PENDING', 'Agotar transporte no inventa resultado de negocio')
        sql('courier', 'DROP TRIGGER mq80_fail', admin=True)
        publish(snapshot('shipment', failed['operationId']), 'commands'); settled(failed)
        scenario('Retry acotado, DLQ y recuperación tras reparar SQL')

        # Regresión síncrona tras todas las fallas y operación completamente ACKeada.
        # El consumidor AMQP puede recuperar antes que el registro/caché de Feign.
        # Ventana acotada de estabilización; no se reintenta un PUT automáticamente.
        control('up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'courier-service')
        time.sleep(75)
        api(couriers + '/' + rider + '/disponibilidad')
        synchronous = create()
        api(shipments + '/' + synchronous + '/repartidor', 'PUT', {'repartidorId': rider})
        check(rider_of(synchronous) == rider, 'Feign/PUT conservados')
        for name in ['lt.courier.assignment.v1', 'lt.shipment.assignment.v1']:
            eventually(lambda: queue(name), lambda q: q['messages_ready'] == 0 and q['messages_unacknowledged'] == 0)
        before = sql('shipment', "SELECT COUNT(*), SUM(status='APPLIED') FROM assignment_operations")
        gateway = gateway_instance()
        control('restart', 'courier-service', 'shipment-service')
        control('up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'courier-service', 'shipment-service')
        courier_after = gateway_read_after_restart(couriers + '/' + rider)
        shipment_after = gateway_read_after_restart(shipments + '/' + synchronous)
        check(courier_after['id'] == rider, 'Gateway recupera la lectura de Courier')
        check(shipment_after['repartidorId'] == rider, 'Gateway recupera Shipment y conserva la asignación')
        check(gateway_instance() == gateway, 'Gateway se mantuvo en la misma instancia')
        check(sql('shipment', "SELECT COUNT(*), SUM(status='APPLIED') FROM assignment_operations") == before, 'Reinicio posterior al ACK no crea ni reaplica operaciones')
        check(sql('shipment', "SELECT COUNT(*) FROM assignment_inbox WHERE operation_id=UNHEX(REPLACE('" + op['operationId'] + "','-',''))") == '1', 'Reinicio posterior al ACK conserva efecto único')
        scenario('Feign/PUT, reinicio posterior al ACK y recuperación por Gateway')
        print(json.dumps({'ok': True, **stats, 'limits': ['Sin UI asíncrona frontend#10',
            'Corte TCP anterior al ACK cubierto por AssignmentDeliveryIT de Courier#21; aquí se prueba reinicio posterior al ACK',
            'At-least-once; sin reserva exclusiva ni alta disponibilidad']}, indent=2))
    finally:
        try: sql('courier', 'DROP TRIGGER IF EXISTS mq80_fail', admin=True)
        finally: control('start', 'rabbitmq', 'courier-service', 'shipment-service')


if __name__ == '__main__':
    main()
