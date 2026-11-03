"""Administra únicamente los recursos Docker y archivos privados de una demo propia."""
from __future__ import annotations

import hashlib
import hmac
import io
import json
import os
import re
import secrets
import socket
import stat
import subprocess
import tarfile
import tempfile
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_COMMIT = '938496bae144d559187fa59368e155eea2525af9'
CONFIG_COMMIT = 'b40bd4d0eecd5eff6e5ef0a7b23d0a106a39ba13'
VERSION = 'sprint02-1.0.0'
SERVICES = {'shipment-mysql', 'courier-mysql', 'security-mysql', 'configserver',
            'shipment-service', 'courier-service', 'security-service'}
VOLUMES = {'shipment_mysql_data', 'courier_mysql_data', 'security_mysql_data'}


class DemoError(RuntimeError):
    """Indica una condición operativa sin incluir cuerpos HTTP ni credenciales."""


def run(args: list[str], *, capture: bool = True, env=None) -> str:
    """Ejecuta una herramienta sin imprimir argumentos o variables privadas."""
    result = subprocess.run(args, cwd=ROOT, env=env, text=True, capture_output=capture)
    if result.returncode:
        raise DemoError(f'{args[0]} terminó con código {result.returncode}.')
    return result.stdout or ''


def write_json(path: Path, data: dict) -> None:
    """Reemplaza un archivo propio atómicamente con permisos de propietario."""
    if path.is_symlink():
        raise DemoError('Un archivo de control de la demo es un enlace simbólico.')
    descriptor, temporary = tempfile.mkstemp(prefix='.demo-write-', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'w') as output:
            json.dump(data, output, ensure_ascii=False, indent=2)
            output.write('\n')
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def snapshot() -> dict[str, bytes]:
    """Lee Compose y SQL de un commit inmutable sin ejecutar contenido del archivo."""
    result = subprocess.run(['git', 'archive', SOURCE_COMMIT, 'docker-compose.yaml', 'docker/mysql',
                             'docs/sprint02/entrega/versiones.json'],
                            cwd=ROOT, capture_output=True)
    if result.returncode:
        raise DemoError('Falta el snapshot Git de Sprint02; consultar docs/sprint03/Demo.md.')
    files = {}
    with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
        for member in archive.getmembers():
            name = Path(member.name)
            if name.is_absolute() or '..' in name.parts:
                raise DemoError('El snapshot contiene una ruta no permitida.')
            if member.isfile():
                with archive.extractfile(member) as content:
                    files[member.name] = content.read()
            elif not member.isdir():
                raise DemoError('El snapshot contiene un tipo de archivo no permitido.')
    return files


def ports() -> dict[str, int]:
    """Reserva puertos distintos durante su elección sin usar puertos de otro entorno."""
    connections = []
    try:
        for _ in range(4):
            connection = socket.socket()
            connection.bind(('127.0.0.1', 0))
            connections.append(connection)
        return dict(zip(['shipment', 'courier', 'security', 'config'],
                        [connection.getsockname()[1] for connection in connections]))
    finally:
        for connection in connections:
            connection.close()


