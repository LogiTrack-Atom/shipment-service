"""Verifica aislamiento de archivos, propiedad de Docker y comportamiento de carga."""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import manage
import runtime
from seed import Seed


class RuntimeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='demo-unit-')
        self.directory = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def initialized(self):
        instance = runtime.Runtime(self.directory)
        with patch.object(runtime, 'ports', return_value=dict(shipment=41001, courier=41002, security=41003, config=41004)):
            instance.initialize()
        return instance

    def test_rejects_unmanaged_nonempty_directory(self):
        marker = self.directory / 'datos-existentes.txt'
        marker.write_text('conservar')
        with self.assertRaises(runtime.DemoError):
            runtime.Runtime(self.directory).initialize()
        self.assertEqual(marker.read_text(), 'conservar')
        self.assertFalse((self.directory / 'runtime.json').exists())

    def test_rejects_symbolic_state_directory(self):
        link = self.directory / 'enlace'
        link.symlink_to(self.directory, target_is_directory=True)
        with self.assertRaises(runtime.DemoError):
            runtime.Runtime(link)

    def test_state_paths_and_secrets_remain_private_and_stable(self):
        instance = self.initialized()
        before = json.loads(instance.state_path.read_text())
        loaded = runtime.Runtime(self.directory)
        loaded.initialize()
        self.assertEqual(before, loaded.state)
        for name in ['runtime.json', 'runtime.env']:
            self.assertEqual((self.directory / name).stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.directory.stat().st_mode & 0o777, 0o700)
        self.assertNotEqual(loaded.values()['SHIPMENT_DB_PASSWORD'], loaded.values()['SECURITY_DB_PASSWORD'])
        self.assertNotEqual(loaded.values()['SHIPMENT_DB_PASSWORD'], loaded.values()['SHIPMENT_DB_ROOT_PASSWORD'])
        self.assertIn('@sha256:', loaded.values()['SHIPMENT_IMAGE'])

    def test_rejects_moved_or_invalid_state(self):
        instance = self.initialized()
        data = dict(instance.state)
        data['directory'] = str(self.directory / 'otro')
        runtime.write_json(instance.state_path, data)
        with self.assertRaises(runtime.DemoError):
            runtime.Runtime(self.directory)

    def test_rejects_modified_compose_before_docker(self):
        instance = self.initialized()
        (instance.source / 'docker-compose.yaml').write_text('services: {}')
        with patch.object(runtime, 'run') as docker:
            with self.assertRaises(runtime.DemoError):
                instance.down(remove_volumes=True)
            docker.assert_not_called()

    def test_rejects_symlink_inside_snapshot(self):
        instance = self.initialized()
        (instance.source / 'extra').symlink_to(instance.state_path)
        with self.assertRaises(runtime.DemoError):
            instance.verify_snapshot()

    def test_rejects_foreign_volume_before_down(self):
        instance = self.initialized()
        volume = instance.project + '_shipment_mysql_data'

        def docker(args, **kwargs):
            if args[:3] == ['docker', 'volume', 'ls']:
                return volume + '\n'
            if args[:3] == ['docker', 'volume', 'inspect']:
                return json.dumps([{'Labels': {'com.docker.compose.project': 'produccion'}}])
            raise AssertionError('No debe ejecutar otras operaciones Docker.')

        with patch.object(runtime, 'run', side_effect=docker), patch.object(instance, 'compose') as compose:
            with self.assertRaises(runtime.DemoError):
                instance.down(remove_volumes=True)
            compose.assert_not_called()

    def test_rejects_foreign_container_configuration(self):
        instance = self.initialized()

        def docker(args, **kwargs):
            if args[:3] == ['docker', 'volume', 'ls']:
                return ''
            if args[:3] == ['docker', 'ps', '-aq']:
                return 'contenedor-ajeno'
            if args[:2] == ['docker', 'inspect']:
                return json.dumps([{'Config': {'Labels': {'com.docker.compose.project': instance.project,
                    'com.docker.compose.service': 'shipment-service', 'com.docker.compose.project.config_files': '/produccion/compose.yaml'}}}])
            raise AssertionError('Operación no prevista.')

        with patch.object(runtime, 'run', side_effect=docker):
            with self.assertRaises(runtime.DemoError):
                instance.guard_resources()

    def test_reset_rotates_credentials_keys_and_invalidates_export(self):
        instance = self.initialized()
        password = instance.state['credentials']['admin_password']
        fingerprint = instance.state['key_fingerprint']
        runtime.write_json(self.directory / 'postman_environment.json', {'values': []})
        with patch.object(instance, 'guard_resources'), patch.object(instance, 'down') as down:
            result = manage.reset(instance, restart=False)
        down.assert_called_once_with(remove_volumes=True)
        self.assertNotEqual(password, instance.state['credentials']['admin_password'])
        self.assertNotEqual(fingerprint, instance.state['key_fingerprint'])
        self.assertFalse((self.directory / 'postman_environment.json').exists())
        self.assertEqual(instance.state['phase'], 'initializing')
        self.assertEqual(result['project'], instance.project)

    def test_reset_does_not_rotate_when_ownership_is_not_proven(self):
        instance = self.initialized()
        before = dict(instance.state['credentials'])
        with patch.object(instance, 'guard_resources', side_effect=runtime.DemoError('recurso ajeno')), patch.object(instance, 'down') as down:
            with self.assertRaises(runtime.DemoError):
                manage.reset(instance)
        down.assert_not_called()
        self.assertEqual(before, instance.state['credentials'])

    def test_down_preserves_volumes(self):
        instance = self.initialized()
        with patch.object(instance, 'guard_resources'), patch.object(instance, 'compose') as compose:
            instance.down()
        compose.assert_called_once_with('down', capture=False)

    def test_env_symlink_or_hardlink_never_overwrites_another_file(self):
        instance = self.initialized()
        external = self.directory / 'ajeno.env'
        external.write_text('datos-que-se-conservan')
        instance.env_file.unlink()
        instance.env_file.symlink_to(external)
        with self.assertRaises(runtime.DemoError):
            instance.write_env()
        instance.env_file.unlink()
        os.link(external, instance.env_file)
        with self.assertRaises(runtime.DemoError):
            instance.write_env()
        self.assertEqual(external.read_text(), 'datos-que-se-conservan')

    def test_atomic_json_never_follows_a_symlink(self):
        external = self.directory / 'ajeno.json'
        external.write_text('conservar')
        link = self.directory / 'estado.json'
        link.symlink_to(external)
        with self.assertRaises(runtime.DemoError):
            runtime.write_json(link, {'nuevo': True})
        self.assertEqual(external.read_text(), 'conservar')

    def test_lock_rejects_unmanaged_files(self):
        marker = self.directory / 'ajeno'
        marker.write_text('conservar')
        with self.assertRaises(runtime.DemoError):
            with manage.locked_directory(self.directory):
                self.fail('No debe adquirir control de un directorio ajeno.')
        self.assertFalse((self.directory / '.lock').exists())


