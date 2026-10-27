# Evidencia de integración de migración — 27/10/2026

## Resultado actual

**19 pruebas integradas aprobadas en 54.693 segundos**, con el SQL real y la
implementación JPA de courier. El ensayo inicial de 18 pruebas contra el contrato
se conserva como antecedente en el historial del PR; esta verificación añade el
proveedor implementado y dos arranques reales sobre el lote importado.

Proveedor: [courier-service@ed47e89](https://github.com/LogiTrack-Atom/courier-service/commit/ed47e891f262f74ff7aa7f684842b80c409f39e6),
integrado mediante [PR #7](https://github.com/LogiTrack-Atom/courier-service/pull/7)
en develop `3d1350ded441345f8a7df1dae1f3004823d91350`. Su CI aprobó 13 pruebas unitarias, 7 de
persistencia y Javadoc estricto sin advertencias. No se presenta la suma de ambas
suites como una sola suite ni se reutiliza su evidencia como una ejecución nueva.

## Entorno y datos

Dos MySQL 8.4.11 descartables, sin volúmenes del equipo. El destino se publica solo
en loopback con un puerto efímero para la prueba Java. Origen con usuario SELECT,
importador con permisos mínimos y lector JPA adicional de solo lectura. Contraseñas
aleatorias y artefactos privados temporales; no hay credenciales en Git o argumentos.

Dataset sintético: 2 clientes, 3 repartidores, 5 envíos y 5 filas de historial;
3 asignaciones y 2 envíos sin repartidor, Unicode/comillas y microsegundos.
La instantánea SQL de courier coincide byte a byte con el checkout y su SHA-256;
la suite verifica también la revisión Git fijada antes de operar.

## Verificaciones

- Backup SQL restaurable; las cuatro tablas de origen coinciden íntegramente.
- Exportación e importación exactas de UUID BINARY(16), texto, estado y DATETIME(6).
- El courier real lee los tres registros del lote con JPA, inicia/cierra la aplicación
  dos veces y mantiene id/datos/fechas y usuario_id NULL; origen y destino sin cambios
  por la lectura. Se exige la señal `MIGRATION_JPA_VERIFIED=3;RESTARTS=2`.
- Reintentos, doble rollback/reaplicación, filas preexistentes y sus asociaciones conservados.
- Conflicto de UUID, datos inválidos/huérfanos, auditoría inversa, esquema incompatible,
  artefactos alterados u origen modificado rechazados sin sobrescribir.
- Error SQL a mitad del lote sin filas ni registro parcial; reversión rechazada si hubo
  ediciones, vínculos nuevos o registro de lote adulterado.
- Origen vacío, límites de contenedor/base/usuario, archivos privados y permisos reales probados.

El workflow de migración obtiene courier por ese commit inmutable, compila su utilidad
de prueba, verifica la procedencia y ejecuta las 19 pruebas. El resultado de CI del
head final se registra en el PR; una ejecución local no se presenta como CI.

## Archivos ensayados

| Archivo | SHA-256 |
| --- | --- |
| `docker/mysql/init/001-schema.sql` | `322eb45e27330386f0ddd3600cf537ad5de68b6deb5dd35db6b80db1098af33b` |
| `docker/mysql/migrations/001-migration-ledger.sql` | `669771fc9dce4d916fd854fc4c6aa94b12e3a38830ae5b412b9635beaaa569c6` |
| `scripts/migracion/repartidores.py` | `7724fb7b3665ea5ef3f4d1a294cdb18939cf7bb89d2c6af0e07ab905650a5bb9` |
| `scripts/migracion/tests/test_migracion.py` | `7b44a4a67a5e1944e69fa2fe62de56cd092b4f2622c57be5915663b163504446` |
| `scripts/migracion/tests/fixtures/courier-contract.sql` | `d5194952c2098cf8c27847a148725224e05f5ebf20ceb6e3c1affa807b8ddccb` |
| `scripts/migracion/tests/fixtures/courier-source.json` | `15c675e44376b2e470d48e58baff22b98946c2be4c9f9749e15358bac692c91e` |

El SQL inicial de shipment sigue idéntico a Sprint01
`899311e39ec8dd270f1e50d8289e35129b713799`; Java, pom, wrapper, Dockerfile y Compose
originales tampoco cambiaron. No se volvió a ejecutar ni se atribuye a este ensayo
la regresión Java del Sprint01. No se ejecutó un corte ni se retiró tabla/FK del equipo.

## Condición posterior

La preparación queda sujeta a la CI del PR y su integración en develop. El refactor
shipment-service#23 y el corte real continúan como tareas separadas: mantener backups,
acordar la ventana sin escritores y revisar el procedimiento de entrega. La prueba
sintética no autoriza a aplicar el CLI a bases del equipo ni a eliminar su volumen.
