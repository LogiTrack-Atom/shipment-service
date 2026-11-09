#!/usr/bin/env python3
"""Reconcilia evidencias de inventario con los snapshots Git indicados."""

import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path
import subprocess


def read_csv(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream))


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repos', type=Path, required=True)
    parser.add_argument('--datos', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((args.datos / 'manifiesto.json').read_text())
    inventory = read_csv(args.datos / 'inventario.csv')
    matrix = read_csv(args.datos / 'matriz-revision.csv')
    docs = read_csv(args.datos / 'javadoc-tipos.csv')
    key = lambda row: (row['repo'], row['sha'], row['archivo'])
    keys = {key(row) for row in inventory}
    require(len(keys) == len(inventory) == manifest['totales']['archivos'], 'Total o duplicados inválidos')
    require(keys == {key(row) for row in matrix} and len(matrix) == len(inventory), 'Matriz incompleta')
    require(all(row['estado'] == 'pendiente' and row['motivo'] for row in matrix), 'Estado inicial inválido')
    require(sum(int(row['bytes']) for row in inventory) == manifest['totales']['bytes'], 'Bytes inválidos')
    for repo in manifest['repositorios']:
        name, sha = repo['repo'], repo['head']
        entries = subprocess.check_output(['git', '-C', str(args.repos / name),
                                          'ls-tree', '-r', '-z', '-l', sha]).split(b'\0')
        actual = {}
        for entry in filter(None, entries):
            meta, path = entry.split(b'\t', 1)
            mode, kind, blob, size = meta.decode().split()
            require(kind == 'blob', 'Tipo Git inesperado')
            actual[path.decode()] = (mode, blob, int(size))
        rows = [row for row in inventory if row['repo'] == name]
        require(len(rows) == repo['archivos'] == len(actual), name + ': conteos diferentes')
        require({row['archivo']: (row['modo'], row['blob'], int(row['bytes'])) for row in rows} == actual,
                name + ': snapshot distinto')
        require(dict(collections.Counter(row['extension'] for row in rows)) == repo['por_extension'],
                name + ': extensiones diferentes')
        require(dict(collections.Counter(row['categoria'] for row in rows)) == repo['por_categoria'],
                name + ': categorías diferentes')
    sources = {key(row) for row in inventory if row['archivo'].endswith('.java')}
    require(sources == {key(row) for row in docs}, 'Cobertura de fuentes Java incompleta')
    require(len(sources) == manifest['totales']['fuentes_java'], 'Conteo Java incorrecto')
    require(len(docs) == manifest['totales']['declaraciones_java'], 'Conteo de tipos incorrecto')
    require(sum(row['javadoc_presente'] == 'true' for row in docs) ==
            manifest['totales']['declaraciones_con_javadoc'], 'Conteo JavaDoc incorrecto')
    for filename, expected in manifest['archivos_generados_sha256'].items():
        require(hashlib.sha256((args.datos / filename).read_bytes()).hexdigest() == expected,
                filename + ': huella diferente')
    print(f'OK: {len(inventory)} archivos, {len(sources)} fuentes Java, {len(docs)} tipos; matriz y huellas coherentes.')


if __name__ == '__main__':
    main()
