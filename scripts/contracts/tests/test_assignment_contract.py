"""Valida los mensajes compartidos y rechazos de contrato antes de integrar AMQP."""
import copy
import json
from pathlib import Path
import unittest

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / 'docs/sprint04/asignacion-asincrona'


class AssignmentContractTest(unittest.TestCase):
    """Comprueba entradas de consumidores, API y estados usados por la interfaz."""

    def setUp(self):
        self.validators = {}
        self.examples = {}
        for name in ('request', 'command', 'result', 'operation'):
            schema = json.loads((CONTRACT / (name + '.schema.json')).read_text())
            Draft202012Validator.check_schema(schema)
            self.validators[name] = Draft202012Validator(
                schema, format_checker=FormatChecker(['uuid', 'date-time']))
            self.examples[name] = json.loads((CONTRACT / 'examples' / (name + '.json')).read_text())

    def rejects(self, name, field, value):
        payload = copy.deepcopy(self.examples[name])
        payload[field] = value
        self.assertFalse(self.validators[name].is_valid(payload), (name, field, value))

    def test_examples_are_valid(self):
        for name, example in self.examples.items():
            with self.subTest(name=name):
                self.validators[name].validate(example)

    def test_no_credentials_or_unknown_fields(self):
        for name in self.validators:
            with self.subTest(name=name):
                self.rejects(name, 'accessToken', 'must-never-be-in-message')

    def test_required_identity_cannot_be_omitted(self):
        for name in ('command', 'result'):
            for field in ('messageId', 'operationId', 'envioId', 'repartidorId'):
                payload = copy.deepcopy(self.examples[name])
                del payload[field]
                with self.subTest(name=name, field=field):
                    self.assertFalse(self.validators[name].is_valid(payload))

    def test_uuid_is_not_free_text(self):
        for name in self.validators:
            with self.subTest(name=name):
                self.rejects(name, 'repartidorId', 'someone@example.test')

    def test_generation_is_positive_integer_with_json_safe_bound(self):
        for value in (-1, 0, 1.5, True, '1', 9007199254740992):
            with self.subTest(value=value):
                self.rejects('command', 'assignmentVersion', value)

    def test_unknown_version_type_and_business_result_rejected(self):
        self.rejects('command', 'schemaVersion', 2)
        self.rejects('command', 'schemaVersion', True)
        self.rejects('command', 'type', 'assignment.availability.checked.v1')
        self.rejects('result', 'availability', 'RESERVED')

    def test_timestamps_are_valid_utc(self):
        for value in ('yesterday', '2026-02-30T00:00:00Z', '2026-11-10T12:00:00+03:00'):
            with self.subTest(value=value):
                self.rejects('command', 'expiresAt', value)

    def test_terminal_status_and_reason_are_consistent(self):
        for status, reason in [('APPLIED', None), ('REJECTED', 'INACTIVE'),
                               ('REJECTED', 'NOT_FOUND'), ('SUPERSEDED', 'REPLACED'),
                               ('SUPERSEDED', 'STATE_CHANGED'), ('EXPIRED', 'DEADLINE_EXCEEDED')]:
            payload = {**self.examples['operation'], 'status': status, 'reason': reason}
            with self.subTest(status=status, reason=reason):
                self.validators['operation'].validate(payload)
                payload['reason'] = 'INACTIVE' if status != 'REJECTED' else None
                self.assertFalse(self.validators['operation'].is_valid(payload))


if __name__ == '__main__':
    unittest.main()
