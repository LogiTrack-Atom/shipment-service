"""Verifica la entrega Sprint02 con siete contenedores y recursos propios descartables.

Usa únicamente imágenes publicadas y el Compose existente. Las credenciales y las
claves se generan en un directorio temporal excluido de Git. El informe público
contiene resultados y versiones; no contiene cuerpos de autenticación ni secretos.
"""
from __future__ import annotations

import io
import json
import os
import secrets
import shutil
import socket
import subprocess
import tempfile
import tarfile
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONFIG_COMMIT = 'b40bd4d0eecd5eff6e5ef0a7b23d0a106a39ba13'
SOURCE_COMMIT = '938496bae144d559187fa59368e155eea2525af9'
VERSION = 'sprint02-1.0.0'


def command(args: list[str], *, capture: bool = False) -> str:
    """Ejecuta herramientas sin imprimir argumentos que puedan contener secretos."""
    result = subprocess.run(args, cwd=ROOT, text=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None, check=False)
    if result.returncode:
        raise RuntimeError(f'La herramienta {args[0]} terminó con código {result.returncode}.')
    return result.stdout if capture else ''


def reserve_ports() -> list[int]:
    """Selecciona cuatro puertos distintos de loopback para este ensayo."""
    sockets = []
    try:
        for _ in range(4):
            connection = socket.socket()
            connection.bind(('127.0.0.1', 0))
            sockets.append(connection)
        return [connection.getsockname()[1] for connection in sockets]
    finally:
        for connection in sockets:
            connection.close()


