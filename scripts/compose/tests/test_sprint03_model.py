"""Prueba contratos de aislamiento y reproducibilidad del modelo, sin Docker Engine."""
import copy
import itertools
import os
import secrets
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_sprint03 import APPLICATIONS, ModelError, ROOT, render, validate, versioned_image


class Sprint03ModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='logitrack-compose-model-')
        directory = Path(cls.temporary.name)
        cls.values = {'COMPOSE_PROJECT_NAME': 'lt-compose-sprint03-modelo', 'CONFIG_LABEL': '1' * 40,
                      'SPRING_PROFILES_ACTIVE': 'dev', 'KEYS_GID': str(os.getgid()),
                      'JWT_PUBLIC_KEY_FILE': str(directory / 'public.pem'),
                      'JWT_PRIVATE_KEY_FILE': str(directory / 'private.pem'),
                      'BOOTSTRAP_ADMIN_ENABLED': 'false', 'BOOTSTRAP_ADMIN_EMAIL': '', 'BOOTSTRAP_ADMIN_PASSWORD': ''}
        image_vars = {'configserver': 'CONFIGSERVER', 'eureka-server': 'EUREKA', 'security-service': 'SECURITY',
                      'courier-service': 'COURIER', 'shipment-service': 'SHIPMENT', 'api-gateway': 'GATEWAY'}
        for service, prefix in image_vars.items():
            # Solo sintaxis: estas referencias no se descargan ni representan imágenes publicadas.
            cls.values[prefix + '_IMAGE'] = 'example.invalid/logitrack/' + service + ':sprint03-modelo'
        for role in ['SHIPMENT', 'COURIER', 'SECURITY']:
            cls.values[role + '_DB_PASSWORD'] = secrets.token_urlsafe(24)
            cls.values[role + '_DB_ROOT_PASSWORD'] = secrets.token_urlsafe(24)
        for service, port in APPLICATIONS.items():
            prefix = {'api-gateway': 'GATEWAY', 'configserver': 'CONFIG_SERVER', 'eureka-server': 'EUREKA'}.get(
                service, service.removesuffix('-service').upper())
            cls.values[prefix + '_HOST_PORT'] = str(port)
        cls.env_file = directory / 'model.env'
        descriptor = os.open(cls.env_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'w') as output:
            output.write(''.join(key + '=' + value + '\n' for key, value in cls.values.items()))
        cls.environment = {**os.environ, **cls.values}
        cls.models = {(build, diagnostics): render(cls.env_file, build=build, diagnostics=diagnostics,
                                                  environment=cls.environment)
                      for build, diagnostics in itertools.product([False, True], repeat=2)}

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def base(self):
        return copy.deepcopy(self.models[False, False])

    def test_base_build_diagnostics_and_combined_models(self):
        for (build, diagnostics), model in self.models.items():
            with self.subTest(build=build, diagnostics=diagnostics):
                result = validate(model, build=build, diagnostics=diagnostics)
                self.assertEqual(result['componentes_declarados'], 9)
                self.assertIn('no se iniciaron contenedores', result['alcance'])

    def test_prod_uses_same_revision_and_isolation(self):
        model = render(self.env_file, environment={**self.environment, 'SPRING_PROFILES_ACTIVE': 'prod'})
        self.assertEqual(validate(model)['resultado'], 'modelo válido')

    def test_missing_required_value_fails_without_printing_secrets(self):
        with self.assertRaises(ModelError) as error:
            render(self.env_file, environment={**self.environment, 'EUREKA_IMAGE': ''})
        self.assertNotIn(self.values['SECURITY_DB_PASSWORD'], str(error.exception))

    def test_rejects_private_key_mounted_in_gateway(self):
        model = self.base()
        private = next(x for x in model['services']['security-service']['volumes'] if x['target'].endswith('private.pem'))
        model['services']['api-gateway']['volumes'].append(copy.deepcopy(private))
        with self.assertRaises(ModelError):
            validate(model)

    def test_accepts_normalized_json_that_omits_false_bind_options(self):
        model = self.base()
        for service in model['services'].values():
            for mount in service.get('volumes', []):
                if mount.get('bind', {}).get('create_host_path') is False:
                    mount['bind'].pop('create_host_path')
                    if not mount['bind']:
                        mount.pop('bind')
        self.assertEqual(validate(model)['resultado'], 'modelo válido')

    def test_rejects_automatic_creation_of_key_paths(self):
        model = self.base()
        mount = model['services']['api-gateway']['volumes'][0]
        mount.setdefault('bind', {})['create_host_path'] = True
        with self.assertRaises(ModelError):
            validate(model)

    def test_rejects_direct_domain_publication(self):
        model = self.base()
        model['services']['courier-service']['ports'] = [{'host_ip': '127.0.0.1', 'target': 8081, 'published': '8081', 'protocol': 'tcp'}]
        with self.assertRaises(ModelError):
            validate(model)

    def test_diagnostics_must_remain_on_loopback(self):
        model = copy.deepcopy(self.models[False, True])
        model['services']['eureka-server']['ports'][0]['host_ip'] = '0.0.0.0'
        with self.assertRaises(ModelError):
            validate(model, diagnostics=True)

    def test_rejects_mutable_or_historical_release_references(self):
        for service, reference in [('configserver', 'maxdz10/configserver:latest'),
                                   ('shipment-service', 'maxdz10/shipment-service:sprint02-1.0.0')]:
            with self.subTest(service=service):
                model = self.base()
                model['services'][service]['image'] = reference
                with self.assertRaises(ModelError):
                    validate(model)
        model = self.base()
        model['services']['eureka-server']['environment']['CONFIG_LABEL'] = 'develop'
        with self.assertRaises(ModelError):
            validate(model)

    def test_rejects_mixed_config_revisions(self):
        model = self.base()
        model['services']['security-service']['environment']['CONFIG_LABEL'] = '2' * 40
        with self.assertRaises(ModelError):
            validate(model)

    def test_rejects_cross_database_configuration(self):
        model = self.base()
        model['services']['shipment-service']['environment']['DB_URL'] = model['services']['courier-service']['environment']['DB_URL']
        with self.assertRaises(ModelError):
            validate(model)

    def test_rejects_password_reuse(self):
        model = self.base()
        model['services']['security-mysql']['environment']['MYSQL_ROOT_PASSWORD'] = model['services']['shipment-mysql']['environment']['MYSQL_ROOT_PASSWORD']
        with self.assertRaises(ModelError):
            validate(model)

    def test_rejects_external_data_volume(self):
        model = self.base()
        model['volumes']['shipment_mysql_data']['external'] = True
        with self.assertRaises(ModelError):
            validate(model)

    def test_requires_health_before_starting_dependants(self):
        model = self.base()
        model['services']['api-gateway']['depends_on']['shipment-service']['condition'] = 'service_started'
        with self.assertRaises(ModelError):
            validate(model)

    def test_rejects_disabled_healthcheck(self):
        model = self.base()
        model['services']['eureka-server']['healthcheck']['test'] = ['NONE']
        with self.assertRaises(ModelError):
            validate(model)

    def test_image_versions_and_digests(self):
        for reference in ['maxdz10/api-gateway:v0.3.0', 'registry.example:5000/org/service:v3',
                          'ghcr.io/org/service@sha256:' + 'a' * 64]:
            self.assertTrue(versioned_image(reference))
        for reference in ['maxdz10/api-gateway', 'maxdz10/api-gateway:latest', 'https://user:password@registry/image:v3',
                          'image@sha256:invalid', 'image:bad tag']:
            self.assertFalse(versioned_image(reference))

    def test_private_dotenv_is_ignored_and_examples_remain_versionable(self):
        for name, expected in [('.env.sprint03', 0), ('.env.example', 1), ('.env.component.example', 1), ('.env.sprint03.example', 1)]:
            result = subprocess.run(['git', 'check-ignore', '--no-index', '--quiet', name], cwd=ROOT)
            self.assertEqual(result.returncode, expected)


if __name__ == '__main__':
    unittest.main()
