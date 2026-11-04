"""Comprueba firma y límites del token vencido sin credenciales de la aplicación."""
import base64
import json
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from security_fixture import encoded, expired_token


class ExpiredTokenTest(unittest.TestCase):
    def test_preserves_claims_and_verifies_signature_of_expired_token(self):
        with tempfile.TemporaryDirectory() as directory:
            private, public = Path(directory) / 'private.pem', Path(directory) / 'public.pem'
            subprocess.run(['openssl', 'genpkey', '-quiet', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)], check=True)
            subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public)], check=True, capture_output=True)
            claims = {'sub': 'efbe86b0-e8b9-43d8-9af5-1a98140ecbe8', 'roles': ['ADMIN'],
                      'iss': 'logitrack-security', 'aud': ['logitrack-api'], 'iat': 1, 'exp': 901}
            issued = encoded(b'{"alg":"RS256"}') + '.' + encoded(json.dumps(claims).encode()) + '.fixture'
            token = expired_token(issued, private, public)
            segment = token.split('.')[1]
            actual = json.loads(base64.urlsafe_b64decode(segment + '=' * (-len(segment) % 4)))
            self.assertEqual(actual['sub'], claims['sub'])
            self.assertEqual(actual['roles'], ['ADMIN'])
            self.assertEqual(actual['exp'] - actual['iat'], 900)
            self.assertLess(actual['exp'], time.time() - 60)

    def test_rejects_another_algorithm_before_reading_keys(self):
        token = encoded(b'{"alg":"HS256"}') + '.' + encoded(b'{}') + '.fixture'
        with self.assertRaises(ValueError):
            expired_token(token, Path('absent-private'), Path('absent-public'))


if __name__ == '__main__':
    unittest.main()
