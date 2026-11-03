"""Aceptación real de la demo con Docker, persistencia y recursos de control ajenos."""
from __future__ import annotations

import json
import tempfile
import uuid
from pathlib import Path

from manage import execute
from runtime import DemoError, ROOT, Runtime, run, write_json
from seed import Seed


def require(condition: bool, message: str) -> None:
    """Falla sin incluir datos privados de las respuestas."""
    if not condition:
        raise DemoError(message)


def marker(volume: str, *, create=False) -> str:
    """Lee un marcador con la imagen ya usada, sin iniciar otra base de datos."""
    command = 'printf control-demo > /control/marker' if create else 'cat /control/marker'
    return run(['docker', 'run', '--rm', '--network', 'none', '--volume', volume + ':/control',
                '--entrypoint', 'sh', 'mysql:8.4.11', '-c', command]).strip()


def verify() -> dict:
    """Prueba preparación repetible, recuperación, reset explícito y aislamiento."""
    control = 'lt-demo-control-' + uuid.uuid4().hex
    foreign = None
    with tempfile.TemporaryDirectory(prefix='logitrack-demo-acceptance-') as temporary:
        directory = Path(temporary) / 'demo'
        try:
            result = execute('prepare', directory)
            initial = Runtime(directory).state
            execute('prepare', directory)
            execute('seed', directory)
            require(Runtime(directory).state['resources'] == initial['resources'], 'La carga duplicó identidades.')

            # Simular pérdida del manifiesto durante una carga: recuperar mediante las APIs.
            interrupted = Runtime(directory)
            interrupted.state['phase'] = 'seeding'
            interrupted.state['resources'] = {}
            interrupted.save()
            execute('seed', directory)
            require(Runtime(directory).state['resources'] == initial['resources'], 'La recuperación duplicó datos.')

            execute('down', directory)
            execute('prepare', directory)
            require(Runtime(directory).state['resources'] == initial['resources'], 'No persistieron las identidades.')

            run(['docker', 'volume', 'create', '--label', 'com.docker.compose.project=control-ajeno', control])
            marker(control, create=True)
            changed = Runtime(directory)
            loader = Seed(changed)
            loader.load_accounts()
            shipment_id = changed.state['resources']['shipments']['asignado']['id']
            for state in ['EN_RUTA', 'ENTREGADO']:
                loader.api.request('shipment', 'PATCH', '/api/v1/envios/' + shipment_id + '/estado',
                                   token=loader.tokens['repartidor_activo'], body={'estado': state})
            try:
                execute('seed', directory)
            except DemoError:
                pass
            else:
                raise DemoError('seed reparó datos de negocio sin un reset explícito.')
            _, persisted = loader.api.request('shipment', 'GET', '/api/v1/envios/' + shipment_id,
                                              token=loader.tokens['admin'])
            require(persisted['estado'] == 'ENTREGADO', 'seed modificó el estado de negocio.')

            execute('reset', directory)
            reset_state = Runtime(directory).state
            require(reset_state['credentials'] != initial['credentials'], 'reset no rotó contraseñas.')
            require(reset_state['key_fingerprint'] != initial['key_fingerprint'], 'reset no rotó claves.')
            require(reset_state['resources']['shipments'] != initial['resources']['shipments'],
                    'reset no recreó los envíos.')
            execute('verify', directory)
            require(marker(control) == 'control-demo', 'reset afectó datos del volumen de control.')

            # Un nombre coincidente no demuestra propiedad: las etiquetas deben concordar.
            guarded = Runtime(Path(temporary) / 'guarded')
            guarded.initialize()
            foreign = guarded.project + '_shipment_mysql_data'
            run(['docker', 'volume', 'create', '--label', 'com.docker.compose.project=control-ajeno', foreign])
            try:
                execute('reset', guarded.directory)
            except DemoError:
                pass
            else:
                raise DemoError('Se aceptó un volumen de otro proyecto.')
            require(Runtime(guarded.directory).state['phase'] == 'initializing',
                    'El rechazo de propiedad modificó el estado privado.')
            run(['docker', 'volume', 'inspect', foreign])
            return {**result, 'idempotencia': True, 'recuperacion_carga': True, 'persistencia': True,
                    'rechazo_reparacion_implicita': True, 'reset': True, 'rotacion_claves_credenciales': True,
                    'volumen_control_conservado': True, 'rechazo_volumen_ajeno': True}
        finally:
            if (directory / 'runtime.json').exists():
                execute('destroy', directory)
            # Estos dos volúmenes se crearon explícitamente en esta prueba.
            for volume in [foreign, control]:
                if volume and run(['docker', 'volume', 'ls', '--filter', 'name=^' + volume + '$',
                                   '--format', '{{.Name}}']).strip():
                    run(['docker', 'volume', 'rm', volume])


if __name__ == '__main__':
    evidence = verify()
    output = ROOT / 'target' / 'demo-verificacion.json'
    output.parent.mkdir(exist_ok=True)
    write_json(output, evidence)
    print(json.dumps(evidence, ensure_ascii=False))
