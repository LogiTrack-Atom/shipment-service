"""Publica únicamente las cuatro imágenes aceptadas de la ampliación MQ, sin pisar P0."""
import argparse
import json
from pathlib import Path
import re
import subprocess
from publish_sprint04 import docker, image_id

SERVICES = {'shipment-service', 'courier-service', 'api-gateway', 'logitrack-frontend'}


def publish(manifest, tag, namespace):
    """Comprueba aceptación e identidades antes del primer push; permite retomar tags propios."""
    if not re.fullmatch(r'sprint04-mq-[a-z0-9_.-]+', tag) or not re.fullmatch(r'[a-z0-9][a-z0-9_-]*', namespace):
        raise ValueError('Usar namespace válido y tag nuevo sprint04-mq-*.')
    if set(manifest['images']) != SERVICES or set(manifest['image_ids']) != SERVICES:
        raise ValueError('El candidato MQ debe contener exactamente las cuatro imágenes modificadas.')
    if not all(manifest.get(key, {}).get('accepted') for key in ['backend_acceptance', 'ui_acceptance']):
        raise ValueError('Falta aceptación backend/UI de la ampliación.')
    for service, local in manifest['images'].items():
        if image_id(local) != manifest['image_ids'][service]:
            raise RuntimeError('La imagen cambió desde su aceptación: ' + service)
    existing = {}
    # Rechazar cualquier colisión antes de publicar un componente nuevo.
    for service in manifest['images']:
        reference = namespace + '/' + service + ':' + tag
        probe = subprocess.run(['docker', 'manifest', 'inspect', reference], capture_output=True, text=True)
        if probe.returncode == 0:
            docker('pull', reference)
            if image_id(reference) != manifest['image_ids'][service]:
                raise RuntimeError('Tag existente con otro contenido; no se sobrescribe: ' + service)
            digests = json.loads(docker('image', 'inspect', '--format', '{{json .RepoDigests}}', reference))
            existing[service] = next(value.split('@')[1] for value in digests if value.startswith(namespace + '/' + service + '@'))
        elif not any(text in probe.stderr.lower() for text in ['no such manifest', 'manifest unknown']):
            raise RuntimeError('No se pudo comprobar el tag remoto: ' + service)
    images = {}
    for service, local in manifest['images'].items():
        reference = namespace + '/' + service + ':' + tag
        if service in existing:
            digest = existing[service]
        else:
            docker('tag', local, reference)
            output = docker('push', reference)
            found = re.findall(r'digest: (sha256:[0-9a-f]{64})', output)
            if not found:
                raise RuntimeError('El registro no devolvió digest: ' + service)
            digest = found[-1]
        images[service] = reference + '@' + digest
        print(service + ': publicado y fijado por digest', flush=True)
    return {**manifest, 'version': tag, 'images': images, 'publication_complete': True,
            'release_accepted': False, 'registry': 'Docker Hub'}


def main():
    """Lee el candidato y guarda referencias inmutables; no accede a credenciales Docker."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--namespace', default='maxdz10')
    parser.add_argument('--tag', required=True)
    args = parser.parse_args()
    report = publish(json.loads(args.candidate.read_text()), args.tag, args.namespace)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')


if __name__ == '__main__':
    main()
