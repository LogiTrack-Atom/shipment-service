"""Impide publicar tags incorrectos, imágenes alteradas o ante errores de registro ambiguos."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('publish_sprint04', Path(__file__).parents[1] / 'publish_sprint04.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class PublicationSafetyTest(unittest.TestCase):
    def candidate(self):
        names = ['security-service', 'api-gateway', 'logitrack-frontend']
        return {'images': {name: name + ':local' for name in names}, 'image_ids': {name: 'verified-id' for name in names}, 'core_acceptance': {'accepted': True}}

    def test_wrong_sprint_never_contacts_docker(self):
        with patch.object(module, 'docker') as docker:
            with self.assertRaises(ValueError): module.publish(self.candidate(), 'sprint03-1.0.0', 'maxdz10')
            docker.assert_not_called()

    def test_missing_acceptance_never_contacts_docker(self):
        candidate = self.candidate(); candidate['core_acceptance']['accepted'] = False
        with patch.object(module, 'docker') as docker:
            with self.assertRaises(ValueError): module.publish(candidate, 'sprint04-rc1', 'maxdz10')
            docker.assert_not_called()

    def test_changed_local_image_never_probes_or_pushes(self):
        with patch.object(module, 'image_id', return_value='changed'), patch.object(module.subprocess, 'run') as run:
            with self.assertRaises(RuntimeError): module.publish(self.candidate(), 'sprint04-rc1', 'maxdz10')
            run.assert_not_called()

    def test_registry_auth_error_never_tags_or_pushes(self):
        result = module.subprocess.CompletedProcess([], 1, '', 'unauthorized')
        with patch.object(module, 'image_id', return_value='verified-id'), patch.object(module.subprocess, 'run', return_value=result), patch.object(module, 'docker') as docker:
            with self.assertRaises(RuntimeError): module.publish(self.candidate(), 'sprint04-rc1', 'maxdz10')
            docker.assert_not_called()

    def test_existing_tag_with_other_content_is_never_overwritten(self):
        result = module.subprocess.CompletedProcess([], 0, '{}', '')
        with patch.object(module, 'image_id', side_effect=['verified-id', 'different-id']), patch.object(module.subprocess, 'run', return_value=result), patch.object(module, 'docker') as docker:
            with self.assertRaises(RuntimeError): module.publish(self.candidate(), 'sprint04-rc1', 'maxdz10')
            self.assertEqual(docker.call_count, 1)
            self.assertEqual(docker.call_args.args[0], 'pull')

if __name__ == '__main__': unittest.main()
