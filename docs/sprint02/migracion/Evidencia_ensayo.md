# Evidencia del ensayo — 27/10/2026

## Alcance verificado

Se ejecutaron **18 pruebas aprobadas en 49.625 segundos** mediante:

```bash
python3 -m unittest discover -s scripts/migracion/tests -p 'test_*.py' -v
```

Dos MySQL 8.4.11 efímeros, sin puertos publicados ni volúmenes del equipo; origen
con el SQL de Sprint01 y destino con el fixture explícito del contrato de courier.
Usuarios limitados: SELECT en el origen y SELECT/INSERT/DELETE/CREATE TEMPORARY
TABLES en el destino. Contraseñas y artefactos externos temporales; sin credenciales
en argumentos, salida o archivos versionados.

Dataset sintético: 2 clientes, 3 repartidores, 5 envíos y 5 filas de historial;
3 asignaciones (incluida una referencia repetida) y 2 envíos sin repartidor.
Hay repartidores ACTIVO/INACTIVO, texto Unicode y comillas, UUID BINARY(16), medidas
con decimales y fechas DATETIME(6) con microsegundos distintos.

## Resultados

| Comprobación | Resultado |
| --- | --- |
| Backup SQL restaurado en otra base descartable | Las cuatro tablas coinciden íntegramente |
| Export/import de repartidores | UUID, texto, estado y auditoría idénticos |
| Clientes, envíos, tracking, asignaciones e historial | Origen sin cambios |
| Repetición, doble rollback y reaplicación | Sin duplicados; datos y auditoría conservados |
| Fila destino preexistente y usuario vinculado | Conservados después de importar y revertir |
| Conflicto de UUID/contenido | Rechazo, sin sobrescritura |
| Fallo SQL real a mitad del lote | Ni filas ni registro parcial en el destino |
| Edición/vinculación posterior o registro adulterado | Reversión rechazada sin borrar datos |
| JSON o backup alterado, u origen modificado | Operación rechazada |
| Auditoría inválida, nombre vacío, referencias huérfanas | Exportación rechazada |
| Origen vacío | Importación, verificación y reversión sin fabricar datos |
| Auditoría destino incompatible (DATETIME(3)) | Importación rechazada antes de escribir |
| Base real, root, contenedor sin etiqueta o servidor compartido | Rechazo |
| Artefactos dentro del checkout o directorio público | Rechazo |
| Escritura con lector / acceso del escritor a mysql.user | Denegados por MySQL |

El SQL original coincide byte a byte con el commit del Sprint01
`899311e39ec8dd270f1e50d8289e35129b713799`. No se modificaron el código Java, sus
pruebas, pom.xml, Dockerfile ni Compose. Esta verificación específica de migración
no se presenta como una nueva ejecución de la regresión Java del Sprint01.

## Archivos exactos ensayados

| Archivo | SHA-256 |
| --- | --- |
| `docker/mysql/init/001-schema.sql` | `322eb45e27330386f0ddd3600cf537ad5de68b6deb5dd35db6b80db1098af33b` |
| `docker/mysql/migrations/001-migration-ledger.sql` | `669771fc9dce4d916fd854fc4c6aa94b12e3a38830ae5b412b9635beaaa569c6` |
| `scripts/migracion/repartidores.py` | `7724fb7b3665ea5ef3f4d1a294cdb18939cf7bb89d2c6af0e07ab905650a5bb9` |
| `scripts/migracion/tests/test_migracion.py` | `1b0abea656ff8d9f1ec17bbe6150666f1f613ab9f1accf6cce9ea2b2eb0c7e6a` |
| `scripts/migracion/tests/fixtures/courier-contract.sql` | `c3e79c149e0ff16cb4f2246f2e650367f2196ab97c1784569e65890c8f280160` |

## Pendiente para cerrar la integración

**No se probó el esquema real ni la entidad JPA de courier-service.**
Su implementación sigue en [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1).
Se debe registrar su commit, repetir el ensayo sobre su SQL y comprobar que la
lectura/persistencia JPA y el reinicio conservan UUID y fechas importados.

El fixture acredita la preparación contra el contrato, no la entrega del proveedor.
Shipment-service#22 y su PR permanecen abiertos; no se retira la tabla ni la FK de
origen y no se ejecuta el corte sobre datos del equipo. La guía describe la ventana
sin escritores y la validación adicional anterior al refactor.
