"""Verifica que la evidencia pública no contenga secretos de los reportes Newman."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from newman_summary import summarize


class SummaryTest(unittest.TestCase):
    def fixture(self):
        return {'collection': {'info': {'name': 'Colección de prueba'}}, 'run': {
            'stats': {'requests': {'total': 1, 'failed': 0}, 'assertions': {'total': 1, 'failed': 0}},
            'failures': [], 'executions': [{'item': {'name': 'Login'},
                'request': {'header': {'Authorization': 'SECRET_TOKEN'}, 'body': 'SECRET_PASSWORD'},
                'response': {'code': 200, 'stream': 'SECRET_RESPONSE'}}]}}

    def test_only_public_fields_are_serialized(self):
        report = summarize(self.fixture())
        self.assertTrue(report['aprobado'])
        self.assertNotIn('SECRET', json.dumps(report))
        self.assertEqual(report['ejecuciones'], [{'nombre': 'Login', 'http': 200}])

    def test_failures_remain_failed_without_copying_error_details(self):
        document = self.fixture()
        document['run']['failures'] = [{'error': {'message': 'SECRET_ERROR'}}]
        report = summarize(document)
        self.assertFalse(report['aprobado'])
        self.assertEqual(report['fallos'], 1)
        self.assertNotIn('SECRET_ERROR', json.dumps(report))


if __name__ == '__main__':
    unittest.main()