class Runtime:
    """Mantiene una identidad de demo y comprueba propiedad antes de operar Docker."""

    def __init__(self, directory: Path):
        if directory.is_symlink():
            raise DemoError('El directorio de la demo no puede ser un enlace simbólico.')
        self.directory = directory.resolve()
        self.state_path = self.directory / 'runtime.json'
        self.source = self.directory / 'source'
        self.env_file = self.directory / 'runtime.env'
        self.state = None
        if self.state_path.exists() or self.state_path.is_symlink():
            self.load()

    @property
    def project(self) -> str:
        """Deriva el nombre Docker de la identidad propia; no acepta nombres externos."""
        if not self.state:
            raise DemoError('Primero debe ejecutarse prepare.')
        return 'lt-demo-' + self.state['demo_id']

    def load(self) -> None:
        """Rechaza estado ajeno, alterado o trasladado antes de usar sus datos."""
        if self.state_path.is_symlink():
            raise DemoError('El archivo de control no puede ser un enlace simbólico.')
        try:
            data = json.loads(self.state_path.read_text())
            valid = (data['format'] == 1 and re.fullmatch(r'[a-f0-9]{32}', data['demo_id'])
                     and data['directory'] == str(self.directory)
                     and data['source_commit'] == SOURCE_COMMIT
                     and data['phase'] in {'initializing', 'seeding', 'ready', 'resetting'})
            valid = valid and set(data['ports']) == {'shipment', 'courier', 'security', 'config'}
            valid = valid and all(type(value) is int and 1024 <= value <= 65535 for value in data['ports'].values())
            valid = valid and len(set(data['ports'].values())) == 4
            valid = valid and set(data['credentials']) == {'admin_password', 'test_password'}
            valid = valid and all(isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9_-]{24,72}', value)
                                  for value in data['credentials'].values())
            if not valid:
                raise ValueError()
        except (ValueError, KeyError, TypeError, OSError):
            raise DemoError('El directorio no contiene un estado de demo válido.') from None
        self.state = data

    def save(self) -> None:
        """Persiste solamente el estado generado por este administrador."""
        write_json(self.state_path, self.state)

    def initialize(self) -> None:
        """Prepara archivos privados sin modificar un directorio ajeno no vacío."""
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        if self.state is None:
            remaining = [p for p in self.directory.iterdir() if p.name != '.lock']
            if remaining:
                raise DemoError('Se requiere un directorio vacío o una demo administrada existente.')
            self.state = {'format': 1, 'demo_id': uuid.uuid4().hex, 'directory': str(self.directory),
                          'source_commit': SOURCE_COMMIT, 'phase': 'initializing', 'ports': ports(),
                          'key_fingerprint': None,
                          'credentials': {'admin_password': secrets.token_urlsafe(24),
                                          'test_password': secrets.token_urlsafe(24)}, 'resources': {}}
            self.save()
        os.chmod(self.directory, 0o700)
        expected = snapshot()
        if self.source.is_symlink():
            raise DemoError('El snapshot no puede ser un enlace simbólico.')
        if not self.source.exists():
            self.source.mkdir()
            for name, content in expected.items():
                path = self.source / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
        self.verify_snapshot(expected)
        key_files = [self.directory / name for name in ['public.pem', 'private.pem']]
        keys_valid = all(p.is_file() and not p.is_symlink() for p in key_files)
        keys_valid = keys_valid and self.state.get('key_fingerprint') == hashlib.sha256(key_files[0].read_bytes()).hexdigest()
        if not keys_valid:
            if self.state['phase'] != 'initializing':
                raise DemoError('Faltan claves de la demo; ejecutar reset explícitamente.')
            self.generate_keys()
        self.write_env()

    def verify_snapshot(self, expected=None) -> None:
        """Comprueba nombres y contenido para impedir usar un Compose modificado."""
        if self.source.is_symlink() or not self.source.is_dir():
            raise DemoError('Falta un snapshot de demo válido.')
        expected = snapshot() if expected is None else expected
        actual = {}
        for path in self.source.rglob('*'):
            if path.is_symlink():
                raise DemoError('El snapshot contiene enlaces no permitidos.')
            if path.is_file():
                actual[str(path.relative_to(self.source))] = path.read_bytes()
        if actual != expected:
            raise DemoError('El Compose o los esquemas de la demo fueron modificados.')

    def generate_keys(self) -> None:
        """Genera un par efímero propio sin exponer el material privado."""
        with tempfile.TemporaryDirectory(prefix='.keys-', dir=self.directory) as temporary:
            private, public = Path(temporary) / 'private.pem', Path(temporary) / 'public.pem'
            run(['openssl', 'genpkey', '-quiet', '-algorithm', 'RSA', '-pkeyopt',
                 'rsa_keygen_bits:2048', '-out', str(private)])
            run(['openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public)])
            for path in [private, public]:
                target = self.directory / path.name
                if target.is_symlink():
                    raise DemoError('Una clave de la demo es un enlace simbólico.')
                os.chmod(path, 0o640)
                os.replace(path, target)
        self.state['key_fingerprint'] = hashlib.sha256((self.directory / 'public.pem').read_bytes()).hexdigest()
        self.save()

    def email(self, alias: str) -> str:
        """Identifica las cuentas sintéticas del entorno sin reutilizar correos del equipo."""
        return f'{alias}.{self.state["demo_id"]}@demo.logitrack.test'

    def values(self) -> dict[str, str]:
        """Construye variables controladas; no consume secretos ni nombres de otros stacks."""
        values = {'COMPOSE_PROJECT_NAME': self.project, 'SPRING_PROFILES_ACTIVE': 'dev',
                  'CONFIG_LABEL': CONFIG_COMMIT, 'KEYS_GID': str(os.getgid()),
                  'JWT_PRIVATE_KEY_FILE': str(self.directory / 'private.pem'),
                  'JWT_PUBLIC_KEY_FILE': str(self.directory / 'public.pem'),
                  'BOOTSTRAP_ADMIN_ENABLED': 'true' if self.state['phase'] in {'initializing', 'seeding'} else 'false',
                  'BOOTSTRAP_ADMIN_EMAIL': self.email('admin'),
                  'BOOTSTRAP_ADMIN_PASSWORD': self.state['credentials']['admin_password']}
        for service in ['shipment', 'courier', 'security']:
            values[service.upper() + '_IMAGE'] = f'maxdz10/{service}-service:{VERSION}'
            for suffix in ['DB_PASSWORD', 'DB_ROOT_PASSWORD']:
                label = service + '_' + suffix.lower()
                # Derivación estable exclusiva de estas bases descartables, sin contraseñas versionadas.
                values[service.upper() + '_' + suffix] = hmac.new(
                    self.state['credentials']['admin_password'].encode(), label.encode(), hashlib.sha256).hexdigest()
        values['CONFIGSERVER_IMAGE'] = f'maxdz10/configserver:{VERSION}'
        for service in ['shipment', 'courier', 'security']:
            values[service.upper() + '_HOST_PORT'] = str(self.state['ports'][service])
        values['CONFIG_SERVER_HOST_PORT'] = str(self.state['ports']['config'])
        manifest = json.loads((self.source / 'docs/sprint02/entrega/versiones.json').read_text())
        for service, variable in [('shipment-service', 'SHIPMENT_IMAGE'), ('courier-service', 'COURIER_IMAGE'),
                                  ('security-service', 'SECURITY_IMAGE'), ('configserver', 'CONFIGSERVER_IMAGE')]:
            image = manifest['imagenes'][service]
            values[variable] = image['referencia'] + '@' + image['digest']
        return values

    def write_env(self) -> None:
        """Mantiene un dotenv privado para inspección o reproducción local del entorno propio."""
        if self.env_file.is_symlink():
            raise DemoError('El archivo de entorno no puede ser un enlace simbólico.')
        descriptor = os.open(self.env_file, os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
        with os.fdopen(descriptor, 'w') as output:
            info = os.fstat(output.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_uid != os.getuid():
                raise DemoError('El archivo de entorno no es un archivo propio válido.')
            os.fchmod(output.fileno(), 0o600)
            os.ftruncate(output.fileno(), 0)
            output.write(''.join(f'{key}={value}\n' for key, value in self.values().items()))

    def compose(self, *args: str, capture=True) -> str:
        """Ejecuta Compose con nombre y archivos fijados, sin aceptar destinos alternativos."""
        self.verify_snapshot()
        self.write_env()
        environment = {**os.environ, **self.values()}
        return run(['docker', 'compose', '--project-name', self.project, '--env-file', str(self.env_file),
                    '--file', str(self.source / 'docker-compose.yaml'), *args], capture=capture, env=environment)

    def guard_resources(self) -> None:
        """Rechaza contenedores, redes o volúmenes que no pertenezcan al proyecto generado."""
        self.verify_snapshot()
        for volume in VOLUMES:
            name = self.project + '_' + volume
            found = run(['docker', 'volume', 'ls', '--filter', 'name=^' + name + '$', '--format', '{{.Name}}']).splitlines()
            if found:
                data = json.loads(run(['docker', 'volume', 'inspect', name]))[0]
                labels = data.get('Labels') or {}
                if labels.get('com.docker.compose.project') != self.project or labels.get('com.docker.compose.volume') != volume:
                    raise DemoError('Un volumen esperado pertenece a otro entorno; no se opera sobre él.')
        containers = run(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=' + self.project]).split()
        if containers:
            for container in json.loads(run(['docker', 'inspect', *containers])):
                labels = container['Config'].get('Labels') or {}
                expected_file = str(self.source / 'docker-compose.yaml')
                if (labels.get('com.docker.compose.project') != self.project
                        or labels.get('com.docker.compose.service') not in SERVICES
                        or labels.get('com.docker.compose.project.config_files') != expected_file):
                    raise DemoError('Un contenedor no coincide con la demo administrada.')
        network = self.project + '_logitrack-net'
        if run(['docker', 'network', 'ls', '--filter', 'name=^' + network + '$', '--format', '{{.Name}}']).strip():
            labels = json.loads(run(['docker', 'network', 'inspect', network]))[0].get('Labels') or {}
            if (labels.get('com.docker.compose.project') != self.project
                    or labels.get('com.docker.compose.network') != 'logitrack-net'):
                raise DemoError('La red esperada pertenece a otro entorno.')

    def up(self) -> None:
        """Arranca o recupera solo el stack administrado, conservando sus volúmenes."""
        self.guard_resources()
        self.compose('config', '--quiet')
        self.compose('pull', '--quiet', capture=False)
        self.compose('up', '-d', '--wait', '--wait-timeout', '240', capture=False)

    def down(self, *, remove_volumes=False) -> None:
        """Detiene la demo; solo reset/destroy solicitan borrar sus volúmenes comprobados."""
        self.guard_resources()
        args = ['down']
        if remove_volumes:
            args += ['--volumes']
        self.compose(*args, capture=False)

    def rotate(self) -> None:
        """Revoca las claves y credenciales anteriores después de retirar las bases de demo."""
        self.state['credentials'] = {'admin_password': secrets.token_urlsafe(24), 'test_password': secrets.token_urlsafe(24)}
        self.state['resources'] = {}
        self.state['key_fingerprint'] = None
        self.state['phase'] = 'initializing'
        self.save()
        self.generate_keys()
        self.write_env()
        for name in ['manifest.json', 'postman_environment.json']:
            path = self.directory / name
            if path.is_symlink():
                raise DemoError('Un archivo de salida es un enlace simbólico.')
            path.unlink(missing_ok=True)
