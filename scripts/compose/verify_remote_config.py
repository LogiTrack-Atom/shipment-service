"""Comprueba la matriz de Config Server y contratos previos al arranque Sprint03.

Solo consulta HTTP GET. El informe contiene resultados, nunca valores del Environment.
No acredita el consumo efectivo de propiedades por las aplicaciones cliente.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

APPLICATIONS = {'eureka-server': 8065, 'api-gateway': 8066, 'shipment-service': 8080,
                'courier-service': 8081, 'security-service': 8082}
ROUTES = {'security-service': 'apisecurity', 'courier-service': 'apicourier',
          'shipment-service': 'apishipment'}
ROUTE_PREFIX = 'spring.cloud.gateway.server.webflux.routes'


def assess(document: dict, application: str, profile: str, label: str) -> dict:
    """Evalúa propiedades efectivas de una respuesta sin copiarlas al resultado."""
    sources = document.get('propertySources', [])
    properties = {}
    for source in reversed(sources):
        properties.update(source['source'])
    checks = []

    def check(name: str, valid: bool) -> None:
        checks.append({'criterio': name, 'aprobado': bool(valid)})

    check('aplicación y perfil solicitados', document.get('name') == application
          and document.get('profiles') == [profile])
    check('revisión Git exacta', document.get('version') == label)
    check('fuentes no vacías', bool(sources) and bool(properties))
    check('nombre de aplicación', properties.get('spring.application.name') == application)
    # Config Server conserva placeholders: se comprueba su valor predeterminado,
    # no se presenta como una resolución o un binding realizado por el cliente.
    port = str(properties.get('server.port', ''))
    check('puerto interno acordado', port == str(APPLICATIONS[application])
          or bool(re.fullmatch(r'\$\{[A-Z_]+:' + str(APPLICATIONS[application]) + r'\}', port)))
    if profile != 'default':
        check('precedencia del marcador de perfil',
              properties.get('logitrack.config.marker') == application + '-' + profile)
    if application == 'eureka-server':
        for key in ['register-with-eureka', 'fetch-registry']:
            check('servidor autónomo: ' + key, properties.get('eureka.client.' + key) is False)
    else:
        zone = properties.get('eureka.client.service-url.defaultZone')
        check('placeholder Eureka compartido', zone == '${EUREKA_URL:http://localhost:8065/eureka/}')
        check('duración JWT acordada', properties.get('logitrack.security.access-token-ttl') in ['15m', '900s'])
    if application == 'api-gateway':
        route_ids = {key[:-3]: value for key, value in properties.items()
                     if re.fullmatch(re.escape(ROUTE_PREFIX) + r'\[\d+\]\.id', key)}
        check('tres rutas explícitas Gateway 5', len(route_ids) == 3
              and set(route_ids.values()) == set(ROUTES))
        check('sin rutas bajo la clave anterior', not any(
            key.startswith('spring.cloud.gateway.routes') for key in properties))
        for service, prefix in ROUTES.items():
            matches = [key for key, value in route_ids.items() if value == service]
            root = matches[0] if len(matches) == 1 else ''
            check('ruta pública de ' + service, bool(root)
                  and properties.get(root + '.uri') == 'lb://' + service.upper()
                  and properties.get(root + '.predicates[0]') == 'Path=/applications/' + prefix + '/**'
                  and properties.get(root + '.filters[0]') == 'StripPrefix=2')
    return {'aplicacion': application, 'perfil': profile, 'criterios': checks,
            'aprobado': all(item['aprobado'] for item in checks)}


def request_environment(base: str, application: str, profile: str, label: str) -> dict:
    """Lee JSON acotado sin reenviar credenciales ni seguir redirecciones."""
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None

    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    request = urllib.request.Request(base.rstrip('/') + '/' + application + '/' + profile + '/' + label,
                                     headers={'Accept': 'application/json'})
    with opener.open(request, timeout=15) as response:
        data = response.read(1024 * 1024 + 1)
    if len(data) > 1024 * 1024:
        raise ValueError('Respuesta demasiado grande.')
    return json.loads(data)


def main() -> int:
    """Consulta quince combinaciones y devuelve fallo si algún contrato no se cumple."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config-url', required=True, help='URL HTTP de loopback del diagnóstico Config Server.')
    parser.add_argument('--config-label', required=True, help='SHA completo del repositorio de configuración.')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    url = urlsplit(args.config_url)
    if (url.scheme != 'http' or url.hostname not in {'127.0.0.1', 'localhost', '::1'}
            or url.username or url.password or url.query or url.fragment or url.path not in {'', '/'}
            or not re.fullmatch('[0-9a-f]{40}', args.config_label)):
        parser.error('Usar HTTP loopback sin credenciales y una revisión Git SHA completa.')
    results = []
    for application in APPLICATIONS:
        for profile in ['default', 'dev', 'prod']:
            try:
                result = assess(request_environment(args.config_url, application, profile, args.config_label),
                                application, profile, args.config_label)
            except (OSError, ValueError, KeyError, TypeError, AttributeError):
                result = {'aplicacion': application, 'perfil': profile, 'aprobado': False,
                          'criterios': [{'criterio': 'respuesta HTTP y Environment válidos', 'aprobado': False}]}
            results.append(result)
    report = {'fecha_utc': datetime.now(timezone.utc).isoformat(), 'config_label': args.config_label,
              'alcance': 'respuestas reales de Config Server; no acredita consumo de los clientes',
              'aprobado': all(item['aprobado'] for item in results), 'resultados': results}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'combinaciones': len(results), 'aprobadas': sum(x['aprobado'] for x in results),
                      'resultado': 'aprobado' if report['aprobado'] else 'contratos pendientes'}, ensure_ascii=False))
    return 0 if report['aprobado'] else 1


if __name__ == '__main__':
    sys.exit(main())
