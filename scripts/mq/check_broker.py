"""Ensayo reproducible de broker real, con proyecto y credenciales descartables.

Requiere Docker Compose y scripts/mq/requirements.txt. Crea solo su proyecto único,
comprueba transporte y permisos y elimina ese proyecto/volúmenes en finally.
No prueba todavía el consumidor Java ni operaciones de negocio de #78/#79.
"""
import argparse
import base64
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import uuid
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]


def available_port():
    """Selecciona un puerto local efímero para el ensayo, sin publicar en otras interfaces."""
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


def main():
    """Comprueba conservación tras reinicio, retry/DLQ y mínimo acceso de los usuarios."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--private-root', type=Path, help='Directorio externo compartido con Docker Desktop; en CI usa el temporal del sistema.')
    args = parser.parse_args()
    if args.private_root:
        if args.private_root.resolve().is_relative_to(ROOT):
            parser.error('El directorio privado debe estar fuera del repositorio.')
        args.private_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    project = 'lt-mq-check-' + uuid.uuid4().hex[:12]
    with tempfile.TemporaryDirectory(prefix=project + '-', dir=args.private_root) as directory:
        private = Path(directory)
        subprocess.run([sys.executable, 'scripts/mq/prepare_broker.py', directory], cwd=ROOT,
                       check=True, capture_output=True)
        management_port, amqp_port = available_port(), available_port()
        while amqp_port == management_port:
            amqp_port = available_port()
        envfile = private / 'broker.env'
        envfile.write_text(envfile.read_text().replace('RABBITMQ_MANAGEMENT_PORT=15672',
                                                       'RABBITMQ_MANAGEMENT_PORT=' + str(management_port)))
        override = private / 'test-port.yaml'
        override.write_text('services:\n  rabbitmq:\n    ports:\n      - 127.0.0.1:' + str(amqp_port) + ':5672\n')
        command = ['docker', 'compose', '-p', project, '--env-file', str(envfile),
                   '-f', 'compose.sprint04-mq.yaml', '-f', str(override), '--profile', 'mq']

        def docker(*args):
            return subprocess.run(command + list(args), cwd=ROOT, capture_output=True,
                                  text=True, check=True).stdout

        def phase(name):
            result = subprocess.run([sys.executable, 'scripts/mq/verify_broker.py', name,
                                     '--credentials', str(private / 'credentials.json'),
                                     '--state', str(private / 'state.json'), '--port', str(amqp_port)],
                                    cwd=ROOT, capture_output=True, text=True, check=True)
            return json.loads(result.stdout)

        try:
            docker('up', '-d', '--wait', '--wait-timeout', '180')
            passwords = json.loads((private / 'credentials.json').read_text())

            def api(path):
                auth = base64.b64encode(('lt_operator:' + passwords['lt_operator']).encode()).decode()
                request = Request('http://127.0.0.1:' + str(management_port) + '/api/' + path,
                                  headers={'Authorization': 'Basic ' + auth})
                with urlopen(request, timeout=10) as response:
                    return json.load(response)

            users = api('users')
            assert {row['name'] for row in users} == {'lt_operator', 'lt_shipment', 'lt_courier'}
            queues = api('queues/logitrack-s04')
            assert len(queues) == 6 and all(q['durable'] and q['arguments']['x-queue-type'] == 'quorum' for q in queues)
            policies = api('policies/logitrack-s04')
            assert len(policies) == 4 and all(p['definition']['dead-letter-strategy'] == 'at-least-once' for p in policies)
            standalone = json.loads(subprocess.run(
                ['docker', 'compose', '--env-file', str(envfile), '-f', 'compose.sprint04-mq.yaml',
                 '--profile', 'mq', 'config', '--format', 'json'], cwd=ROOT,
                capture_output=True, text=True, check=True).stdout)
            ports = standalone['services']['rabbitmq']['ports']
            assert len(ports) == 1 and ports[0]['host_ip'] == '127.0.0.1' and ports[0]['target'] == 15672
            prepare = phase('prepare')
            docker('restart', 'rabbitmq')
            docker('up', '-d', '--wait', '--wait-timeout', '180')
            verify = phase('verify')
            print(json.dumps({'ok': True, 'prepare': prepare, 'verify': verify,
                              'topology_checks': 4, 'guest_absent': True,
                              'production_amqp_port_unpublished': True,
                              'business_consumers_tested': False}, indent=2))
        finally:
            # Solo el proyecto recién creado por esta ejecución y sus datos ficticios.
            docker('down', '--volumes')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        if isinstance(error, subprocess.CalledProcessError):
            print('Etapa fallida: ' + ' '.join(str(x) for x in error.cmd[:4]), file=sys.stderr)
            # Solo stderr de comandos propios: nunca se imprimen stdout/JSON privados.
            print((error.stderr or '')[-3000:], file=sys.stderr)
        print('Verificación real de broker incompleta: ' + type(error).__name__
              + '. No se muestran archivos privados ni salidas del daemon.', file=sys.stderr)
        sys.exit(1)
