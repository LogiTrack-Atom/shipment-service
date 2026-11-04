"""Administra una demo Sprint03 aislada, con negocio exclusivamente por Gateway."""
from __future__ import annotations

import io
import json
import re
import socket
import subprocess
import tarfile
import time
import urllib.request
from pathlib import Path

from runtime import DemoError, ROOT, Runtime, SERVICES

APPLICATIONS = {'configserver', 'eureka-server', 'api-gateway', 'shipment-service',
                'courier-service', 'security-service'}
CLIENTS = {'SHIPMENT-SERVICE', 'COURIER-SERVICE', 'SECURITY-SERVICE', 'API-GATEWAY'}


def validate_release(release: dict) -> None:
    """Rechaza referencias incompletas antes de crear recursos de Docker."""
    if (not isinstance(release, dict) or set(release.get('images', {})) != APPLICATIONS
            or any(not re.fullmatch('[0-9a-f]{40}', release.get(key, ''))
                   for key in ['source_commit', 'config_label'])
            or release['config_label'] == 'b40bd4d0eecd5eff6e5ef0a7b23d0a106a39ba13'):
        raise DemoError('El manifiesto necesita seis imágenes y revisiones Git completas de Sprint03.')
    for name, reference in release['images'].items():
        if (not isinstance(reference, str) or not re.fullmatch(r'[a-z0-9./:_@-]+', reference)
                or not (re.search(r':[A-Za-z0-9_.-]+(?:@sha256:[0-9a-f]{64})?$', reference)
                        or re.search(r'@sha256:[0-9a-f]{64}$', reference))
                or reference.endswith(':latest')
                or name != 'configserver' and 'sprint02' in reference):
            raise DemoError('Las imágenes requieren referencias explícitas de Sprint03, sin latest.')


def allocate_ports() -> dict:
    """Elige seis puertos diferentes de loopback para negocio y diagnóstico."""
    sockets = []
    try:
        for _ in range(6):
            connection = socket.socket()
            connection.bind(('127.0.0.1', 0))
            sockets.append(connection)
        return dict(zip(['shipment', 'courier', 'security', 'config', 'eureka', 'gateway'],
                        [s.getsockname()[1] for s in sockets]))
    finally:
        for connection in sockets:
            connection.close()