class Baseline:
    """Aísla el proyecto Compose, los secretos y las solicitudes de un único ensayo."""

    def __init__(self, directory: Path):
        self.directory = directory
        self.project = 'lt-s02-baseline-' + uuid.uuid4().hex[:12]
        self.env_file = directory / 'baseline.env'
        self.source = directory / 'source'
        self.password = secrets.token_urlsafe(24)
        self.admin_email = 'admin-' + uuid.uuid4().hex + '@example.test'
        self.ports = dict(zip(['shipment', 'courier', 'security', 'config'], reserve_ports()))
        self.results: list[dict] = []
        self.values: dict[str, str] = {}

    def compose(self, *args: str, capture: bool = False) -> str:
        """Opera exclusivamente sobre el proyecto y archivo de entorno de este ensayo."""
        return command(['docker', 'compose', '--env-file', str(self.env_file),
                        '--project-name', self.project, '--file', str(self.source / 'docker-compose.yaml'),
                        *args], capture=capture)

    def prepare(self) -> None:
        """Genera claves y configuración privadas para las imágenes históricas fijadas."""
        os.chmod(self.directory, 0o750)
        archive = subprocess.run(['git', 'archive', SOURCE_COMMIT, 'docker-compose.yaml', 'docker/mysql'],
                                 cwd=ROOT, capture_output=True, check=True).stdout
        self.source.mkdir()
        with tarfile.open(fileobj=io.BytesIO(archive)) as bundle:
            for member in bundle.getmembers():
                destination = (self.source / member.name).resolve()
                if not destination.is_relative_to(self.source.resolve()):
                    raise RuntimeError('Ruta de snapshot fuera del directorio de prueba.')
                if member.isdir():
                    destination.mkdir(parents=True, exist_ok=True)
                elif member.isfile():
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with bundle.extractfile(member) as source, destination.open('wb') as target:
                        shutil.copyfileobj(source, target)
                else:
                    raise RuntimeError('El snapshot contiene un tipo de archivo no permitido.')
        private_key, public_key = self.directory / 'private.pem', self.directory / 'public.pem'
        command(['openssl', 'genpkey', '-quiet', '-algorithm', 'RSA', '-pkeyopt',
                 'rsa_keygen_bits:2048', '-out', str(private_key)], capture=True)
        command(['openssl', 'pkey', '-in', str(private_key), '-pubout', '-out', str(public_key)], capture=True)
        os.chmod(private_key, 0o640)
        os.chmod(public_key, 0o640)
        self.values = {
            'COMPOSE_PROJECT_NAME': self.project,
            'SPRING_PROFILES_ACTIVE': 'dev', 'CONFIG_LABEL': CONFIG_COMMIT,
            'JWT_PRIVATE_KEY_FILE': str(private_key), 'JWT_PUBLIC_KEY_FILE': str(public_key),
            'KEYS_GID': str(os.getgid()), 'BOOTSTRAP_ADMIN_ENABLED': 'true',
            'BOOTSTRAP_ADMIN_EMAIL': self.admin_email, 'BOOTSTRAP_ADMIN_PASSWORD': self.password,
            'SHIPMENT_HOST_PORT': str(self.ports['shipment']),
            'COURIER_HOST_PORT': str(self.ports['courier']),
            'SECURITY_HOST_PORT': str(self.ports['security']),
            'CONFIG_SERVER_HOST_PORT': str(self.ports['config']),
        }
        for name in ['shipment', 'courier', 'security']:
            for suffix in ['DB_PASSWORD', 'DB_ROOT_PASSWORD']:
                self.values[name.upper() + '_' + suffix] = secrets.token_urlsafe(24)
            self.values[name.upper() + '_IMAGE'] = f'maxdz10/{name}-service:{VERSION}'
        self.values['CONFIGSERVER_IMAGE'] = f'maxdz10/configserver:{VERSION}'
        self.write_env()

    def write_env(self) -> None:
        """Escribe únicamente el archivo privado de recursos generados por el ensayo."""
        self.env_file.write_text(''.join(f'{key}={value}\n' for key, value in self.values.items()))
        os.chmod(self.env_file, 0o600)

    def request(self, name: str, service: str, method: str, path: str, expected: int,
                *, token: str | None = None, body: dict | None = None):
        """Comprueba HTTP sin registrar tokens, contraseñas o cuerpos de respuesta."""
        headers = {'Accept': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        url = f'http://127.0.0.1:{self.ports[service]}' + path
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                status, payload = response.status, response.read()
        except urllib.error.HTTPError as response:
            status, payload = response.code, response.read()
        self.results.append({'escenario': name, 'http': status, 'esperado': expected})
        if status != expected:
            raise AssertionError(f'{name}: HTTP {status}, se esperaba {expected}.')
        return json.loads(payload) if payload else None

    def login(self, email: str) -> str:
        """Obtiene un JWT real de una cuenta creada en las bases del ensayo."""
        return self.request('login', 'security', 'POST', '/api/v1/auth/login', 200,
                            body={'email': email, 'password': self.password})['accessToken']

    def shipment(self, token: str, email: str) -> dict:
        """Crea un envío PENDIENTE con el contrato público de Sprint02."""
        return self.request('alta de envío', 'shipment', 'POST', '/api/v1/envios', 201,
                            token=token, body={
                                'cliente': {'nombre': 'Cliente de regresión', 'email': email},
                                'origen': {'calle': 'Belgrano', 'numero': '100', 'ciudad': 'Jujuy', 'codigoPostal': '4600'},
                                'destino': {'calle': 'Alvear', 'numero': '200', 'ciudad': 'Salta', 'codigoPostal': '4400'},
                                'pesoKg': 1.25, 'anchoCm': 25.25, 'altoCm': 15.5, 'largoCm': 30.75})

    def verify(self) -> dict:
        """Ejecuta asignación real, rechazos, recuperación, entrega y persistencia."""
        self.compose('config', '--quiet')
        self.compose('pull', '--quiet')
        self.compose('up', '-d', '--wait', '--wait-timeout', '240')
        for service in ['shipment', 'courier', 'security', 'config']:
            self.request('salud ' + service, service, 'GET', '/actuator/health', 200)
        admin = self.login(self.admin_email)
        customer_email = 'cliente-' + uuid.uuid4().hex + '@example.test'
        self.request('registro CLIENTE', 'security', 'POST', '/api/v1/auth/register', 201,
                     body={'email': customer_email, 'password': self.password})
        customer = self.login(customer_email)
        courier = self.request('alta de repartidor', 'courier', 'POST', '/api/v1/repartidores', 201,
                               token=admin, body={'nombre': 'Repartidor de regresión', 'tipoVehiculo': 'Moto'})
        courier_id = courier['id']
        shipment = self.shipment(customer, customer_email)
        shipment_id = shipment['id']
        path = '/api/v1/envios/' + shipment_id
        self.request('repartidor inexistente', 'shipment', 'PUT', path + '/repartidor', 404,
                     token=admin, body={'repartidorId': str(uuid.uuid4())})
        self.request('desactivar repartidor', 'courier', 'PATCH',
                     '/api/v1/repartidores/' + courier_id + '/estado', 200,
                     token=admin, body={'estado': 'INACTIVO'})
        self.request('rechazo de repartidor inactivo', 'shipment', 'PUT', path + '/repartidor', 422,
                     token=admin, body={'repartidorId': courier_id})
        unchanged = self.request('rechazos sin mutación', 'shipment', 'GET', path, 200, token=customer)
        assert unchanged == shipment, 'Los rechazos modificaron el envío.'
        self.compose('stop', 'courier-service')
        self.request('caída real de courier', 'shipment', 'PUT', path + '/repartidor', 503,
                     token=admin, body={'repartidorId': courier_id})
        unchanged = self.request('caída sin mutación', 'shipment', 'GET', path, 200, token=customer)
        assert unchanged == shipment, 'La caída del proveedor modificó el envío.'
        self.compose('up', '-d', '--wait', '--wait-timeout', '120', 'courier-service')
        self.request('reactivar repartidor', 'courier', 'PATCH',
                     '/api/v1/repartidores/' + courier_id + '/estado', 200,
                     token=admin, body={'estado': 'ACTIVO'})
        assigned = self.request('asignación tras recuperación', 'shipment', 'PUT', path + '/repartidor', 200,
                                token=admin, body={'repartidorId': courier_id})
        assert assigned['repartidorId'] == courier_id and assigned['estado'] == 'PENDIENTE'
        history = self.request('asignación sin evento de estado', 'shipment', 'GET', path + '/historial', 200, token=customer)
        assert len(history) == 1
        self.request('acceso anónimo', 'shipment', 'GET', path, 401)
        self.request('JWT alterado', 'shipment', 'GET', path, 401, token=customer + 'alterado')
        self.request('CLIENTE no asigna', 'shipment', 'PUT', path + '/repartidor', 403,
                     token=customer, body={'repartidorId': courier_id})
        other_email = 'otro-' + uuid.uuid4().hex + '@example.test'
        self.request('registro de otro cliente', 'security', 'POST', '/api/v1/auth/register', 201,
                     body={'email': other_email, 'password': self.password})
        other = self.login(other_email)
        self.request('recurso ajeno', 'shipment', 'GET', path, 403, token=other)
        driver_email = 'repartidor-' + uuid.uuid4().hex + '@example.test'
        user = self.request('alta de cuenta REPARTIDOR', 'security', 'POST', '/api/v1/usuarios', 201,
                            token=admin, body={'email': driver_email, 'password': self.password, 'rol': 'REPARTIDOR'})
        self.request('identidad en courier', 'courier', 'PUT', '/api/v1/repartidores/' + courier_id + '/usuario', 200,
                     token=admin, body={'usuarioId': user['id']})
        self.request('asociación verificada en shipment', 'shipment', 'PUT',
                     '/api/v1/asociaciones/repartidores/' + courier_id, 200,
                     token=admin, body={'usuarioId': user['id']})
        driver = self.login(driver_email)
        for state in ['EN_RUTA', 'ENTREGADO']:
            changed = self.request('transición ' + state, 'shipment', 'PATCH', path + '/estado', 200,
                                   token=driver, body={'estado': state})
            assert changed['estado'] == state
        history = self.request('historial de entrega', 'shipment', 'GET', path + '/historial', 200, token=customer)
        assert len(history) == 3
        self.compose('down')
        self.values['BOOTSTRAP_ADMIN_ENABLED'] = 'false'
        self.write_env()
        self.compose('up', '-d', '--wait', '--wait-timeout', '240')
        customer = self.login(customer_email)
        persisted = self.request('envío tras recreación', 'shipment', 'GET', path, 200, token=customer)
        assert persisted['id'] == shipment_id and persisted['estado'] == 'ENTREGADO'
        assert persisted['repartidorId'] == courier_id
        assert self.request('historial tras recreación', 'shipment', 'GET', path + '/historial', 200, token=customer) == history
        return {'version': VERSION, 'source_commit': SOURCE_COMMIT,
                'config_commit': CONFIG_COMMIT, 'contenedores': 7,
                'solicitudes': self.results, 'resultado': 'aprobado',
                'asignacion_real': True, 'rechazos_sin_mutacion': True,
                'recuperacion_courier': True, 'persistencia_tras_recreacion': True}

    def cleanup(self) -> None:
        """Retira únicamente los recursos descartables creados por este objeto."""
        if self.env_file.exists() and self.project.startswith('lt-s02-baseline-'):
            self.compose('down', '--volumes', '--remove-orphans')


def main() -> None:
    """Ejecuta la regresión y conserva solo un informe sanitizado bajo target."""
    if shutil.which('docker') is None or shutil.which('openssl') is None:
        raise RuntimeError('Se requieren Docker Compose y OpenSSL.')
    command(['docker', 'info'], capture=True)
    target = ROOT / 'target'
    target.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='baseline-private-', dir=target) as temporary:
        baseline = Baseline(Path(temporary))
        try:
            baseline.prepare()
            result = baseline.verify()
            (target / 'sprint02-baseline.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
            print(json.dumps(result, ensure_ascii=False), flush=True)
        finally:
            baseline.cleanup()


if __name__ == '__main__':
    main()
