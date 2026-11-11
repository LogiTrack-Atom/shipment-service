"""Verifica Eureka, Feign, Gateway, perfiles y persistencia en una demo Sprint03 propia."""
from __future__ import annotations

import argparse
import hashlib
from datetime import datetime, timezone
import json
import os
import sys
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'demo'))
from manage_sprint03 import execute
from runtime import DemoError, ROOT, run
from runtime_sprint03 import CLIENTS, Sprint03Runtime
from seed import Seed


def require(condition: bool, message: str) -> None:
    """Falla con una descripción sin cuerpos HTTP ni credenciales."""
    if not condition:
        raise DemoError(message)


def verify(directory: Path, *, reset_after=False) -> dict:
    """Opera solo sobre la demo administrada y limpia su proveedor temporal."""
    runtime = Sprint03Runtime(directory)
    runtime.guard_resources()
    require(runtime.state['phase'] == 'ready', 'La demo debe estar preparada antes de aceptar.')
    checks = []

    def check(name, condition=True):
        require(condition, name)
        checks.append({'escenario': name, 'aprobado': True})
        print('OK: ' + name, flush=True)

    loader = Seed(runtime)
    loader.verify()
    initial = json.loads(json.dumps(runtime.state['resources']))
    execute('prepare', directory)
    check('prepare repetido conserva las identidades', Sprint03Runtime(directory).state['resources'] == initial)
    for profile in ['prod', 'dev']:
        execute('prepare', directory, profile=profile)
        runtime = Sprint03Runtime(directory)
        loader = Seed(runtime)
        loader.load_accounts()
        _, diagnostic = loader.api.request('courier', 'GET', '/api/v1/admin/configuracion', token=loader.tokens['admin'])
        check('diagnóstico consumido ' + profile,
              set(diagnostic) == {'aplicacion', 'perfiles', 'marcador'}
              and diagnostic['aplicacion'] == 'courier-service'
              and diagnostic['perfiles'] == [profile] and diagnostic['marcador'] == 'courier-service-' + profile)
        check('identidades persistidas en ' + profile, runtime.state['resources'] == initial)
    registry = runtime.registry().get('application', [])
    check('cuatro clientes registrados en Eureka', {a['name'] for a in registry} == CLIENTS)
    # Compose emite objetos JSON separados por línea en algunas versiones.
    entries = runtime.compose('ps', '--format', 'json').strip()
    containers = json.loads(entries) if entries.startswith('[') else [json.loads(line) for line in entries.splitlines()]
    check('nueve contenedores saludables', len(containers) == 9
          and all(c.get('Health') == 'healthy' for c in containers))
    admin = loader.tokens['admin']
    _, shipment = loader.api.request('shipment', 'POST', '/api/v1/envios', token=admin,
                                      body=loader.shipment_body('aceptacion-' + uuid.uuid4().hex, 'cliente'), expected=(201,))
    identifier = shipment['id']
    path = '/api/v1/envios/' + identifier
    _, history = loader.api.request('shipment', 'GET', path + '/historial', token=admin)
    assigned_path = '/api/v1/envios/' + initial['shipments']['asignado']['id']
    _, assigned_before = loader.api.request('shipment', 'GET', assigned_path, token=admin)
    _, assigned_history = loader.api.request('shipment', 'GET', assigned_path + '/historial', token=admin)
    active = initial['couriers']['activo']['id']
    shipment_container = runtime.compose('ps', '-q', 'shipment-service').strip()
    before_process = run(['docker', 'inspect', '--format', '{{.Id}} {{.Image}} {{.State.StartedAt}}', shipment_container]).strip()
    replacement = runtime.project + '-courier-shift'
    replacement_created = False
    runtime.compose('stop', 'courier-service')
    try:
        _, error = loader.api.request('shipment', 'PUT', path + '/repartidor', token=admin,
                                      body={'repartidorId': active}, expected=(503,))
        check('courier detenido devuelve ApiError 503', error['status'] == 503
              and set(error) == {'timestamp', 'status', 'error', 'mensaje', 'detalles'})
        _, current = loader.api.request('shipment', 'GET', path, token=admin)
        _, current_history = loader.api.request('shipment', 'GET', path + '/historial', token=admin)
        check('caída no modifica asignación, fechas, estado ni historial', current == shipment and current_history == history)
        loader.api.request('shipment', 'PATCH', assigned_path + '/estado', token=admin,
                           body={'estado': 'EN_RUTA'}, expected=(503,))
        _, current = loader.api.request('shipment', 'GET', assigned_path, token=admin)
        _, current_history = loader.api.request('shipment', 'GET', assigned_path + '/historial', token=admin)
        check('caída también impide EN_RUTA sin mutación', current == assigned_before and current_history == assigned_history)

        model = json.loads(runtime.compose('config', '--format', 'json'))
        service = model['services']['courier-service']
        environment = {**service['environment'], 'SERVER_PORT': '8083', 'EUREKA_INSTANCE_HOSTNAME': replacement,
                       'EUREKA_INSTANCE_INSTANCE_ID': replacement + ':8083', 'EUREKA_INSTANCE_NON_SECURE_PORT': '8083'}
        env_file = runtime.directory / 'courier-shift.env'
        fd = os.open(env_file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, 'w') as output:
            output.write(''.join(k + '=' + str(v) + '\n' for k, v in environment.items()))
        command = ['docker', 'run', '-d', '--name', replacement, '--network', runtime.project + '_logitrack-net',
                   '--network-alias', replacement, '--env-file', str(env_file)]
        for group in service.get('group_add', []):
            command += ['--group-add', str(group)]
        for mount in service.get('volumes', []):
            command += ['--mount', 'type=bind,source=' + mount['source'] + ',target=' + mount['target'] + ',readonly']
        command += [service['image']]
        run(command)
        replacement_created = True
        deadline = time.monotonic() + 150
        recovered = False
        while time.monotonic() < deadline:
            registry = runtime.registry().get('application', [])
            instances = [i for app in registry if app['name'] == 'COURIER-SERVICE' for i in app.get('instance', [])]
            announced = any(i.get('hostName') == replacement and int(i['port']['$']) == 8083 and i['status'] == 'UP'
                            for i in instances)
            if announced:
                status, _ = loader.api.request('shipment', 'PUT', path + '/repartidor', token=admin,
                                               body={'repartidorId': active}, expected=(200, 503))
                if status == 200:
                    recovered = True
                    break
                _, unchanged = loader.api.request('shipment', 'GET', path, token=admin)
                require(unchanged == shipment, 'Un intento fallido de recuperación modificó el envío.')
            time.sleep(2)
        check('Feign descubre una instancia con otro DNS y puerto', recovered)
        after_process = run(['docker', 'inspect', '--format', '{{.Id}} {{.Image}} {{.State.StartedAt}}', shipment_container]).strip()
        check('Shipment conserva contenedor, imagen y proceso', before_process == after_process)
        _, persisted = loader.api.request('shipment', 'GET', path, token=admin)
        check('asignación persistida sin cambiar PENDIENTE', persisted['repartidorId'] == active and persisted['estado'] == 'PENDIENTE')
        loader.api.request('shipment', 'PATCH', path + '/estado', token=loader.tokens['repartidor_activo'], body={'estado': 'EN_RUTA'})
        check('EN_RUTA vuelve a consultar disponibilidad mediante Feign')
    finally:
        if replacement_created:
            run(['docker', 'rm', '-f', replacement])
        (runtime.directory / 'courier-shift.env').unlink(missing_ok=True)
        runtime.compose('up', '-d', '--wait', '--wait-timeout', '180', 'courier-service')

    loader.api.request('shipment', 'PATCH', path + '/estado', token=loader.tokens['repartidor_activo'], body={'estado': 'ENTREGADO'})
    _, delivered = loader.api.request('shipment', 'GET', path, token=admin)
    _, delivered_history = loader.api.request('shipment', 'GET', path + '/historial', token=admin)
    check('flujo completo e historial', delivered['estado'] == 'ENTREGADO'
          and [x['estadoNuevo'] for x in delivered_history] == ['PENDIENTE', 'EN_RUTA', 'ENTREGADO'])
    runtime.down()
    runtime.up()
    loader = Seed(runtime)
    loader.load_accounts()
    _, current = loader.api.request('shipment', 'GET', path, token=loader.tokens['admin'])
    _, current_history = loader.api.request('shipment', 'GET', path + '/historial', token=loader.tokens['admin'])
    check('recrear contenedores conserva envío e historial', current == delivered and current_history == delivered_history)
    check('tres bases conservan identidades', loader.accounts == initial['accounts'])
    try:
        execute('seed', directory)
    except DemoError:
        check('seed rechaza reparar datos modificados implícitamente')
    else:
        raise DemoError('seed debía requerir reset explícito después del flujo de negocio.')
    if reset_after:
        previous = json.loads(json.dumps(runtime.state))
        execute('reset', directory)
        restored = Sprint03Runtime(directory)
        check('reset explícito rota claves y credenciales', previous['credentials'] != restored.state['credentials']
              and previous['key_fingerprint'] != restored.state['key_fingerprint'])
        execute('verify', directory)
        check('reset repone catálogo inicial sin duplicados')
    return {'fecha_utc': datetime.now(timezone.utc).isoformat(),
            'verificador_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'alcance': 'demo Sprint03 propia; negocio por Gateway, Eureka y Feign reales',
            'config_label': runtime.release['config_label'], 'imagenes': runtime.release['images'],
            'aprobado': True, 'escenarios': checks,
            'limites': '500, timeout y contratos HTTP malformados se verifican en las suites Java de Feign con HTTP real y MySQL.'}


def main() -> int:
    """Registra evidencia sanitizada y propaga fallos sin exponer el estado privado."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state-dir', type=Path, default=ROOT / '.logitrack-demo/sprint03')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/sprint03-acceptance.json')
    parser.add_argument('--reset-after', action='store_true', help='Restablecer solo la demo propia al terminar.')
    args = parser.parse_args()
    try:
        report = verify(args.state_dir, reset_after=args.reset_after)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'aprobado': True, 'escenarios': len(report['escenarios'])}))
        return 0
    except (DemoError, OSError, ValueError, KeyError, TypeError) as error:
        print(str(error) if isinstance(error, DemoError) else 'No se pudo completar la aceptación Sprint03.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