class Sprint03Runtime(Runtime):
    """Reutiliza claves, bloqueo y guardas de propiedad de la demo histórica."""
    services = SERVICES | {'eureka-server', 'api-gateway'}

    def __init__(self, directory: Path, release: dict | None = None):
        self.release = release
        if release is not None:
            validate_release(release)
        super().__init__(directory)

    @property
    def project(self) -> str:
        if not self.state:
            raise DemoError('Primero ejecutar prepare con el manifiesto de Sprint03.')
        return 'lt-demo-s03-' + self.state['demo_id']

    def load(self) -> None:
        """No acepta el estado histórico ni URLs o identidades ajenas."""
        if self.state_path.is_symlink():
            raise DemoError('El estado no puede ser un enlace simbólico.')
        try:
            data = json.loads(self.state_path.read_text())
            release = data['release']
            validate_release(release)
            if (data['format'] != 3 or data['directory'] != str(self.directory)
                    or not re.fullmatch('[a-f0-9]{32}', data['demo_id'])
                    or data['source_commit'] != release['source_commit']
                    or self.release is not None and self.release != release
                    or data['phase'] not in {'initializing', 'seeding', 'finalizing', 'ready', 'resetting'}
                    or data['profile'] not in {'dev', 'prod'}
                    or set(data['ports']) != {'shipment', 'courier', 'security', 'config', 'eureka', 'gateway'}
                    or any(type(p) is not int or not 1024 <= p <= 65535 for p in data['ports'].values())
                    or len(set(data['ports'].values())) != 6
                    or set(data['credentials']) != {'admin_password', 'test_password'}
                    or any(not re.fullmatch('[A-Za-z0-9_-]{24,72}', p) for p in data['credentials'].values())):
                raise ValueError()
        except (OSError, ValueError, KeyError, TypeError):
            raise DemoError('El estado no corresponde a una demo Sprint03 válida.') from None
        self.state, self.release = data, release

    def new_state(self) -> dict:
        if self.release is None:
            raise DemoError('Una demo nueva requiere --release-manifest.')
        state = super().new_state()
        return {**state, 'format': 3, 'source_commit': self.release['source_commit'],
                'release': self.release, 'ports': allocate_ports(), 'profile': 'dev'}

    def snapshot(self) -> dict[str, bytes]:
        """Lee únicamente topología y SQL del commit declarado en el manifiesto."""
        if self.release is None:
            raise DemoError('Falta el manifiesto de Sprint03.')
        result = subprocess.run(['git', 'archive', self.release['source_commit'], 'compose.sprint03.yaml',
                                 'compose.sprint03.diagnostics.yaml', 'docker/mysql'], cwd=ROOT, capture_output=True)
        if result.returncode:
            raise DemoError('Falta el commit de la topología Sprint03; recuperar su objeto Git.')
        files = {}
        with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
            for member in archive.getmembers():
                name = Path(member.name)
                if name.is_absolute() or '..' in name.parts:
                    raise DemoError('Ruta no permitida en el snapshot de Sprint03.')
                if member.isfile():
                    with archive.extractfile(member) as source:
                        files[member.name] = source.read()
                elif not member.isdir():
                    raise DemoError('Tipo de archivo no permitido en el snapshot.')
        return files

    def compose_files(self) -> list[Path]:
        return [self.source / 'compose.sprint03.yaml', self.source / 'compose.sprint03.diagnostics.yaml']

    def api_url(self, service: str) -> str:
        if service not in {'shipment', 'courier', 'security'}:
            raise DemoError('API no declarada en la demo.')
        suffix = {'shipment': 'apishipment', 'courier': 'apicourier', 'security': 'apisecurity'}[service]
        return f'http://127.0.0.1:{self.state["ports"]["gateway"]}/applications/{suffix}'

    def values(self) -> dict[str, str]:
        """Externaliza credenciales exclusivas y fija las seis imágenes del manifiesto."""
        import hashlib
        import hmac
        import os
        values = {'COMPOSE_PROJECT_NAME': self.project, 'SPRING_PROFILES_ACTIVE': self.state['profile'],
                  'CONFIG_LABEL': self.release['config_label'], 'KEYS_GID': str(os.getgid()),
                  'JWT_PRIVATE_KEY_FILE': str(self.directory / 'private.pem'),
                  'JWT_PUBLIC_KEY_FILE': str(self.directory / 'public.pem'),
                  'BOOTSTRAP_ADMIN_ENABLED': 'true' if self.state['phase'] in {'initializing', 'seeding'} else 'false',
                  'BOOTSTRAP_ADMIN_EMAIL': self.email('admin'),
                  'BOOTSTRAP_ADMIN_PASSWORD': self.state['credentials']['admin_password']}
        images = {'configserver': 'CONFIGSERVER', 'eureka-server': 'EUREKA', 'api-gateway': 'GATEWAY',
                  'shipment-service': 'SHIPMENT', 'courier-service': 'COURIER', 'security-service': 'SECURITY'}
        for name, prefix in images.items():
            values[prefix + '_IMAGE'] = self.release['images'][name]
        for service in ['shipment', 'courier', 'security']:
            for suffix in ['DB_PASSWORD', 'DB_ROOT_PASSWORD']:
                values[service.upper() + '_' + suffix] = hmac.new(
                    self.state['credentials']['admin_password'].encode(), (service + '_' + suffix.lower()).encode(),
                    hashlib.sha256).hexdigest()
        for name, prefix in [('shipment', 'SHIPMENT'), ('courier', 'COURIER'), ('security', 'SECURITY'),
                             ('config', 'CONFIG_SERVER'), ('eureka', 'EUREKA'), ('gateway', 'GATEWAY')]:
            values[prefix + '_HOST_PORT'] = str(self.state['ports'][name])
        return values

    def registry(self) -> dict:
        """Consulta el registro local de la demo sin publicar cabeceras ni secretos."""
        request = urllib.request.Request(f'http://127.0.0.1:{self.state["ports"]["eureka"]}/eureka/apps',
                                         headers={'Accept': 'application/json'})
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=5) as response:
            return json.load(response)['applications']

    def up(self) -> None:
        self.guard_resources()
        self.compose('config', '--quiet')
        self.compose('up', '-d', '--wait', '--wait-timeout', '600', capture=False)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                applications = self.registry().get('application', [])
                healthy = {app['name'] for app in applications
                           if any(i.get('status') == 'UP' for i in app.get('instance', []))}
                if CLIENTS <= healthy:
                    self.wait_for_routes()
                    return
            except (OSError, ValueError, KeyError, TypeError):
                pass
            time.sleep(2)
        raise DemoError('No aparecen los cuatro clientes saludables en Eureka dentro del tiempo previsto.')

    def wait_for_routes(self) -> None:
        """Espera propagación al Gateway; el registro no implica una caché ya actualizada."""
        from seed import Api
        api = Api(self)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                status, login = api.request('security', 'POST', '/api/v1/auth/login',
                                            body={'email': self.email('admin'),
                                                  'password': self.state['credentials']['admin_password']},
                                            expected=(200, 401, 503))
                if status == 200:
                    token = login['accessToken']
                    shipment, _ = api.request('shipment', 'GET', '/api/v1/envios', token=token, expected=(200, 503))
                    courier, _ = api.request('courier', 'GET', '/api/v1/admin/configuracion', token=token, expected=(200, 503))
                    if shipment == courier == 200:
                        return
            except (DemoError, OSError, ValueError, KeyError, TypeError):
                pass
            time.sleep(2)
        raise DemoError('Gateway no alcanza las tres APIs después de propagar el registro de Eureka.')
