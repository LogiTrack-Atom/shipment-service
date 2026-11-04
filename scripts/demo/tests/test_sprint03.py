"""Prueba referencias, aislamiento y persistencia del estado de la demo Sprint03."""
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from runtime import DemoError, ROOT, Runtime
from runtime_sprint03 import APPLICATIONS, Sprint03Runtime, validate_release


class Sprint03RuntimeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='logitrack-s03-unit-')
        self.directory = Path(self.temporary.name) / 'demo'
        self.release = {'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                        'config_label': 'a' * 40,
                        'images': {name: 'example.invalid/logitrack/' + name + ':sprint03-fixture' for name in APPLICATIONS}}

    def tearDown(self):
        self.temporary.cleanup()

    def test_private_state_and_three_api_urls_share_gateway(self):
        runtime = Sprint03Runtime(self.directory, self.release)
        runtime.initialize()
        loaded = Sprint03Runtime(self.directory)
        self.assertEqual(runtime.state, loaded.state)
        self.assertEqual(len(set(loaded.state['ports'].values())), 6)
        for service, prefix in [('shipment', 'apishipment'), ('courier', 'apicourier'), ('security', 'apisecurity')]:
            self.assertEqual(loaded.api_url(service), 'http://127.0.0.1:' + str(loaded.state['ports']['gateway']) + '/applications/' + prefix)
        self.assertEqual(loaded.values()['CONFIG_LABEL'], self.release['config_label'])
        self.assertEqual(loaded.values()['SPRING_PROFILES_ACTIVE'], 'dev')
        self.assertEqual(self.directory.joinpath('runtime.json').stat().st_mode & 0o777, 0o600)

    def test_refuses_legacy_state_and_changed_manifest(self):
        runtime = Sprint03Runtime(self.directory, self.release)
        runtime.initialize()
        with self.assertRaises(DemoError):
            Runtime(self.directory)
        changed = copy.deepcopy(self.release)
        changed['config_label'] = 'b' * 40
        with self.assertRaises(DemoError):
            Sprint03Runtime(self.directory, changed)

    def test_refuses_modified_snapshot_and_repeated_ports(self):
        runtime = Sprint03Runtime(self.directory, self.release)
        runtime.initialize()
        source = runtime.source / 'compose.sprint03.yaml'
        source.write_text(source.read_text() + '\n# modificado\n')
        with self.assertRaises(DemoError):
            runtime.verify_snapshot()
        runtime.state['ports']['gateway'] = runtime.state['ports']['config']
        runtime.save()
        with self.assertRaises(DemoError):
            Sprint03Runtime(self.directory)

    def test_requires_complete_release_and_explicit_images(self):
        for mutation in ['label', 'latest', 'missing']:
            release = copy.deepcopy(self.release)
            if mutation == 'label':
                release['config_label'] = 'develop'
            elif mutation == 'latest':
                release['images']['api-gateway'] = 'example.invalid/gateway:latest'
            else:
                release['images'].pop('eureka-server')
            with self.assertRaises(DemoError):
                validate_release(release)

    def test_waits_for_gateway_routes_after_registry_is_available(self):
        runtime = Sprint03Runtime(self.directory, self.release)
        runtime.initialize()
        attempts = []

        def request(api, service, method, path, **kwargs):
            if service == 'security':
                return 200, {'accessToken': 'token-sintetico-sin-credenciales'}
            if service == 'shipment':
                attempts.append(service)
                return (503, {}) if len(attempts) == 1 else (200, [])
            return 200, {}

        with patch('seed.Api.request', request), patch('runtime_sprint03.time.sleep'):
            runtime.wait_for_routes()
        self.assertEqual(attempts, ['shipment', 'shipment'])


if __name__ == '__main__':
    unittest.main()
