"""Genera usuarios propios y topología del broker en un directorio privado externo.

Uso: python3 scripts/mq/prepare_broker.py /ruta/privada/nueva
No muestra contraseñas ni hashes y no reemplaza archivos existentes.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets

ROOT = Path(__file__).resolve().parents[2]


def password_hash(password):
    """Codifica el hash salado RabbitMQ SHA-256; el valor también debe ser privado."""
    salt = secrets.token_bytes(4)
    return base64.b64encode(salt + hashlib.sha256(salt + password.encode()).digest()).decode()


def definitions(passwords):
    """Deriva declaraciones y permisos desde la topología compartida de #76."""
    topology = json.loads((ROOT / 'docs/sprint04/asignacion-asincrona/topology.json').read_text())
    vhost = topology['vhost']
    exchanges = topology['exchanges']
    result = {
        'vhosts': [{'name': vhost}],
        'users': [{'name': user, 'password_hash': password_hash(password),
                   'hashing_algorithm': 'rabbit_password_hashing_sha256',
                   'tags': ['administrator'] if user == 'lt_operator' else []}
                  for user, password in passwords.items()],
        'permissions': [
            {'user': 'lt_operator', 'vhost': vhost, 'configure': '.*', 'write': '.*', 'read': '.*'},
            {'user': 'lt_shipment', 'vhost': vhost, 'configure': '^$',
             'write': r'^lt\.assignment\.(commands|retry)\.v1$', 'read': r'^lt\.shipment\.assignment\.v1$'},
            {'user': 'lt_courier', 'vhost': vhost, 'configure': '^$',
             'write': r'^lt\.assignment\.(results|retry)\.v1$', 'read': r'^lt\.courier\.assignment\.v1$'}],
        'exchanges': [{'name': name, 'vhost': vhost, 'type': 'direct', 'durable': True,
                       'auto_delete': False, 'internal': False, 'arguments': {}}
                      for name in exchanges.values()],
        'queues': [], 'bindings': [], 'policies': []}
    for direction, flow in topology['flows'].items():
        for key, exchange, routing in [('queue', exchanges[direction], flow['routing_key']),
                                       ('retry_queue', exchanges['retry'], flow['retry_routing_key']),
                                       ('dead_queue', exchanges['dead'], flow['dead_routing_key'])]:
            result['queues'].append({'name': flow[key], 'vhost': vhost, 'durable': True,
                                     'auto_delete': False, 'arguments': {'x-queue-type': 'quorum'}})
            result['bindings'].append({'source': exchange, 'vhost': vhost, 'destination': flow[key],
                                       'destination_type': 'queue', 'routing_key': routing, 'arguments': {}})
        for key, target, routing in [('queue', exchanges['dead'], flow['dead_routing_key']),
                                      ('retry_queue', exchanges[direction], flow['routing_key'])]:
            policy = {'dead-letter-exchange': target, 'dead-letter-routing-key': routing,
                      'dead-letter-strategy': 'at-least-once', 'overflow': 'reject-publish',
                      'delivery-limit': 10}
            if key == 'retry_queue':
                policy['message-ttl'] = topology['retry_delay_ms']
            result['policies'].append({'name': direction + '-' + key, 'vhost': vhost,
                                       'pattern': '^' + flow[key].replace('.', r'\.') + '$',
                                       'apply-to': 'quorum_queues', 'priority': 10, 'definition': policy})
    return result


def main():
    """Crea archivos exclusivos, con contraseñas aleatorias y permisos privados."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    directory = args.directory.resolve()
    if directory.is_relative_to(ROOT):
        parser.error('Elegir un directorio privado fuera del repositorio.')
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if any(directory.iterdir()):
        parser.error('El directorio debe estar vacío; no se reemplazan credenciales existentes.')
    directory.chmod(0o700)
    passwords = {user: secrets.token_urlsafe(32) for user in ('lt_operator', 'lt_shipment', 'lt_courier')}
    files = {'definitions.json': json.dumps(definitions(passwords), indent=2) + '\n',
             'credentials.json': json.dumps(passwords, indent=2) + '\n',
             'application.env': 'ASSIGNMENT_ENABLED=false\nASSIGNMENT_PUBLISHER_ENABLED=true\nASSIGNMENT_CONSUMER_ENABLED=true\n'
                                + 'MQ_SHIPMENT_PASSWORD=' + passwords['lt_shipment'] + '\nMQ_COURIER_PASSWORD=' + passwords['lt_courier'] + '\n',
             'broker.env': 'COMPOSE_PROJECT_NAME=logitrack-sprint04\nRABBITMQ_DEFINITIONS_FILE=' + str(directory / 'definitions.json')
                           + '\nRABBITMQ_SECRETS_GID=' + str(os.getgid()) + '\nRABBITMQ_MANAGEMENT_PORT=15672\n'}
    for name, content in files.items():
        with open(directory / name, 'x', opener=lambda path, flags: os.open(path, flags, 0o600)) as stream:
            stream.write(content)
    (directory / 'definitions.json').chmod(0o640)
    print('Broker preparado; archivos privados externos creados, sin mostrar credenciales.')


if __name__ == '__main__':
    main()
