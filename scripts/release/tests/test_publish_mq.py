"""Guardas de publicación: no publicar una aceptación parcial ni sobreescribir releases."""
import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
spec = importlib.util.spec_from_file_location('publish_mq', ROOT / 'publish_mq.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublicationGuards(unittest.TestCase):
    """Comprueba fallos previos a cualquier push, sin red ni Docker."""
    def candidate(self):
        return {'images': {s: s + ':local' for s in module.SERVICES},
                'image_ids': {s: 'sha256:expected' for s in module.SERVICES},
                'backend_acceptance': {'accepted': True}, 'ui_acceptance': {'accepted': True}}

    @patch.object(module, 'docker')
    def test_partial_acceptance_and_p0_tag_cannot_publish(self, docker):
        manifest = self.candidate()
        manifest['ui_acceptance']['accepted'] = False
        with self.assertRaises(ValueError): module.publish(manifest, 'sprint04-mq-rc1', 'maxdz10')
        with self.assertRaises(ValueError): module.publish(self.candidate(), 'sprint04-rc1', 'maxdz10')
        docker.assert_not_called()

    @patch.object(module, 'docker')
    @patch.object(module, 'image_id', return_value='sha256:changed')
    def test_changed_local_image_stops_before_remote_mutation(self, image_id, docker):
        with self.assertRaises(RuntimeError): module.publish(self.candidate(), 'sprint04-mq-rc1', 'maxdz10')
        docker.assert_not_called()

    @patch.object(module.subprocess, 'run', return_value=types.SimpleNamespace(returncode=0))
    @patch.object(module, 'docker', return_value='')
    @patch.object(module, 'image_id', side_effect=lambda ref: 'sha256:other' if ref.startswith('maxdz10/') else 'sha256:expected')
    def test_existing_different_tag_cannot_be_overwritten(self, image_id, docker, run):
        with self.assertRaises(RuntimeError): module.publish(self.candidate(), 'sprint04-mq-rc1', 'maxdz10')
        self.assertFalse(any(call.args[0] in ('tag', 'push') for call in docker.call_args_list))


if __name__ == '__main__': unittest.main()
