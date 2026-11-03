"""Valida el modelo Compose de Sprint03 sin iniciar, construir ni retirar recursos."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APPLICATIONS = {'configserver': 8085, 'eureka-server': 8065, 'security-service': 8082,
                'courier-service': 8081, 'shipment-service': 8080, 'api-gateway': 8066}
DOMAINS = {'shipment': ('envios', 'init'), 'courier': ('repartidores', 'courier'),
           'security': ('usuarios', 'security')}
CLIENTS = {'security-service', 'courier-service', 'shipment-service', 'api-gateway'}
LEGACY_LABEL = 'b40bd4d0eecd5eff6e5ef0a7b23d0a106a39ba13'


class ModelError(ValueError):
    """Describe un incumplimiento sin incluir valores privados del modelo."""


def require(condition: bool, message: str) -> None:
    """Rechaza un modelo con un mensaje estático seguro."""
    if not condition:
        raise ModelError(message)


def versioned_image(reference: str) -> bool:
    """Exige versión explícita o digest SHA-256 y rechaza latest y credenciales en URL."""
    if not isinstance(reference, str) or '://' in reference or any(c.isspace() for c in reference):
        return False
    parts = reference.split('@')
    if len(parts) > 2 or (len(parts) == 2 and not re.fullmatch(r'sha256:[0-9a-f]{64}', parts[1])):
        return False
    name = parts[0]
    tag = None
    if ':' in name.rsplit('/', 1)[-1]:
        name, tag = name.rsplit(':', 1)
        if not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}', tag) or tag.lower() == 'latest':
            return False
    return bool((tag or len(parts) == 2) and re.fullmatch(
        r'[a-z0-9][a-z0-9._-]*(?::[0-9]+)?(?:/[a-z0-9][a-z0-9._-]*)*', name))


def render(env_file: Path, *, build=False, diagnostics=False, environment=None) -> dict:
    """Solicita únicamente config JSON y mantiene stdout/stderr privados en memoria."""
    require(env_file.is_file(), 'Se requiere un archivo de entorno privado existente.')
    command = ['docker', 'compose', '--env-file', str(env_file.resolve()), '-f', 'compose.sprint03.yaml']
    if build:
        command += ['-f', 'compose.sprint03.build.yaml']
    if diagnostics:
        command += ['-f', 'compose.sprint03.diagnostics.yaml']
    result = subprocess.run(command + ['config', '--format', 'json'], cwd=ROOT,
                            env=environment, text=True, capture_output=True, timeout=20)
    require(result.returncode == 0, 'Compose rechazó el modelo; comprobar las variables requeridas y sus formatos.')
    try:
        return json.loads(result.stdout)
    except ValueError:
        raise ModelError('Compose no devolvió un modelo JSON válido.') from None


def validate(model: dict, *, build=False, diagnostics=False) -> dict:
    """Comprueba topología, entrada pública, secretos, revisiones y aislamiento declarado."""
    services = model['services']
    project = model['name']
    databases = {name + '-mysql' for name in DOMAINS}
    require(set(services) == set(APPLICATIONS) | databases, 'Se requieren seis aplicaciones y tres MySQL.')
    require(project and not project.startswith('logitrack-sprint02'), 'Usar un proyecto distinto de la entrega Sprint02.')
    volume_keys = {name + '_mysql_data' for name in DOMAINS}
    require(set(model.get('volumes', {})) == volume_keys, 'Las tres bases necesitan volúmenes independientes.')
    for name, definition in model['volumes'].items():
        require(not definition.get('external') and definition.get('name') == project + '_' + name,
                'Los volúmenes deben pertenecer al proyecto, sin referencias externas.')
    require(set(model.get('networks', {})) == {'logitrack-net'}, 'Se requiere la red propia de LogiTrack.')
    network = model['networks']['logitrack-net']
    require(not network.get('external') and network.get('name') == project + '_logitrack-net',
            'La red debe pertenecer al proyecto.')

    published = set()
    for name, service in services.items():
        require(versioned_image(service.get('image')), 'Todas las imágenes necesitan versión explícita o digest, sin latest.')
        if name in APPLICATIONS and name != 'configserver':
            require('sprint02' not in service['image'].lower(), 'Las aplicaciones modificadas requieren imágenes Sprint03.')
        require(not service.get('privileged') and not service.get('container_name') and not service.get('profiles'),
                'No se admiten privilegios, nombres globales ni componentes omitidos por perfiles.')
        require(set(service.get('networks', {})) == {'logitrack-net'} and not service.get('network_mode'),
                'Todos los componentes deben usar la red propia.')
        health = service.get('healthcheck', {})
        require(health and not health.get('disable') and health.get('test') != ['NONE'],
                'Las imágenes deben conservar su sonda de salud habilitada.')
        require('build' in service if build and name in APPLICATIONS else 'build' not in service,
                'La construcción local debe ser explícita y limitarse a las seis aplicaciones.')
        if build and name in APPLICATIONS:
            expected = ROOT if name == 'shipment-service' else ROOT.parent / name
            require(Path(service['build']['context']) == expected and service['build'].get('dockerfile') == 'Dockerfile',
                    'Los contextos de construcción deben apuntar a los repositorios hermanos.')
        ports = service.get('ports', [])
        allowed = name == 'api-gateway' or (diagnostics and name in APPLICATIONS)
        require(len(ports) == (1 if allowed else 0), 'Solo Gateway publica negocio; el diagnóstico local es optativo y MySQL no publica puertos.')
        for port in ports:
            require(port.get('host_ip') == '127.0.0.1' and port.get('protocol') == 'tcp'
                    and port['target'] == APPLICATIONS[name], 'Los puertos publicados deben ser locales y conservar el destino interno.')
            key = (port['host_ip'], port['published'])
            require(key not in published, 'Los puertos locales publicados no pueden repetirse.')
            published.add(key)

    label = services['eureka-server']['environment']['CONFIG_LABEL']
    require(re.fullmatch(r'[0-9a-f]{40}', label) is not None and label != LEGACY_LABEL,
            'CONFIG_LABEL debe fijar un commit Sprint03, no una rama ni la configuración histórica.')
    require(services['configserver']['environment']['CONFIG_GIT_DEFAULT_LABEL'] == label,
            'Config Server y sus clientes deben usar la misma revisión Git.')
    require(services['configserver']['environment']['CONFIG_GIT_URI'] == 'https://github.com/LogiTrack-Atom/configserver-ms.git',
            'Config Server debe consultar el repositorio acordado.')
    dependencies = {'eureka-server': {'configserver'}, 'security-service': {'configserver', 'eureka-server', 'security-mysql'},
                    'courier-service': {'configserver', 'eureka-server', 'courier-mysql', 'security-service'},
                    'shipment-service': {'configserver', 'eureka-server', 'shipment-mysql', 'security-service', 'courier-service'},
                    'api-gateway': {'configserver', 'eureka-server', 'security-service', 'courier-service', 'shipment-service'}}
    for name, expected in dependencies.items():
        actual = services[name].get('depends_on', {})
        require(expected <= set(actual) and all(value['condition'] == 'service_healthy' for value in actual.values()),
                'El arranque debe esperar salud en Config, Eureka y los proveedores requeridos.')
        env = services[name]['environment']
        require(env['CONFIG_LABEL'] == label and env['CONFIG_SERVER_URL'] == 'http://configserver:8085'
                and env['SPRING_PROFILES_ACTIVE'] in {'dev', 'prod'}, 'Los clientes necesitan configuración remota fijada y perfil dev/prod.')
        require(env['SERVER_PORT'] == str(APPLICATIONS[name]), 'Los puertos anunciados deben coincidir con los internos.')
    for name in CLIENTS:
        env = services[name]['environment']
        require(env.get('EUREKA_DEFAULT_ZONE') == 'http://eureka-server:8065/eureka/'
                and env.get('EUREKA_CLIENT_ENABLED') == 'true' and env.get('EUREKA_INSTANCE_HOSTNAME') == name
                and env.get('EUREKA_INSTANCE_PREFER_IP_ADDRESS') == 'false',
                'Los cuatro clientes deben resolver Eureka y anunciar su DNS de Docker.')
    require(len({services[name]['environment']['SPRING_PROFILES_ACTIVE'] for name in dependencies}) == 1,
            'Los cinco clientes de configuración deben utilizar el mismo perfil.')
    eureka = services['eureka-server']['environment']
    require(eureka.get('EUREKA_CLIENT_REGISTER_WITH_EUREKA') == 'false' and eureka.get('EUREKA_CLIENT_FETCH_REGISTRY') == 'false',
            'El servidor Eureka autónomo no debe registrarse ni consultar su propio registro.')

    passwords = []
    for role, (table, folder) in DOMAINS.items():
        database = services[role + '-mysql']
        env = database['environment']
        app = services[role + '-service']['environment']
        require(env['MYSQL_DATABASE'] == role + '_db' and env['MYSQL_USER'] == role + '_app'
                and app['DB_USERNAME'] == env['MYSQL_USER'] and app['DB_PASSWORD'] == env['MYSQL_PASSWORD']
                and app['DB_URL'].startswith('jdbc:mysql://' + role + '-mysql:3306/' + role + '_db' + '?'),
                'Cada servicio debe usar únicamente su propia base y usuario.')
        passwords += [env['MYSQL_PASSWORD'], env['MYSQL_ROOT_PASSWORD']]
        mounts = {item['target']: item for item in database['volumes']}
        require(set(mounts) == {'/var/lib/mysql', '/docker-entrypoint-initdb.d'}
                and mounts['/var/lib/mysql']['source'] == role + '_mysql_data'
                and mounts['/docker-entrypoint-initdb.d']['source'] == str(ROOT / 'docker/mysql' / folder)
                and mounts['/docker-entrypoint-initdb.d'].get('read_only'), 'Cada base requiere su volumen y sus esquemas de inicialización de solo lectura.')
        command = ' '.join(database['healthcheck'].get('test', []))
        require(table in command and 'MYSQL_USER' in command and 'MYSQL_PASSWORD' in command,
                'La salud de MySQL debe comprobar una tabla con el usuario de aplicación.')
    require(all(passwords) and len(set(passwords)) == 6, 'Las seis contraseñas de base deben ser externas, no vacías y distintas.')
    public_source = None
    for name in APPLICATIONS:
        service = services[name]
        env = service['environment']
        mounts = {item['target']: item for item in service.get('volumes', [])}
        expected = {'/run/keys/public.pem'} if name in CLIENTS else set()
        if name == 'security-service':
            expected.add('/run/keys/private.pem')
        require(set(mounts) == expected, 'La privada se monta exclusivamente en security; solo los verificadores reciben la pública.')
        for mount in mounts.values():
            require(mount['type'] == 'bind' and mount.get('read_only') and not mount.get('bind', {}).get('create_host_path', True),
                    'Las claves externas requieren bind de solo lectura, sin creación implícita.')
        if name in CLIENTS:
            source = mounts['/run/keys/public.pem']['source']
            public_source = source if public_source is None else public_source
            require(source == public_source and env.get('JWT_PUBLIC_KEY_LOCATION') == 'file:/run/keys/public.pem',
                    'Los verificadores deben usar el mismo archivo público externo.')
        require(name == 'security-service' or 'JWT_PRIVATE_KEY_LOCATION' not in env, 'Solo security puede recibir la ubicación de la privada.')
        require(not any(key.startswith('MYSQL_') for key in env), 'Las aplicaciones no deben recibir contraseñas administrativas de MySQL.')
        if name not in {role + '-service' for role in DOMAINS}:
            require(not any(key.startswith('DB_') or key.startswith('MYSQL_') for key in env), 'Config, Eureka y Gateway no reciben credenciales MySQL.')
    private = {item['target']: item for item in services['security-service']['volumes']}['/run/keys/private.pem']
    require(private['source'] != public_source, 'La pública y la privada deben ser archivos externos distintos.')
    require(services['security-service']['environment'].get('JWT_PRIVATE_KEY_LOCATION') == 'file:/run/keys/private.pem',
            'Security debe usar su montaje privado externo.')
    return {'alcance': 'modelo Compose; no se iniciaron contenedores', 'aplicaciones': 6, 'bases_mysql': 3,
            'componentes_declarados': 9, 'clientes_eureka_declarados': 4, 'diagnostico_local': diagnostics,
            'construccion_local': build, 'resultado': 'modelo válido'}


def main() -> int:
    """Imprime solo un resumen sin secretos; nunca presenta el JSON privado de Compose."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, required=True)
    parser.add_argument('--build', action='store_true', help='Validar también el override de construcción; no construye imágenes.')
    parser.add_argument('--diagnostics', action='store_true', help='Validar también los puertos de diagnóstico local.')
    args = parser.parse_args()
    try:
        print(json.dumps(validate(render(args.env_file, build=args.build, diagnostics=args.diagnostics),
                                  build=args.build, diagnostics=args.diagnostics), ensure_ascii=False))
        return 0
    except (ModelError, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(str(error) if isinstance(error, ModelError) else 'No se pudo validar el modelo; comprobar Compose y las variables privadas.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
