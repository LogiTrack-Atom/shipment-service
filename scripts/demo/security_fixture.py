"""Exporta credenciales de prueba vencidas y un envío CANCELADO para Postman."""
import argparse
import base64
import hashlib
import json
import subprocess
import tempfile
import time
from pathlib import Path
from manage import locked_directory
from runtime import DemoError, ROOT, write_json
from runtime_sprint03 import Sprint03Runtime
from seed import Seed


def encoded(value: bytes) -> str:
    """Codifica un segmento JWT sin padding."""
    return base64.urlsafe_b64encode(value).decode().rstrip('=')


def expired_token(issued: str, private: Path, public: Path) -> str:
    """Conserva claims reales y firma una variante que solo cambia iat/exp al pasado."""
    header, payload, _ = issued.split('.')
    header = json.loads(base64.urlsafe_b64decode(header + '=' * (-len(header) % 4)))
    payload = json.loads(base64.urlsafe_b64decode(payload + '=' * (-len(payload) % 4)))
    if header.get('alg') != 'RS256':
        raise ValueError('El fixture requiere una credencial RS256 real.')
    payload['exp'] = int(time.time()) - 120
    payload['iat'] = payload['exp'] - 900
    message = (encoded(json.dumps(header, separators=(',', ':')).encode()) + '.'
               + encoded(json.dumps(payload, separators=(',', ':')).encode())).encode()
    signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(private)],
                               input=message, capture_output=True, check=True).stdout
    with tempfile.NamedTemporaryFile(dir=private.parent) as file:
        file.write(signature)
        file.flush()
        verification = subprocess.run(['openssl', 'dgst', '-sha256', '-verify', str(public),
                                       '-signature', file.name], input=message, capture_output=True)
    if verification.returncode:
        raise DemoError('El fixture vencido no coincide con la clave pública de la demo.')
    return message.decode() + '.' + encoded(signature)


def export(directory: Path) -> dict:
    """Solo opera sobre una demo propia y mantiene tokens en su entorno privado."""
    with locked_directory(directory) as directory:
        runtime = Sprint03Runtime(directory)
        runtime.guard_resources()
        if runtime.state['phase'] != 'ready':
            raise DemoError('Primero preparar la demo Sprint03.')
        if hashlib.sha256((directory / 'public.pem').read_bytes()).hexdigest() != runtime.state['key_fingerprint']:
            raise DemoError('La clave pública no coincide con la demo administrada.')
        loader = Seed(runtime)
        loader.login('admin', 'ADMIN')
        admin = loader.tokens['admin']
        _, rows = loader.api.request('shipment', 'GET', '/api/v1/envios', token=admin)
        street = loader.shipment_street('rechazo-no-asignable')
        matching = [row for row in rows if row['origen']['calle'] == street]
        if len(matching) > 1:
            raise DemoError('El recurso de prueba está duplicado; restablecer la demo propia.')
        if matching:
            fixture = matching[0]
        else:
            _, fixture = loader.api.request('shipment', 'POST', '/api/v1/envios', token=admin,
                                            body=loader.shipment_body('rechazo-no-asignable', 'cliente'), expected=(201,))
            _, fixture = loader.api.request('shipment', 'PATCH', '/api/v1/envios/' + fixture['id'] + '/cancelacion', token=admin)
        if fixture['estado'] != 'CANCELADO':
            raise DemoError('El envío auxiliar cambió; restablecer la demo propia.')
        token = expired_token(admin, directory / 'private.pem', directory / 'public.pem')
        environment = json.loads((directory / 'postman_environment.json').read_text())
        environment['name'] += ' — seguridad controlada'
        environment['values'] = [row for row in environment['values'] if row['key'] not in {'expiredToken', 'envio_no_asignableId'}]
        environment['values'] += [{'key': 'expiredToken', 'value': token, 'enabled': True, 'type': 'secret'},
                                  {'key': 'envio_no_asignableId', 'value': fixture['id'], 'enabled': True, 'type': 'default'}]
        write_json(directory / 'postman_security_environment.json', environment)
        return {'aprobado': True, 'firma_rs256_verificada': True, 'ttl_segundos': 900,
                'vencimiento_supera_tolerancia': True, 'envio_auxiliar': 'CANCELADO',
                'alcance': 'fixture privado; la clave permanece fuera de Postman y Git'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state-dir', type=Path, default=ROOT / '.logitrack-demo/sprint03')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/sprint03-security-fixture.json')
    args = parser.parse_args()
    try:
        result = export(args.state_dir)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except (DemoError, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(str(error) if isinstance(error, DemoError) else 'No se pudo preparar el fixture privado de seguridad.')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
