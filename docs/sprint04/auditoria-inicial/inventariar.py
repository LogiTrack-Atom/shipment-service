#!/usr/bin/env python3
"""Genera metadatos de siete snapshots Git; no publica contenidos de archivos."""

import argparse
import collections
import csv
import datetime
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

REPOS = ('shipment-service', 'courier-service', 'security-service', 'api-gateway',
         'eureka-server', 'configserver', 'configserver-ms')


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args])


def write_csv(path, rows, columns):
    with path.open('w', newline='', encoding='utf-8') as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)


def category(path):
    if path.startswith('src/main/java/'):
        return 'java-produccion'
    if path.startswith('src/test/'):
        return 'pruebas'
    if path.startswith('.github/'):
        return 'ci'
    if path.startswith('.mvn/') or path in ('mvnw', 'mvnw.cmd', 'pom.xml'):
        return 'build'
    if path.endswith('.sql'):
        return 'sql'
    if path.endswith(('.yml', '.yaml', '.properties')) or 'Dockerfile' in path:
        return 'configuracion-infra'
    if path.startswith(('scripts/', 'tools/')) or path.endswith(('.py', '.sh', '.ps1')):
        return 'herramientas'
    if path.startswith('docs/') or path.endswith(('.md', '.pdf', '.json', '.puml')):
        return 'documentacion-contratos'
    return 'otros'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repos', type=Path, required=True)
    parser.add_argument('--remote-refs', type=Path, required=True,
                        help='JSON capturado con git ls-remote --heads --tags origin')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    remote_refs = json.loads(args.remote_refs.read_text())
    manifest = {'capturado_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'issue': 'https://github.com/LogiTrack-Atom/shipment-service/issues/66',
                'alcance': 'Archivos versionados en HEAD; todos pendientes de auditoría contextual.',
                'repositorios': []}
    inventory, matrix, sources = [], [], []
    before = {}
    for name in REPOS:
        repo = (args.repos / name).resolve()
        head = git(repo, 'rev-parse', 'HEAD').decode().strip()
        status = git(repo, 'status', '--porcelain=v1', '-uno').decode()
        if status:
            raise RuntimeError(f'{name}: cambios versionados; congelar antes de inventariar')
        before[name] = (head, status)
        refs = {ref: sha for sha, ref in (line.split(' ', 1) for line in
                git(repo, 'show-ref', '--heads', '--tags').decode().splitlines())}
        if remote_refs[name].get('refs/heads/develop') != head:
            raise RuntimeError(f'{name}: HEAD no coincide con develop remoto capturado')
        remote = git(repo, 'remote', 'get-url', 'origin').decode().strip()
        if remote not in (f'https://github.com/LogiTrack-Atom/{name}.git',
                          f'git@github.com:LogiTrack-Atom/{name}.git'):
            raise RuntimeError(f'{name}: remoto inesperado; revisar sin publicar credenciales')
        entries = []
        for entry in git(repo, 'ls-tree', '-r', '-z', '-l', head).split(b'\0'):
            if not entry:
                continue
            metadata, path = entry.split(b'\t', 1)
            mode, kind, oid, size = metadata.decode().split()
            path = path.decode('utf-8')
            if kind != 'blob':
                raise RuntimeError('Submódulo requiere inventario explícito')
            suffix = Path(path).suffix.lower() or '(sin extension)'
            row = dict(repo=name, sha=head, archivo=path, modo=mode, blob=oid,
                       bytes=int(size), extension=suffix, categoria=category(path))
            entries.append(row)
            inventory.append(row)
            matrix.append(dict(repo=name, sha=head, archivo=path, tipo=category(path),
                               estado='pendiente', motivo='Inventariado; requiere revisión contextual #68',
                               uso='pendiente', documentacion='pendiente', configuracion='pendiente',
                               arquitectura='pendiente', seguridad='pendiente', datos_pruebas='pendiente',
                               evidencia='', hallazgos=''))
            if path.endswith('.java'):
                sources.append(str(repo / path))
        pom = None
        if (repo / 'pom.xml').exists():
            root = ET.fromstring(git(repo, 'show', f'{head}:pom.xml'))
            ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
            pom = {key: root.findtext(value, namespaces=ns) for key, value in {
                'java': 'm:properties/m:java.version',
                'spring_boot_parent': 'm:parent/m:version',
                'spring_cloud_bom': 'm:properties/m:spring-cloud.version',
                'version_aplicacion': 'm:version'}.items()}
        manifest['repositorios'].append(dict(repo=name, head=head,
                rama=git(repo, 'branch', '--show-current').decode().strip(),
                origen_publico=remote, cambios_versionados=False,
                no_versionados='Fuera del alcance; no se listan rutas o valores privados.',
                refs_locales=refs, refs_remotas=remote_refs[name],
                develop_remoto_coincide=True,
                archivos=len(entries), bytes=sum(e['bytes'] for e in entries),
                por_extension=dict(sorted(collections.Counter(e['extension'] for e in entries).items())),
                por_categoria=dict(sorted(collections.Counter(e['categoria'] for e in entries).items())),
                versiones_declaradas=pom))
    write_csv(output / 'inventario.csv', inventory, inventory[0].keys())
    write_csv(output / 'matriz-revision.csv', matrix, matrix[0].keys())
    # El parser del JDK distingue tipos, records, interfaces y tipos anidados.
    with tempfile.TemporaryDirectory(prefix='s04-docs-') as directory:
        paths = Path(directory) / 'fuentes.txt'
        paths.write_text('\n'.join(sources) + '\n')
        result = subprocess.check_output(['java', str(Path(__file__).with_name('DocInventory.java')),
                                          str(paths)], text=True)
    docs = []
    for row in csv.DictReader(io.StringIO(result), delimiter='\t'):
        full = Path(row.pop('archivo'))
        relative = full.relative_to(args.repos.resolve())
        name, *parts = relative.parts
        docs.append(dict(repo=name, sha=before[name][0], archivo='/'.join(parts),
                         ambito='produccion' if '/src/main/' in str(full) else 'pruebas', **row,
                         calidad='pendiente de revisión contextual #68'))
    write_csv(output / 'javadoc-tipos.csv', docs, docs[0].keys())
    for name, previous in before.items():
        repo = args.repos / name
        assert (git(repo, 'rev-parse', 'HEAD').decode().strip(),
                git(repo, 'status', '--porcelain=v1', '-uno').decode()) == previous
    manifest['totales'] = dict(archivos=len(inventory), bytes=sum(r['bytes'] for r in inventory),
                               fuentes_java=len(sources), declaraciones_java=len(docs),
                               declaraciones_con_javadoc=sum(r['javadoc_presente'] == 'true' for r in docs),
                               revisados_contextualmente=0, excluidos_versionados=0)
    manifest['archivos_generados_sha256'] = {file.name: hashlib.sha256(file.read_bytes()).hexdigest()
                                            for file in sorted(output.glob('*.csv'))}
    (output / 'manifiesto.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(manifest['totales'], ensure_ascii=False))


if __name__ == '__main__':
    main()
