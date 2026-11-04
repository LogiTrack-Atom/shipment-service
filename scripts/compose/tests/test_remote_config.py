"""Comprueba precedencia, incompatibilidades y transporte del diagnóstico remoto."""
import json
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_remote_config import assess, request_environment

LABEL = 'a' * 40


def gateway():
    """Representa las fuentes ordenadas de una respuesta Config Server."""
    base = {'spring.application.name': 'api-gateway', 'server.port': '${GATEWAY_SERVER_PORT:8066}',
            'eureka.client.service-url.defaultZone': '${EUREKA_URL:http://localhost:8065/eureka/}',
            'logitrack.security.access-token-ttl': '15m'}
    for index, (service, prefix) in enumerate([('security-service', 'apisecurity'),
                                             ('courier-service', 'apicourier'),
                                             ('shipment-service', 'apishipment')]):
        root = 'spring.cloud.gateway.server.webflux.routes[' + str(index) + ']'
        base.update({root + '.id': service, root + '.uri': 'lb://' + service.upper(),
                     root + '.predicates[0]': 'Path=/applications/' + prefix + '/**',
                     root + '.filters[0]': 'StripPrefix=2'})
    return {'name': 'api-gateway', 'profiles': ['dev'], 'version': LABEL,
            'propertySources': [{'source': {'logitrack.config.marker': 'api-gateway-dev'}}, {'source': base}]}


class RemoteConfigurationTest(unittest.TestCase):
    def check_document(self, document):
        return assess(document, 'api-gateway', 'dev', LABEL)

    def test_accepts_agreed_routes_and_configuration(self):
        self.assertTrue(self.check_document(gateway())['aprobado'])

    def test_profile_precedence_can_break_base_route(self):
        document = gateway()
        document['propertySources'][0]['source']['spring.cloud.gateway.server.webflux.routes[0].uri'] = 'http://localhost:8082'
        self.assertFalse(self.check_document(document)['aprobado'])

    def test_rejects_observed_old_namespace_missing_ttl_and_wrong_port(self):
        for mutation in ['namespace', 'ttl', 'port', 'prefix', 'strip']:
            with self.subTest(mutation=mutation):
                document = gateway()
                base = document['propertySources'][1]['source']
                root = 'spring.cloud.gateway.server.webflux.routes[0]'
                if mutation == 'namespace':
                    document['propertySources'][1]['source'] = {
                        key.replace('spring.cloud.gateway.server.webflux.routes', 'spring.cloud.gateway.routes'): value
                        for key, value in base.items()}
                elif mutation == 'ttl':
                    base.pop('logitrack.security.access-token-ttl')
                elif mutation == 'port':
                    base['server.port'] = '${GATEWAY_SERVER_PORT:8060}'
                elif mutation == 'prefix':
                    base[root + '.predicates[0]'] = 'Path=/auth/**'
                else:
                    base.pop(root + '.filters[0]')
                self.assertFalse(self.check_document(document)['aprobado'])

    def test_rejects_different_git_revision_or_wrong_profile(self):
        for key, value in [('version', 'b' * 40), ('profiles', ['prod'])]:
            document = gateway()
            document[key] = value
            self.assertFalse(self.check_document(document)['aprobado'])

    def test_report_does_not_copy_private_environment_or_sources(self):
        document = gateway()
        document['propertySources'][0].update({'name': 'private-source-address'})
        document['propertySources'][0]['source']['DB_PASSWORD'] = 'private-value-for-test'
        serialized = json.dumps(self.check_document(document))
        self.assertNotIn('private-value-for-test', serialized)
        self.assertNotIn('private-source-address', serialized)
        self.assertNotIn('DB_PASSWORD', serialized)

    def test_reads_http_json_and_rejects_redirects(self):
        document = gateway()

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                if self.path.startswith('/redirect/'):
                    self.send_response(302)
                    self.send_header('Location', '/private-destination')
                    self.end_headers()
                else:
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(json.dumps(document).encode())

        with ThreadingHTTPServer(('127.0.0.1', 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                url = 'http://127.0.0.1:' + str(server.server_port)
                self.assertEqual(request_environment(url, 'api-gateway', 'dev', LABEL), document)
                with self.assertRaises(OSError):
                    request_environment(url + '/redirect', 'api-gateway', 'dev', LABEL)
            finally:
                server.shutdown()
                thread.join(timeout=5)


if __name__ == '__main__':
    unittest.main()
