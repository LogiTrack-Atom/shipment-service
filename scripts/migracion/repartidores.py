#!/usr/bin/env python3
"""Ensayo offline de migración Sprint01 sobre dos MySQL descartables.

No forma parte del servicio ni modifica el esquema de origen. Los artefactos
contienen datos y se guardan fuera del repositorio con permisos restrictivos.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime

LABEL = 'com.logitrack.migration.rehearsal'
ROOT = Path(__file__).resolve().parents[2]
TABLES = {
    'clientes': ['id', 'created_at', 'updated_at', 'nombre', 'email', 'telefono'],
    'repartidores': ['id', 'created_at', 'updated_at', 'nombre', 'tipo_vehiculo', 'estado'],
    'envios': ['id', 'created_at', 'updated_at', 'codigo_seguimiento', 'cliente_id',
               'repartidor_id', 'peso_kg', 'ancho_cm', 'alto_cm', 'largo_cm', 'estado',
               'origen_calle', 'origen_numero', 'origen_ciudad', 'origen_codigo_postal',
               'destino_calle', 'destino_numero', 'destino_ciudad', 'destino_codigo_postal'],
    'historial_estados_envio': ['id', 'created_at', 'updated_at', 'envio_id',
                               'estado_anterior', 'estado_nuevo', 'fecha_cambio'],
}
TARGET_COLUMNS = TABLES['repartidores'] + ['usuario_id']
LEDGER = 'logitrack_migracion_repartidores'


class MigrationError(Exception):
    """Fallo controlado, comunicado sin SQL, credenciales ni registros."""


def canonical(value):
    """Serializa determinísticamente para detectar cambios de datos."""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')


def digest(value):
    """Calcula el SHA-256 de la representación canónica."""
    return hashlib.sha256(canonical(value)).hexdigest()


def private_directory(value):
    """Exige directorio externo, propio y privado; no lo reutiliza dentro de Git."""
    path = Path(value).expanduser().resolve()
    if path == ROOT or ROOT in path.parents:
        raise MigrationError('Los artefactos deben estar fuera del repositorio.')
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o077:
        raise MigrationError('El directorio de artefactos debe pertenecer al usuario y tener modo 0700.')
    return path


def write_private(path, value):
    """Crea un artefacto 0600, sin sobrescribir archivos o enlaces existentes."""
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as output:
        output.write(canonical(value) + b'\n')


def load_private(path):
    """Lee un artefacto propio 0600, rechazando enlaces y permisos amplios."""
    if path.is_symlink() or path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o077:
        raise MigrationError('El artefacto debe ser propio, sin enlaces y tener modo 0600.')
    return json.loads(path.read_text(encoding='utf-8'))


class Database:
    """Cliente MySQL por Docker exec, limitado a contenedores de ensayo etiquetados."""

    def __init__(self, container, database, user, password_variable):
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', container or ''):
            raise MigrationError('Nombre de contenedor inválido.')
        if not re.fullmatch(r'[a-z][a-z0-9_]*_migration_copy', database or ''):
            raise MigrationError('Solo se admiten bases terminadas en _migration_copy.')
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*', user or '') or user.lower() == 'root':
            raise MigrationError('Se requiere un usuario de migración sin root.')
        if not os.environ.get(password_variable):
            raise MigrationError('Falta una contraseña externa de migración.')
        result = subprocess.run(['docker', 'inspect', '--format', '{{json .Config.Labels}}', container],
                                capture_output=True, text=True)
        if result.returncode or (json.loads(result.stdout or '{}') or {}).get(LABEL) != 'true':
            raise MigrationError('El contenedor no está identificado como ensayo descartable.')
        self.container, self.database, self.user = container, database, user
        self.password_variable = password_variable
        identity = self.query("SELECT JSON_OBJECT('database',DATABASE(),'server',@@server_uuid)")[0]
        if identity['database'] != database:
            raise MigrationError('La base conectada no coincide con la seleccionada.')
        self.server = identity['server']

    def command(self, program):
        """Entrega la contraseña por entorno, nunca como argumento de proceso."""
        environment = {**os.environ, 'MYSQL_PWD': os.environ[self.password_variable]}
        return ['docker', 'exec', '--interactive', '--env', 'MYSQL_PWD', self.container,
                program, '--user=' + self.user, '--default-character-set=utf8mb4'], environment

    def query(self, sql):
        """Ejecuta un batch sin --force; al fallar, la desconexión revierte su transacción."""
        command, environment = self.command('mysql')
        result = subprocess.run(command + ['--batch', '--raw', '--skip-column-names', '--binary-mode',
                                           self.database], input="SET time_zone='+00:00';\n" + sql,
                                env=environment, text=True, capture_output=True)
        if result.returncode:
            raise MigrationError('MySQL rechazó la operación; no se publica el SQL ni su contenido.')
        return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]

    def backup(self, path):
        """Exporta una copia lógica consistente; no ejecuta SQL de restauración."""
        command, environment = self.command('mysqldump')
        command += ['--single-transaction', '--quick', '--hex-blob', '--no-tablespaces',
                    '--set-gtid-purged=OFF', '--skip-add-locks', '--skip-triggers', self.database]
        with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as output:
            result = subprocess.run(command, env=environment, stdout=output, stderr=subprocess.PIPE)
        if result.returncode:
            path.unlink()
            raise MigrationError('No se pudo completar el backup de ensayo.')


def projection(table, columns):
    """Conserva los 16 bytes UUID, microsegundos y decimales sin conversión flotante."""
    fields = []
    for column in columns:
        expression = column
        if column == 'id' or column.endswith('_id'):
            expression = 'HEX(' + column + ')'
        elif column.endswith('_at') or column == 'fecha_cambio':
            expression = "DATE_FORMAT(" + column + ",'%Y-%m-%d %H:%i:%s.%f')"
        elif column in ['peso_kg', 'ancho_cm', 'alto_cm', 'largo_cm']:
            expression = 'CAST(' + column + ' AS CHAR)'
        fields += ["'" + column + "'", expression]
    return "SELECT JSON_OBJECT('table','" + table + "','row',JSON_OBJECT(" + ','.join(fields) + ')) FROM ' + table + ' ORDER BY id;'


def read_tables(db, tables):
    """Lee todas las tablas bajo una misma instantánea InnoDB de solo lectura."""
    sql = 'SET TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;\n'
    sql += '\n'.join(projection(table, columns) for table, columns in tables.items()) + '\nCOMMIT;'
    result = {table: [] for table in tables}
    for item in db.query(sql):
        result[item['table']].append(item['row'])
    return result


def validate_source(tables):
    """Rechaza auditoría inválida, estado desconocido, datos vacíos y referencias huérfanas."""
    for name, rows in tables.items():
        ids = set()
        for row in rows:
            for key, value in row.items():
                if (key == 'id' or key.endswith('_id')) and value is not None:
                    if not re.fullmatch(r'[0-9A-F]{32}', value):
                        raise MigrationError('UUID de origen con representación inválida.')
            if row['id'] in ids:
                raise MigrationError('Identificador duplicado en el origen.')
            ids.add(row['id'])
            created, updated = row['created_at'], row['updated_at']
            if created is None or updated is None or datetime.fromisoformat(updated) < datetime.fromisoformat(created):
                raise MigrationError('Auditoría de origen incompleta o cronológicamente inválida.')
            if name == 'repartidores' and (row['estado'] not in ['ACTIVO', 'INACTIVO']
                    or not isinstance(row['nombre'], str) or not row['nombre'].strip()
                    or not isinstance(row['tipo_vehiculo'], str) or not row['tipo_vehiculo'].strip()):
                raise MigrationError('Datos de repartidor inválidos; no se corrigen silenciosamente.')
    clients = {row['id'] for row in tables['clientes']}
    couriers = {row['id'] for row in tables['repartidores']}
    shipments = {row['id'] for row in tables['envios']}
    if any(row['cliente_id'] not in clients or (row['repartidor_id'] is not None
            and row['repartidor_id'] not in couriers) for row in tables['envios']):
        raise MigrationError('El origen contiene envíos con referencias huérfanas.')
    if any(row['envio_id'] not in shipments for row in tables['historial_estados_envio']):
        raise MigrationError('El origen contiene historial huérfano.')


def schema_guard(db, target=False):
    """Exige InnoDB y UUID/auditoría compatibles; no adapta un esquema desconocido."""
    metadata = db.query("SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")
    required = {'repartidores', LEDGER} if target else set(TABLES)
    engines = {item['table']: item['engine'] for item in metadata}
    if not required <= engines.keys() or any(engines[name] != 'InnoDB' for name in required):
        raise MigrationError('Faltan tablas InnoDB del contrato de migración.')
    triggers = db.query("SELECT JSON_OBJECT('name',TRIGGER_NAME) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()")
    if triggers:
        raise MigrationError('El esquema tiene triggers; requiere revisar el procedimiento antes de migrar.')
    columns = db.query("SELECT JSON_OBJECT('name',COLUMN_NAME,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'key',COLUMN_KEY,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='repartidores'")
    expected = {'id': 'binary(16)', 'created_at': 'datetime(6)', 'updated_at': 'datetime(6)',
                'nombre': 'varchar(255)', 'tipo_vehiculo': 'varchar(255)', "estado": "enum('ACTIVO','INACTIVO')"}
    if target:
        expected['usuario_id'] = 'binary(16)'
    if {column['name']: column['type'] for column in columns} != expected:
        raise MigrationError('El esquema de repartidores difiere del contrato; no se modifica automáticamente.')
    indexes = db.query("SELECT JSON_OBJECT('name',INDEX_NAME,'column',COLUMN_NAME,'position',SEQ_IN_INDEX,'non_unique',NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='repartidores' ORDER BY INDEX_NAME,SEQ_IN_INDEX")
    unique_indexes = {}
    for item in indexes:
        if item['non_unique'] == 0:
            unique_indexes.setdefault(item['name'], []).append(item['column'])
    if unique_indexes.get('PRIMARY') != ['id'] or (target and ['usuario_id'] not in unique_indexes.values()):
        raise MigrationError('Las restricciones únicas no coinciden con los identificadores del contrato.')
    for column in columns:
        if column['extra'] or column['nullable'] != ('YES' if column['name'] == 'usuario_id' else 'NO'):
            raise MigrationError('Nulabilidad o generación de columnas incompatible.')
        if column['name'] == 'id' and column['key'] != 'PRI':
            raise MigrationError('Se requiere PK para preservar los UUID.')
        if column['name'] == 'usuario_id' and column['key'] != 'UNI':
            raise MigrationError('La asociación de usuario debe ser única cuando tiene valor.')


def hex_literal(value):
    """Codifica texto como hexadecimal UTF-8 para evitar escapes o interpolación SQL."""
    return "CONVERT(UNHEX('" + value.encode('utf-8').hex() + "') USING utf8mb4)"


def literal(column, value):
    """Representa valores del artefacto validado sin concatenar texto de usuario en SQL."""
    if value is None:
        return 'NULL'
    if column == 'id' or column.endswith('_id'):
        if not re.fullmatch(r'[0-9A-F]{32}', value):
            raise MigrationError('UUID del artefacto inválido.')
        return "UNHEX('" + value + "')"
    return hex_literal(value)


def same_row(row, alias=''):
    """Compara bytes de texto, UUID, nulos y fechas; evita igualdad por collation."""
    conditions = []
    for key, value in row.items():
        column = alias + key
        if key in ['nombre', 'tipo_vehiculo', 'estado']:
            column = 'CAST(' + column + ' AS BINARY)'
        conditions.append(column + ' <=> ' + literal(key, value))
    return '(' + ' AND '.join(conditions) + ')'


def assertion(condition):
    """Aborta todo el batch mediante un CHECK temporal si falla la precondición."""
    return 'INSERT INTO migracion_asercion(ok) SELECT IF((' + condition + '),1,0);'


def begin_sql():
    """Serializa ensayos de migración y conserva la atomicidad de filas y registro."""
    return ["CREATE TEMPORARY TABLE migracion_asercion(ok INT NOT NULL CHECK(ok=1));",
            "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; START TRANSACTION;",
            assertion("GET_LOCK(CONCAT(DATABASE(),':repartidores'),5)=1")]


def guard_rows(rows):
    """Comprueba la instantánea destino dentro de la transacción que va a escribir."""
    return [assertion('SELECT COUNT(*)=' + str(len(rows)) + ' FROM repartidores')] + [
        assertion('EXISTS(SELECT 1 FROM repartidores WHERE ' + same_row(row) + ')') for row in rows]


def apply(db, bundle, directory, reverse=False):
    """Importa sin sobrescribir; revierte solo filas insertadas y aún inalteradas."""
    batch = bundle['sha256']
    source_rows = bundle['payload']['tables']['repartidores']
    current = read_tables(db, {'repartidores': TARGET_COLUMNS})['repartidores']
    before_path = directory / 'target-before.json'
    if not before_path.exists():
        if reverse:
            raise MigrationError('Falta la instantánea previa del destino; no se revierte a ciegas.')
        if db.query("SELECT JSON_OBJECT('id',HEX(repartidor_id)) FROM " + LEDGER + " WHERE lote='" + batch + "'"):
            raise MigrationError('Hay un lote aplicado sin su instantánea previa local; requiere revisión.')
        write_private(before_path, {'batch': batch, 'rows': current})
    before = load_private(before_path)
    if before['batch'] != batch:
        raise MigrationError('La instantánea destino corresponde a otro lote.')
    baseline = {row['id']: row for row in before['rows']}
    wanted = {row['id']: row for row in current}
    added = []
    for row in source_rows:
        if row['id'] not in baseline:
            added.append({**row, 'usuario_id': None})
        elif any(baseline[row['id']][key] != value for key, value in row.items()):
            raise MigrationError('Un UUID existente tiene contenido diferente; no se sobrescribe.')
    expected = {**baseline, **{row['id']: row for row in added}}
    if wanted not in [baseline, expected]:
        raise MigrationError('El destino cambió respecto del ensayo; requiere revisión sin sobrescrituras.')
    ledger = db.query("SELECT JSON_OBJECT('id',HEX(repartidor_id)) FROM " + LEDGER + " WHERE lote='" + batch + "' ORDER BY repartidor_id")
    ledger_ids = {item['id'] for item in ledger}
    inserted_ids = {row['id'] for row in added}
    if ledger_ids != (inserted_ids if wanted == expected else set()):
        raise MigrationError('El registro del lote no coincide con las inserciones del ensayo.')
    statements = begin_sql() + guard_rows(current)
    statements.append(assertion("SELECT COUNT(*)=" + str(len(ledger_ids)) + " FROM " + LEDGER + " WHERE lote='" + batch + "'"))
    for identifier in ledger_ids:
        statements.append(assertion("EXISTS(SELECT 1 FROM " + LEDGER + " WHERE lote='" + batch + "' AND repartidor_id=" + literal('id', identifier) + ')'))
    if reverse:
        for row in added:
            identifier = literal('id', row['id'])
            statements.append(assertion("NOT EXISTS(SELECT 1 FROM repartidores WHERE id=" + identifier + ") OR (EXISTS(SELECT 1 FROM repartidores WHERE " + same_row(row) + ") AND EXISTS(SELECT 1 FROM " + LEDGER + " WHERE lote='" + batch + "' AND repartidor_id=" + identifier + '))'))
        statements += ["DELETE r FROM repartidores r JOIN " + LEDGER + " m ON r.id=m.repartidor_id WHERE m.lote='" + batch + "';",
                       "DELETE FROM " + LEDGER + " WHERE lote='" + batch + "';"]
    else:
        for row in source_rows:
            identifier = literal('id', row['id'])
            statements.append(assertion('NOT EXISTS(SELECT 1 FROM repartidores WHERE id=' + identifier + ') OR EXISTS(SELECT 1 FROM repartidores WHERE ' + same_row(row) + ')'))
            statements.append("INSERT INTO " + LEDGER + "(lote,repartidor_id) SELECT '" + batch + "'," + identifier + ' WHERE NOT EXISTS(SELECT 1 FROM repartidores WHERE id=' + identifier + ');')
            columns = TABLES['repartidores']
            statements.append('INSERT INTO repartidores(' + ','.join(columns) + ',usuario_id) SELECT ' + ','.join(literal(key, row[key]) for key in columns) + ',NULL WHERE NOT EXISTS(SELECT 1 FROM repartidores WHERE id=' + identifier + ');')
    statements += ["COMMIT;", "DO RELEASE_LOCK(CONCAT(DATABASE(),':repartidores'));"]
    db.query('\n'.join(statements))
    after = read_tables(db, {'repartidores': TARGET_COLUMNS})['repartidores']
    if {row['id']: row for row in after} != (baseline if reverse else expected):
        raise MigrationError('La verificación posterior no coincide; conservar las copias y revisar.')
    return {'filas_destino': len(after), 'filas_del_lote': len(added), 'sha256_destino': digest(after)}


def run(args):
    """Coordina exportación, importación, verificación o reversión del ensayo."""
    directory = private_directory(args.artifacts)
    source = Database(args.source_container, args.source_db, args.source_user, 'MIGRATION_SOURCE_PASSWORD')
    schema_guard(source)
    tables = read_tables(source, TABLES)
    validate_source(tables)
    path = directory / 'source.json'
    if args.operation == 'export':
        if path.exists() or (directory / 'source.sql').exists():
            raise MigrationError('La exportación ya existe; utilizar un directorio nuevo.')
        source.backup(directory / 'source.sql')
        if read_tables(source, TABLES) != tables:
            raise MigrationError('El origen cambió durante el backup; descartar el ensayo incompleto.')
        payload = {'format': 'logitrack-sprint01-v1', 'tables': tables}
        bundle = {'sha256': digest(payload), 'payload': payload,
                  'backup_sha256': hashlib.sha256((directory / 'source.sql').read_bytes()).hexdigest()}
        write_private(path, bundle)
        return {'lote': bundle['sha256'], 'recuentos': {name: len(rows) for name, rows in tables.items()},
                'sin_repartidor': sum(row['repartidor_id'] is None for row in tables['envios'])}
    bundle = load_private(path)
    if bundle['sha256'] != digest(bundle['payload']) or bundle['payload']['format'] != 'logitrack-sprint01-v1':
        raise MigrationError('El artefacto fue alterado o su versión no es compatible.')
    if bundle['backup_sha256'] != hashlib.sha256((directory / 'source.sql').read_bytes()).hexdigest():
        raise MigrationError('El backup SQL fue alterado; no se continúa sin un respaldo íntegro.')
    if tables != bundle['payload']['tables']:
        raise MigrationError('El origen cambió desde la exportación; no se aplica un lote obsoleto.')
    target = Database(args.target_container, args.target_db, args.target_user, 'MIGRATION_TARGET_PASSWORD')
    if source.server == target.server:
        raise MigrationError('El ensayo requiere instancias MySQL separadas.')
    schema_guard(target, target=True)
    if args.operation in ['apply', 'rollback']:
        result = apply(target, bundle, directory, reverse=args.operation == 'rollback')
    else:
        rows = read_tables(target, {'repartidores': TARGET_COLUMNS})['repartidores']
        by_id = {row['id']: row for row in rows}
        for row in tables['repartidores']:
            if row['id'] not in by_id or any(by_id[row['id']][key] != value for key, value in row.items()):
                raise MigrationError('El destino no conserva todos los datos de los repartidores.')
        result = {'filas_destino': len(rows), 'asignaciones_verificadas': sum(row['repartidor_id'] is not None for row in tables['envios'])}
    if read_tables(source, TABLES) != tables:
        raise MigrationError('El origen cambió durante la operación; conservar los respaldos y revisar.')
    return {'lote': bundle['sha256'], 'origen_sin_cambios': True, **result}


def main():
    """Expone un CLI con salida resumida; no imprime datos ni errores SQL internos."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['export', 'apply', 'verify', 'rollback'])
    for role in ['source', 'target']:
        parser.add_argument('--' + role + '-container', required=role == 'source')
        parser.add_argument('--' + role + '-db', required=role == 'source')
        parser.add_argument('--' + role + '-user', default='migration_reader' if role == 'source' else 'migration_writer')
    parser.add_argument('--artifacts', required=True)
    args = parser.parse_args()
    try:
        result = run(args)
        print(json.dumps({'operacion': args.operation, 'resultado': 'correcto', **result}, ensure_ascii=False))
        return 0
    except (MigrationError, OSError, ValueError, KeyError, TypeError):
        # Los diagnósticos controlados solo contienen textos propios, nunca SQL o datos.
        error = sys.exc_info()[1]
        message = str(error) if isinstance(error, MigrationError) else 'No se pudo completar el ensayo; revisar argumentos y artefactos privados.'
        print(json.dumps({'operacion': args.operation, 'resultado': 'rechazado', 'motivo': message}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
