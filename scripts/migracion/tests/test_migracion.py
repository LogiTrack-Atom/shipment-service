"""Pruebas reales de migración en MySQL 8.4; nunca usan Compose ni volúmenes del equipo."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
import uuid

SCRIPT = Path(__file__).resolve().parents[1] / 'repartidores.py'
spec = importlib.util.spec_from_file_location('migracion', SCRIPT)
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)
REPO = SCRIPT.parents[2]
SOURCE_DB = 'shipment_migration_copy'
TARGET_DB = 'courier_migration_copy'


def execute(command, **kwargs):
    """Ejecuta sin imprimir credenciales o SQL al fallar."""
    result = subprocess.run(command, capture_output=True, **kwargs)
    if result.returncode:
        raise AssertionError('Falló una operación Docker de preparación/verificación del ensayo.')
    return result


class MigrationMySqlTest(unittest.TestCase):
    """Comprueba conservación, idempotencia, rechazo seguro y reversión en dos servidores."""

    @classmethod
    def setUpClass(cls):
        """Provisiona únicamente dos contenedores efímeros, sin puertos ni volúmenes."""
        frozen = Path(__file__).parent / 'fixtures/shipment-sprint01.sql'
        provenance = json.loads(frozen.with_suffix('.json').read_text())
        if hashlib.sha256(frozen.read_bytes()).hexdigest() != provenance['sha256']:
            raise AssertionError('La instantánea de origen no corresponde al Sprint01 registrado.')
        cls.owned = []
        cls.probe_classpath = os.environ.get('COURIER_PROBE_CLASSPATH')
        cls.probe_password = uuid.uuid4().hex
        schema = Path(__file__).parent / 'fixtures/courier-contract.sql'
        origin = json.loads((schema.parent / 'courier-source.json').read_text())
        if hashlib.sha256(schema.read_bytes()).hexdigest() != origin['sha256']:
            raise AssertionError('La instantánea SQL no corresponde al proveedor registrado.')
        source_dir = os.environ.get('COURIER_SOURCE_DIR')
        if source_dir:
            source_dir = Path(source_dir).resolve()
            revision = execute(['git','-C',str(source_dir),'rev-parse','HEAD'],text=True).stdout.strip()
            if revision != origin['commit'] or (source_dir/origin['path']).read_bytes() != schema.read_bytes():
                raise AssertionError('El checkout real de courier difiere de la procedencia fijada.')
        cls.addClassCleanup(cls.cleanup)
        cls.environment_before = {key: os.environ.get(key) for key in ['MIGRATION_SOURCE_PASSWORD', 'MIGRATION_TARGET_PASSWORD']}
        os.environ['MIGRATION_SOURCE_PASSWORD'] = uuid.uuid4().hex
        os.environ['MIGRATION_TARGET_PASSWORD'] = uuid.uuid4().hex
        suffix = uuid.uuid4().hex[:10]
        cls.source_name, cls.target_name = 'migracion-source-' + suffix, 'migracion-target-' + suffix
        cls.root_passwords = {}
        for name, db, user, variable in [(cls.source_name, SOURCE_DB, 'migration_reader', 'MIGRATION_SOURCE_PASSWORD'),
                                         (cls.target_name, TARGET_DB, 'migration_writer', 'MIGRATION_TARGET_PASSWORD')]:
            cls.root_passwords[name] = uuid.uuid4().hex
            env = {**os.environ, 'MYSQL_ROOT_PASSWORD': cls.root_passwords[name], 'MYSQL_DATABASE': db,
                   'MYSQL_USER': user, 'MYSQL_PASSWORD': os.environ[variable]}
            cls.owned.append(name)
            publish = ['--publish','127.0.0.1::3306'] if name == cls.target_name and cls.probe_classpath else []
            execute(['docker', 'run', '--detach', '--name', name, *publish, '--label', migration.LABEL + '=true',
                     '--env', 'MYSQL_ROOT_PASSWORD', '--env', 'MYSQL_DATABASE', '--env', 'MYSQL_USER',
                     '--env', 'MYSQL_PASSWORD', 'mysql:8.4.11'], env=env)
        for name in cls.owned:
            deadline = time.monotonic() + 100
            while time.monotonic() < deadline:
                result = cls.root(name, 'SELECT 1;', check=False)
                if result.returncode == 0:
                    break
                time.sleep(1)
            else:
                raise AssertionError('MySQL de ensayo no inició.')
        cls.root(cls.source_name, 'USE ' + SOURCE_DB + ';\n' + (REPO / 'scripts/migracion/tests/fixtures/shipment-sprint01.sql').read_text())
        cls.root(cls.target_name, 'USE ' + TARGET_DB + ';\n' + (Path(__file__).parent / 'fixtures/courier-contract.sql').read_text()
                 + '\n' + (REPO / 'docker/mysql/migrations/001-migration-ledger.sql').read_text())
        cls.root(cls.source_name, "REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'migration_reader'@'%'; GRANT SELECT ON " + SOURCE_DB + ".* TO 'migration_reader'@'%';")
        cls.root(cls.target_name, "REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'migration_writer'@'%'; GRANT SELECT,INSERT,DELETE,CREATE TEMPORARY TABLES ON " + TARGET_DB + ".* TO 'migration_writer'@'%';")
        if cls.probe_classpath:
            cls.root(cls.target_name, "CREATE USER 'courier_probe'@'%' IDENTIFIED BY '" + cls.probe_password
                     + "'; GRANT SELECT ON " + TARGET_DB + ".* TO 'courier_probe'@'%';")
        cls.source = migration.Database(cls.source_name, SOURCE_DB, 'migration_reader', 'MIGRATION_SOURCE_PASSWORD')
        cls.target = migration.Database(cls.target_name, TARGET_DB, 'migration_writer', 'MIGRATION_TARGET_PASSWORD')

    @classmethod
    def root(cls, name, sql, check=True):
        """Limita DDL e inyección de fallos al administrador del contenedor propio."""
        env = {**os.environ, 'MYSQL_PWD': cls.root_passwords[name]}
        result = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', name, 'mysql', '-uroot',
                                 '--protocol=TCP', '--host=127.0.0.1', '--default-character-set=utf8mb4',
                                 '--batch', '--raw', '--skip-column-names'],
                                input=sql, text=True, capture_output=True, env=env)
        if check and result.returncode:
            raise AssertionError('Falló SQL de preparación de datos sintéticos.')
        return result

    @classmethod
    def cleanup(cls):
        """Retira solo los contenedores creados y restaura el entorno del proceso."""
        for name in reversed(cls.owned):
            subprocess.run(['docker', 'rm', '--force', '--volumes', name], capture_output=True)
        for key, value in cls.environment_before.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value

    def setUp(self):
        """Recupera el dataset sintético y un destino vacío antes de cada caso."""
        self.directory = tempfile.TemporaryDirectory(prefix='logitrack-migracion-')
        self.addCleanup(self.directory.cleanup)
        self.folder = Path(self.directory.name)
        self.root(self.target_name, 'USE ' + TARGET_DB + '; DROP TRIGGER IF EXISTS fallo_ensayo; DELETE FROM logitrack_migracion_repartidores; DELETE FROM repartidores;')
        self.root(self.source_name, 'USE ' + SOURCE_DB + '; DELETE FROM historial_estados_envio; DELETE FROM envios; DELETE FROM repartidores; DELETE FROM clientes;\n' + self.seed())
        self.before = migration.read_tables(self.source, migration.TABLES)

    @staticmethod
    def identifier(value):
        """Devuelve un UUID BINARY(16) determinista solo para datos sintéticos."""
        return "UNHEX('" + format(value, '032X') + "')"

    @classmethod
    def seed(cls):
        """Incluye Unicode, comillas, auditoría con microsegundos y asignaciones nulas/repetidas."""
        time1, time2 = "'2026-10-19 12:13:14.123456'", "'2026-10-20 13:14:15.654321'"
        sql = []
        for value in [1, 2]:
            sql.append('INSERT INTO clientes VALUES (' + ','.join([cls.identifier(value), time1, time2,
                migration.hex_literal('Cliente sintético ' + str(value)), migration.hex_literal('cliente' + str(value) + '@example.com'), 'NULL']) + ');')
        for value, name, vehicle, state in [(11, "O'Hara \\ Logística 🛵", 'Moto eléctrica', 'ACTIVO'),
                                            (12, 'Repartidor inactivo', 'Bicicleta', 'INACTIVO'),
                                            (13, 'Repartidor Álvarez', 'Camioneta', 'ACTIVO')]:
            sql.append('INSERT INTO repartidores VALUES (' + ','.join([cls.identifier(value), time1, time2,
                migration.hex_literal(name), migration.hex_literal(vehicle), migration.hex_literal(state)]) + ');')
        for value, courier, state in [(21, None, 'PENDIENTE'), (22, 11, 'PENDIENTE'),
                                      (23, 11, 'EN_RUTA'), (24, 12, 'ENTREGADO'), (25, None, 'CANCELADO')]:
            values = [cls.identifier(value), time1, time2, migration.hex_literal('LT-S01-' + str(value)),
                      cls.identifier(1 if value % 2 else 2), cls.identifier(courier) if courier else 'NULL',
                      '1.234', '10.25', '11.50', '12.75', migration.hex_literal(state)]
            values += [migration.hex_literal(v) for v in ['Calle Álamo', '123', 'San Salvador de Jujuy', '4600',
                                                          'Destino sintético', '456', 'Palpalá', '4612']]
            sql.append('INSERT INTO envios VALUES (' + ','.join(values) + ');')
            sql.append('INSERT INTO historial_estados_envio VALUES (' + ','.join([cls.identifier(value + 100), time1, time2,
                cls.identifier(value), 'NULL', migration.hex_literal(state), time2]) + ');')
        return '\n'.join(sql)

    def cli(self, operation, success=True, **changes):
        """Prueba el punto de entrada real y exige salida JSON sin credenciales."""
        options = {'source-container': self.source_name, 'source-db': SOURCE_DB,
                   'target-container': self.target_name, 'target-db': TARGET_DB, 'artifacts': str(self.folder), **changes}
        command = ['python3', str(SCRIPT), operation]
        for key, value in options.items():
            command += ['--' + key, value]
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        for secret in [*self.root_passwords.values(), os.environ['MIGRATION_SOURCE_PASSWORD'], os.environ['MIGRATION_TARGET_PASSWORD']]:
            self.assertNotIn(secret, result.stdout + result.stderr)
        return json.loads(result.stdout if success else result.stderr)

    def export_apply(self):
        """Prepara la exportación íntegra y aplica el lote con el usuario limitado."""
        self.cli('export')
        return self.cli('apply')

    def test_preserva_todos_los_datos_y_referencias(self):
        """Conserva cada byte de UUID/datos, auditoría y el origen completo."""
        exported = self.cli('export')
        self.assertEqual(exported['recuentos'], {'clientes': 2, 'repartidores': 3, 'envios': 5, 'historial_estados_envio': 5})
        self.assertEqual(exported['sin_repartidor'], 2)
        self.cli('apply')
        self.assertEqual(self.cli('verify')['asignaciones_verificadas'], 3)
        rows = migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})['repartidores']
        self.assertEqual(rows, [{**row, 'usuario_id': None} for row in self.before['repartidores']])
        self.assertEqual(self.before, migration.read_tables(self.source, migration.TABLES))
        self.assertEqual((self.folder / 'source.json').stat().st_mode & 0o777, 0o600)
        self.assertEqual((self.folder / 'source.sql').stat().st_mode & 0o777, 0o600)

    def test_repeticion_reversion_y_reaplicacion(self):
        """Reintentos no duplican; revertir dos veces y reaplicar conserva la auditoría."""
        self.export_apply()
        for _ in range(2):
            self.assertEqual(self.cli('apply')['filas_destino'], 3)
        self.cli('rollback')
        self.cli('rollback')
        self.assertEqual(migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})['repartidores'], [])
        self.assertEqual(self.cli('apply')['filas_destino'], 3)
        self.assertEqual(self.before, migration.read_tables(self.source, migration.TABLES))

    def test_conserva_filas_preexistentes_y_asociaciones(self):
        """Una fila coincidente y su usuario no son propiedad del lote ni se borran al revertir."""
        row = {**self.before['repartidores'][0], 'usuario_id': format(201, '032X')}
        columns = migration.TARGET_COLUMNS
        self.root(self.target_name, 'USE ' + TARGET_DB + '; INSERT INTO repartidores(' + ','.join(columns) + ') VALUES (' + ','.join(migration.literal(key, row[key]) for key in columns) + ');')
        self.export_apply()
        self.cli('apply')
        self.cli('rollback')
        self.assertEqual(migration.read_tables(self.target, {'repartidores': columns})['repartidores'], [row])

    def test_conflicto_uuid_no_sobrescribe_destino(self):
        """Una coincidencia de UUID con nombre diferente bloquea todo el lote."""
        self.cli('export')
        self.root(self.target_name, 'USE ' + TARGET_DB + '; INSERT INTO repartidores VALUES (' + self.identifier(11)
                  + ",'2026-10-19 12:13:14.123456','2026-10-20 13:14:15.654321','DISTINTO','Moto','ACTIVO',NULL);")
        before = migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})
        self.cli('apply', success=False)
        self.assertEqual(before, migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS}))

    def test_error_a_mitad_del_lote_revierte_filas_y_registro(self):
        """Una falla real del segundo INSERT no deja filas ni registro de migración parcial."""
        self.cli('export')
        self.root(self.target_name, 'USE ' + TARGET_DB + ';\nDELIMITER //\nCREATE TRIGGER fallo_ensayo BEFORE INSERT ON repartidores FOR EACH ROW BEGIN IF NEW.id=' + self.identifier(12) + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fallo de ensayo'; END IF; END;//\nDELIMITER ;\n")
        self.cli('apply', success=False)
        self.assertEqual(migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})['repartidores'], [])
        self.assertEqual(self.target.query('SELECT JSON_OBJECT(\'count\',COUNT(*)) FROM ' + migration.LEDGER)[0]['count'], 0)

    def test_origen_modificado_impide_importacion(self):
        """No importa una instantánea obsoleta ni cambia el destino."""
        self.cli('export')
        self.root(self.source_name, 'USE ' + SOURCE_DB + "; UPDATE repartidores SET nombre='Otro nombre' WHERE id=" + self.identifier(11) + ';')
        self.cli('apply', success=False)
        self.assertEqual(migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})['repartidores'], [])

    def test_reversion_rechaza_ediciones_y_vinculaciones(self):
        """La reversión nunca elimina una fila editada o asociada después del ensayo."""
        self.export_apply()
        self.root(self.target_name, 'USE ' + TARGET_DB + '; UPDATE repartidores SET usuario_id=' + self.identifier(201) + ' WHERE id=' + self.identifier(11) + ';')
        before = migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})
        self.cli('rollback', success=False)
        self.assertEqual(before, migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS}))

    def test_reversion_rechaza_registro_de_lote_adulterado(self):
        """El registro nunca habilita borrar un repartidor preexistente ajeno al lote."""
        self.export_apply()
        batch = migration.load_private(self.folder / 'source.json')['sha256']
        self.root(self.target_name, 'USE ' + TARGET_DB + '; INSERT INTO ' + migration.LEDGER + " VALUES ('" + batch + "'," + self.identifier(999) + ');')
        before = migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})
        self.cli('rollback', success=False)
        self.assertEqual(before, migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS}))

    def test_artefacto_alterado_impide_importacion(self):
        """Detecta una edición del export antes de ejecutar DML en courier."""
        self.cli('export')
        path = self.folder / 'source.json'
        data = json.loads(path.read_text()); data['payload']['tables']['repartidores'][0]['nombre'] = 'Adulterado'
        path.write_text(json.dumps(data))
        self.cli('apply', success=False)

    def test_origen_invalido_y_huerfanos_rechazados(self):
        """No corrige nombres vacíos, auditoría inversa ni referencias rotas silenciosamente."""
        cases = ["UPDATE repartidores SET nombre='   ' WHERE id=" + self.identifier(11),
                 "UPDATE repartidores SET updated_at='2020-01-01' WHERE id=" + self.identifier(11),
                 'SET FOREIGN_KEY_CHECKS=0; UPDATE envios SET repartidor_id=' + self.identifier(999) + ' WHERE id=' + self.identifier(22),
                 'SET FOREIGN_KEY_CHECKS=0; UPDATE historial_estados_envio SET envio_id=' + self.identifier(999) + ' WHERE id=' + self.identifier(121)]
        for sql in cases:
            with self.subTest(case=cases.index(sql)):
                self.root(self.source_name, 'USE ' + SOURCE_DB + '; DELETE FROM historial_estados_envio; DELETE FROM envios; DELETE FROM repartidores; DELETE FROM clientes;' + self.seed() + ';' + sql + ';')
                self.cli('export', success=False)
                self.assertFalse((self.folder / 'source.json').exists())

    def test_guards_contenerdor_base_usuario_y_permisos(self):
        """Rechaza bases reales, root, artefactos públicos o dentro del checkout."""
        self.cli('export', success=False, **{'source-db': 'shipment_db'})
        self.cli('export', success=False, **{'source-user': 'root'})
        self.cli('export', success=False, artifacts=str(REPO / 'tmp-migration-artifacts'))
        self.folder.chmod(0o755)
        self.cli('export', success=False)
        self.folder.chmod(0o700)
        self.assertFalse((REPO / 'tmp-migration-artifacts').exists())

    def test_usuarios_no_escriben_en_origen_ni_acceden_a_otra_base(self):
        """Los permisos de los usuarios de ensayo limitan el alcance efectivo del proceso."""
        with self.assertRaises(migration.MigrationError):
            self.source.query('DELETE FROM repartidores;')
        with self.assertRaises(migration.MigrationError):
            self.target.query('SELECT * FROM mysql.user;')

    def test_exportacion_no_sobrescribe_backup(self):
        """Un segundo export exige carpeta nueva y conserva el backup anterior."""
        self.cli('export')
        before = (self.folder / 'source.sql').read_bytes()
        self.cli('export', success=False)
        self.assertEqual(before, (self.folder / 'source.sql').read_bytes())

    def test_backup_alterado_impide_importacion(self):
        """El respaldo SQL también tiene integridad verificable antes de escribir."""
        self.cli('export')
        with (self.folder / 'source.sql').open('a') as stream:
            stream.write('-- alterado')
        self.cli('apply', success=False)

    def test_origen_vacio_migrable_y_reversible(self):
        """Una instalación sin datos conserva recuentos cero y no fabrica filas."""
        self.root(self.source_name, 'USE ' + SOURCE_DB + '; DELETE FROM historial_estados_envio; DELETE FROM envios; DELETE FROM repartidores; DELETE FROM clientes;')
        self.cli('export')
        self.assertEqual(self.cli('apply')['filas_destino'], 0)
        self.cli('verify')
        self.assertEqual(self.cli('rollback')['filas_destino'], 0)

    def test_esquema_destino_incompatible_rechazado(self):
        """Una auditoría que pierde microsegundos se rechaza antes de importar."""
        self.cli('export')
        self.root(self.target_name, 'USE ' + TARGET_DB + '; ALTER TABLE repartidores MODIFY updated_at DATETIME(3) NOT NULL;')
        try:
            self.cli('apply', success=False)
        finally:
            self.root(self.target_name, 'USE ' + TARGET_DB + '; ALTER TABLE repartidores MODIFY updated_at DATETIME(6) NOT NULL;')

    def test_contenedor_sin_etiqueta_y_servidor_compartido_rechazados(self):
        """La herramienta no actúa sobre un contenedor sin etiqueta ni dos DB del mismo servidor."""
        unused = 'migracion-unlabelled-' + uuid.uuid4().hex[:10]
        execute(['docker', 'create', '--name', unused, 'mysql:8.4.11'])
        self.owned.append(unused)
        self.cli('export', success=False, **{'source-container': unused})
        self.cli('export')
        previous = os.environ['MIGRATION_TARGET_PASSWORD']
        os.environ['MIGRATION_TARGET_PASSWORD'] = os.environ['MIGRATION_SOURCE_PASSWORD']
        try:
            self.cli('apply', success=False, **{'target-container': self.source_name,
                'target-db': SOURCE_DB, 'target-user': 'migration_reader'})
        finally:
            os.environ['MIGRATION_TARGET_PASSWORD'] = previous

    def test_backup_restaurable_en_base_descartable(self):
        """Restaura el dump completo en otra copia y compara las cuatro tablas byte a byte."""
        self.cli('export')
        restored_db = 'restored_migration_copy'
        self.root(self.source_name, 'DROP DATABASE IF EXISTS ' + restored_db + '; CREATE DATABASE ' + restored_db
                  + '; USE ' + restored_db + ';\n' + (self.folder / 'source.sql').read_text()
                  + "; GRANT SELECT ON " + restored_db + ".* TO 'migration_reader'@'%';")
        restored = migration.Database(self.source_name, restored_db, 'migration_reader', 'MIGRATION_SOURCE_PASSWORD')
        self.assertEqual(self.before, migration.read_tables(restored, migration.TABLES))
        self.root(self.source_name, 'DROP DATABASE ' + restored_db + ';')


def probe_importacion_jpa(self):
    """El courier real lee el lote del migrador sin regenerar UUID/fechas tras dos arranques."""
    self.export_apply()
    before = migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS})
    port = execute(['docker','port',self.target_name,'3306/tcp'],text=True).stdout.strip().rsplit(':',1)[1]
    env = {**os.environ, 'DB_URL':'jdbc:mysql://127.0.0.1:'+port+'/'+TARGET_DB+'?serverTimezone=UTC',
           'DB_USERNAME':'courier_probe','DB_PASSWORD':self.probe_password}
    result = execute(['java','-cp',self.probe_classpath,
        'com.logitrack_atom.courier_service.support.ImportedCourierProbe',str(self.folder/'source.json')],
        text=True,env=env)
    self.assertIn('MIGRATION_JPA_VERIFIED=3;RESTARTS=2',result.stdout)
    self.assertNotIn(self.probe_password,result.stdout+result.stderr)
    self.assertEqual(before,migration.read_tables(self.target, {'repartidores': migration.TARGET_COLUMNS}))
    self.assertEqual(self.before,migration.read_tables(self.source,migration.TABLES))


# La suite SQL se puede ejecutar sola. El modo integrado exige el classpath real
# y agrega una prueba efectiva, sin marcar como aprobada una prueba omitida.
if os.environ.get('COURIER_PROBE_CLASSPATH'):
    MigrationMySqlTest.test_lote_importado_leido_por_jpa_tras_reiniciar = probe_importacion_jpa


if __name__ == '__main__':
    unittest.main(verbosity=2)