class SeedingTest(unittest.TestCase):
    def test_interrupted_finalization_disables_bootstrap_before_ready(self):
        instance = Mock()
        instance.state = {'phase': 'finalizing', 'resources': {'existentes': True}}
        with patch.object(manage, 'Seed') as factory:
            instance.compose.side_effect = runtime.DemoError('Recreación interrumpida.')
            with self.assertRaises(runtime.DemoError):
                manage.seed(instance)
            self.assertEqual(instance.state['phase'], 'finalizing')
            factory.return_value.provision.assert_not_called()
            factory.return_value.export.assert_not_called()
            instance.compose.side_effect = None
            manage.seed(instance)
            self.assertEqual(instance.state['phase'], 'ready')
            instance.wait_for_routes.assert_called_once()
            factory.return_value.provision.assert_not_called()
            factory.return_value.export.assert_called_once()

    def test_ready_seed_is_read_only_for_business_data(self):
        instance = Mock()
        instance.state = {'phase': 'ready', 'resources': {'existentes': True}}
        with patch.object(manage, 'Seed') as factory:
            factory.return_value.verify.return_value = {'estado': 'inicial verificado'}
            result = manage.seed(instance)
            factory.return_value.provision.assert_not_called()
            factory.return_value.verify.assert_called_once()
            factory.return_value.export.assert_called_once()
        instance.compose.assert_not_called()
        instance.save.assert_not_called()
        self.assertEqual(result['estado'], 'inicial verificado')

    def test_modified_demo_is_not_repaired_implicitly(self):
        instance = Mock()
        instance.state = {'phase': 'ready'}
        with patch.object(manage, 'Seed') as factory:
            factory.return_value.verify.side_effect = runtime.DemoError('estado modificado; ejecutar reset')
            with self.assertRaises(runtime.DemoError):
                manage.seed(instance)
            factory.return_value.provision.assert_not_called()
            factory.return_value.export.assert_not_called()
        instance.compose.assert_not_called()

    def test_ambiguous_resource_never_selects_a_random_identity(self):
        instance = Mock()
        loader = Seed(instance)
        with self.assertRaises(runtime.DemoError):
            loader.unique([{'id': 'uno'}, {'id': 'otro'}], 'repartidor')


if __name__ == '__main__':
    unittest.main()
