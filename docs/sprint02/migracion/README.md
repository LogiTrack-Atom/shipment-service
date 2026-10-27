# Ensayo de migración de repartidores — Sprint 02

Este procedimiento corresponde a [shipment-service#22](https://github.com/LogiTrack-Atom/shipment-service/issues/22).
Prepara la separación de datos; **no cambia el modelo Java, la API, la tabla ni la FK
del Sprint01**. El esquema de origen sigue en `docker/mysql/init/001-schema.sql`.
No se usa `ddl-auto=create`, no se reinicializa el volumen original y no se ejecuta
la herramienta contra las bases del equipo.

## Estado y límite de integración

La herramienta y el ensayo usan el contrato acordado para courier: `BINARY(16)`,
`DATETIME(6)`, datos actuales y `usuario_id` nullable y único. El archivo
`scripts/migracion/tests/fixtures/courier-contract.sql` es **un fixture**, no la
implementación de courier. Para cerrar esta issue hay que repetir la comprobación
contra el SQL y la persistencia JPA entregados en
[courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1), registrar
su commit y actualizar la evidencia. Mientras ese proveedor no esté integrado, el
PR permanece en borrador y la issue En curso.

Las pruebas publicadas utilizan datos sintéticos y el esquema original de Sprint01.
No representan una migración de datos reales del equipo.

## Qué se conserva

| Dato | Tratamiento |
| --- | --- |
| UUID | `HEX`/`UNHEX` conservan los 16 bytes; no usar UUID nuevos ni `UUID_TO_BIN(...,1)` |
| Nombre, vehículo y estado | Copia exacta; no normalizar, activar ni actualizar silenciosamente |
| created_at / updated_at | Conserva UTC y seis decimales; la importación SQL no dispara auditoría JPA |
| usuario_id | NULL para filas nuevas; no se inventa un usuario desde el UUID del repartidor |
| Fila ya existente idéntica | Se conserva; también conserva su asociación de usuario |
| UUID existente con otros datos | Rechazo íntegro, sin sobrescribir |
| Envíos y asignaciones | Se conservan en shipment, incluidos repartidor NULL y asignaciones repetidas |
| Clientes, tracking, medidas e historial | Se incluyen en la instantánea y el backup; deben permanecer iguales |

La exportación rechaza nombres/vehículos vacíos, estados desconocidos, auditoría
inválida y referencias huérfanas de envíos o historial. No descarta ni corrige esos
registros. Se revisan en una copia y se prepara un lote nuevo después de resolverlos.
Un repartidor INACTIVO conserva su estado y las referencias de sus envíos históricos.

## Requisitos del ensayo manual

- Python 3.10 o posterior, Docker y clientes `mysql`/`mysqldump` de MySQL 8.4 dentro
  de los contenedores. No se requiere un paquete Python ni instalar MySQL en el host.
- Dos instancias MySQL **descartables**, sin los volúmenes del equipo. Los dos
  contenedores deben tener la etiqueta `com.logitrack.migration.rehearsal=true`.
- Bases con nombres terminados en `_migration_copy`, por ejemplo
  `shipment_migration_copy` y `courier_migration_copy`. El CLI rechaza nombres como
  `shipment_db`, contenedores sin etiqueta, root y dos bases en un mismo servidor.
- Origen restaurado desde una copia autorizada y completa de Sprint01. Conservar
  el respaldo original fuera de Git. Para las pruebas automáticas se crea un
  dataset sintético; no se conecta al entorno del equipo.
- Destino con el esquema de courier y el registro operativo de
  `docker/mysql/migrations/001-migration-ledger.sql`. Este SQL se aplica únicamente
  sobre la copia destino, separado del init de shipment. No forma una relación JPA
  ni una FK entre servicios.
- No puede haber aplicaciones, DDL ni escritores concurrentes durante el ensayo.
  La etiqueta indica una copia de ensayo; no reemplaza detener los escritores.
- Un administrador debe comprobar previamente que la copia corresponde al esquema
  versionado y no tiene triggers/vistas adicionales. La visibilidad de triggers en
  information_schema depende de permisos; un usuario lector no acredita su ausencia.

Los usuarios se aprovisionan con contraseñas externas, sin registrarlas en SQL
versionado o en el historial de shell. Una vez creados, los permisos necesarios son:

```sql
-- Administración de las copias, no de las bases del equipo.
GRANT SELECT ON shipment_migration_copy.* TO 'migration_reader'@'%';
GRANT SELECT, INSERT, DELETE, CREATE TEMPORARY TABLES
    ON courier_migration_copy.* TO 'migration_writer'@'%';
```

No conceder al lector permisos de escritura. El escritor no necesita UPDATE,
DROP ni ALTER, ni acceso a otra base. No se usan las credenciales root en el CLI.
El ensayo automático configura y comprueba esos permisos.

## Exportar y respaldar

Preparar un directorio **fuera del checkout**, propio y 0700. Los archivos contienen
datos: no agregarlos a Git, al PR ni a una imagen. No usar un directorio público o
compartido. Las contraseñas se leen del entorno y se entregan a Docker por nombre;
no aparecen en los argumentos del proceso ni en la salida del CLI.

Desde la raíz del repositorio, en Bash:

```bash
read -r -s -p 'Contraseña del lector: ' MIGRATION_SOURCE_PASSWORD; echo
read -r -s -p 'Contraseña del escritor: ' MIGRATION_TARGET_PASSWORD; echo
export MIGRATION_SOURCE_PASSWORD MIGRATION_TARGET_PASSWORD

# Elegir una ruta externa nueva para cada exportación.
MIGRATION_ARTIFACTS="$HOME/logitrack-migration/ensayo-01"
mkdir -p "$MIGRATION_ARTIFACTS"
chmod 700 "$MIGRATION_ARTIFACTS"

MIGRATION_ARGS=(
  --source-container shipment-copy
  --source-db shipment_migration_copy
  --target-container courier-copy
  --target-db courier_migration_copy
  --artifacts "$MIGRATION_ARTIFACTS"
)

python3 scripts/migracion/repartidores.py export "${MIGRATION_ARGS[@]}"
```

`export` lee las cuatro tablas bajo una única transacción InnoDB de solo lectura,
valida las referencias y genera `source.json` y `source.sql` con modo 0600.
`mysqldump` usa single-transaction, hex-blob y no incorpora credenciales. Se compara
el origen antes/después del backup. Se registra SHA-256 del JSON canónico y del dump;
no autentican un archivo frente a un atacante que pueda reescribir ambos, pero
permiten detectar corrupción o cambios accidentales. Un export no sobrescribe uno
anterior. Si falla, descartar ese directorio incompleto y comenzar con uno nuevo.

El dump contiene DDL de restauración: no ejecutarlo sobre el original. El ensayo
automático restaura el respaldo en otra base descartable y compara las cuatro
tablas, incluidos UUID, decimales, nulos, texto Unicode y microsegundos.

## Importar, comprobar y repetir

```bash
python3 scripts/migracion/repartidores.py apply "${MIGRATION_ARGS[@]}"
python3 scripts/migracion/repartidores.py verify "${MIGRATION_ARGS[@]}"
# Debe conservar el mismo resultado sin duplicados ni cambios de auditoría.
python3 scripts/migracion/repartidores.py apply "${MIGRATION_ARGS[@]}"
```

Antes de escribir se comprueba la integridad de ambos artefactos, que el origen
sigue igual y que el destino usa el contrato de UUID, fechas, nulabilidad y claves
únicas. El CLI no crea ni adapta el esquema de dominio. `target-before.json` conserva
la instantánea previa del destino en el directorio privado.

Las filas nuevas y su registro de lote se insertan en **una misma transacción
InnoDB**. Se usan precondiciones SQL, aislamiento SERIALIZABLE y un lock nominal
para serializar ejecuciones de esta herramienta. Un error a mitad del batch
revierte tanto las filas como el registro. No se usa INSERT IGNORE ni UPDATE sobre
coincidencias. Si se pierde la conexión al confirmar, conservar los artefactos y
repetir el mismo lote: los UUID idénticos y el registro transaccional evitan duplicados.

`verify` compara los campos originales de cada repartidor, comprueba las
asignaciones y vuelve a verificar que las cuatro tablas de origen no cambiaron.
La salida contiene recuentos y hashes, sin registros, contraseñas ni errores SQL.

No hay una transacción distribuida entre servidores. La consistencia del ensayo
requiere mantener ambas copias sin escritores; detectar un cambio no equivale a
bloquear a un administrador o a una aplicación externa. Si aparece una diferencia,
se detiene el procedimiento y se conservan los respaldos para revisar.

## Reversión

```bash
python3 scripts/migracion/repartidores.py rollback "${MIGRATION_ARGS[@]}"
# Después del ensayo, retirar las contraseñas de la sesión.
unset MIGRATION_SOURCE_PASSWORD MIGRATION_TARGET_PASSWORD
```

La reversión elimina únicamente las filas insertadas por ese lote y su registro,
en una misma transacción. Las filas preexistentes idénticas y sus usuarios asociados
se conservan. Antes de borrar se exige que el destino y el registro coincidan con
el ensayo: una edición, vínculo de usuario, fila agregada o registro adulterado hace
fallar la reversión. No se borra una fila que pudo haber empezado a usarse.

La copia de origen permanece intacta y disponible. No se restauran dumps sobre
bases del equipo para demostrar rollback. Si se perdió la instantánea previa o
hay cambios posteriores, se requiere revisar las copias y sus backups; no borrar
manualmente el registro para forzar la reversión.

## Prueba automática reproducible

```bash
python3 -m unittest discover -s scripts/migracion/tests -p 'test_*.py' -v
```

El test crea dos MySQL 8.4.11 sin puertos publicados ni volúmenes del equipo,
aplica el SQL original y el fixture explícito de courier, prepara usuarios limitados
y datos sintéticos. Retira sus propios contenedores y volúmenes anónimos al finalizar.
Los artefactos y contraseñas son temporales. No utiliza docker compose down -v.

Los resultados, versión de los scripts y límite del fixture se registran en
[Evidencia del ensayo](Evidencia_ensayo.md). Una prueba aprobada de este contrato no
acredita que courier-service ya haya implementado o validado su entidad.

## Condición antes de retirar tabla o FK

Este PR no ejecuta el corte de datos. Antes de shipment-service#23 y de la entrega:

1. Integrar courier-service#1 y registrar su commit y SQL; ensayar contra ese esquema.
2. Verificar mediante JPA/API de courier que los UUID y fechas importados se conservan,
   también después de reiniciar; usuario_id continúa sin inferencias automáticas.
3. Obtener backup autorizado y verificable del entorno de entrega y congelar escrituras
   en una ventana acordada. El CLI de ensayo conserva deliberadamente sus guardas
   de copias y no se habilita sobre bases reales cambiando los nombres por defecto.
4. Preparar y revisar un procedimiento de corte separado, con las credenciales mínimas,
   esquema de producción exacto y evidencia de recuentos/UUID/referencias/historial.
5. Cambiar la relación JPA por repartidorId, verificar regresión/integración y confirmar
   la aceptación antes de retirar la FK. Conservar la tabla/copia de origen y respaldos
   durante la verificación; no incluir un DROP automático en esta preparación.

La preparación de la migración y el refactor son tareas separadas. No declarar
Hecho ni retirar datos solo porque esta prueba sintética terminó correctamente.
