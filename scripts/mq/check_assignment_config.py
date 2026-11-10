"""Valida el modelo Compose con valores ficticios; no arranca servicios ni usa credenciales reales."""
import json
import os
from pathlib import Path
import subprocess


def main():
    """Comprueba separación de credenciales y ausencia de dependencia de arranque a RabbitMQ."""
    root = Path(__file__).resolve().parents[2]
    values = dict(line.split('=', 1) for line in (root / '.env.sprint04.example').read_text().splitlines()
                  if line and not line.startswith('#') and '=' in line)
    for key in list(values):
        if 'PASSWORD' in key:
            values[key] = 'configuration-only'
    values.update(KEYS_GID='0', JWT_PUBLIC_KEY_FILE='/tmp/configuration-only-public.pem',
                  JWT_PRIVATE_KEY_FILE='/tmp/configuration-only-private.pem',
                  RABBITMQ_SECRETS_GID='0', RABBITMQ_DEFINITIONS_FILE='/tmp/configuration-only-definitions.json',
                  ASSIGNMENT_ENABLED='true', MQ_SHIPMENT_PASSWORD='configuration-only-shipment',
                  MQ_COURIER_PASSWORD='configuration-only-courier')
    environment = os.environ.copy()
    environment.update(values)
    result = subprocess.run(['docker', 'compose', '-f', 'compose.sprint04.yaml', '-f', 'compose.sprint04-mq.yaml',
                             '-f', 'compose.sprint04-assignment.yaml', '--profile', 'mq', 'config', '--format', 'json'],
                            cwd=root, env=environment, capture_output=True, text=True, check=True)
    services = json.loads(result.stdout)['services']
    for service, own, other in [('shipment-service', 'MQ_SHIPMENT_PASSWORD', 'MQ_COURIER_PASSWORD'),
                                 ('courier-service', 'MQ_COURIER_PASSWORD', 'MQ_SHIPMENT_PASSWORD')]:
        assert services[service]['environment'][own] == values[own]
        assert other not in services[service]['environment']
        assert str(services[service]['environment']['ASSIGNMENT_ENABLED']).lower() == 'true'
        assert 'rabbitmq' not in services[service].get('depends_on', {})
    print(json.dumps({'ok': True, 'credentials_separated': True, 'no_broker_start_dependency': True}))


if __name__ == '__main__':
    main()
