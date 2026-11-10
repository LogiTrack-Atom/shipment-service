"""Publica imágenes verificadas con una etiqueta nueva y registra sus digests."""
import argparse
import json
import re
import subprocess
from pathlib import Path


def docker(*args):
    """No imprime credenciales, configuración Docker ni variables del proceso."""
    result = subprocess.run(['docker', *args], capture_output=True, text=True)
    if result.returncode:
        denied = any(word in result.stderr.lower() for word in ['denied', 'unauthorized', 'insufficient_scope'])
        reason = 'autorización de registro insuficiente' if denied else 'fallo del comando Docker'
        raise RuntimeError('Docker no pudo completar ' + args[0] + ': ' + reason + '.')
    return result.stdout


def image_id(reference):
    """Identifica el contenido local que se publicará."""
    return docker('image', 'inspect', '--format', '{{.Id}}', reference).strip()


def publish(manifest, tag, namespace):
    """Rechaza sobrescrituras de contenido distinto y permite retomar un push propio."""
    if not re.fullmatch('sprint04-[a-z0-9_.-]+', tag) or not re.fullmatch('[a-z0-9][a-z0-9_-]*', namespace):
        raise ValueError('Usar una etiqueta explícita de Sprint04 y un namespace Docker Hub válido.')
    if set(manifest['images']) != {'security-service', 'api-gateway', 'logitrack-frontend'}:
        raise ValueError('Publicar únicamente las tres imágenes modificadas del núcleo Sprint04.')
    if not manifest.get('core_acceptance', {}).get('accepted'):
        raise ValueError('Falta aceptación del núcleo.')
    images = {}
    for service, local in manifest['images'].items():
        reference = namespace + '/' + service + ':' + tag
        expected = manifest['image_ids'][service]
        if image_id(local) != expected:
            raise RuntimeError('La imagen local cambió desde su verificación: ' + service)
        probe = subprocess.run(['docker', 'manifest', 'inspect', reference], capture_output=True, text=True)
        if probe.returncode == 0:
            docker('pull', reference)
            if image_id(reference) != expected:
                raise RuntimeError('La etiqueta ya contiene otra imagen; no se sobrescribe: ' + service)
            digests = json.loads(docker('image', 'inspect', '--format', '{{json .RepoDigests}}', reference))
            digest = next(value.split('@')[1] for value in digests if value.startswith(namespace + '/' + service + '@'))
        else:
            if not any(message in probe.stderr.lower() for message in ['no such manifest', 'manifest unknown']):
                raise RuntimeError('No se pudo comprobar si la etiqueta existe: ' + service)
            docker('tag', local, reference)
            output = docker('push', reference)
            found = re.findall(r'digest: (sha256:[0-9a-f]{64})', output)
            if not found:
                raise RuntimeError('Docker no devolvió el digest publicado: ' + service)
            digest = found[-1]
        images[service] = reference + '@' + digest
        print(service + ': publicado y fijado por digest', flush=True)
    return {**manifest, 'version': tag, 'images': images, 'registry': 'Docker Hub', 'publication_complete': True, 'g1_approved': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--namespace', default='maxdz10')
    args = parser.parse_args()
    report = publish(json.loads(args.candidate.read_text()), args.tag, args.namespace)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    main()
