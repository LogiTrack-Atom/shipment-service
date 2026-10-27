# Matriz de regresión — Sprint02

Esta matriz identifica pruebas ejecutables, no constituye por sí sola evidencia de que
una ejecución haya pasado. Los resultados actuales se registran en el PR y sus workflows;
la línea base del Sprint01 sigue siendo un antecedente identificado por su commit.

| Regla o contrato | Verificación |
| --- | --- |
| Registro PENDIENTE, tracking único y cliente reutilizado sin sobrescritura indebida | ShipmentServiceTest, ShipmentPersistenceIT, ShipmentSecurityIT |
| Precisión/peso/dimensiones y datos inválidos | EnvioMedidasValidationTest, ShipmentApiIT, OpenApiIT |
| Dieciséis combinaciones de estados, terminales y cancelación | ShipmentServiceTest, ShipmentApiIT |
| UUID de repartidor remoto, nullable y sin FK/JPA entre servicios | ShipmentCourierBoundaryIT, ShipmentSchemaMigrationIT |
| Asignar y EN_RUTA requieren disponibilidad; timeouts, caídas y contratos inválidos | CourierRestIT, RestCourierClientTest |
| Estado, asignación, auditoría e historial sin efectos ante rechazo | CourierRestIT, ShipmentTransactionIT, ShipmentSecurityIT |
| Escrituras concurrentes conservan el UUID y la cancelación confirmados | CourierRestIT |
| Respuesta de alta/asignación/reasignación/estado/cancelación igual a lectura confirmada | ShipmentAuditIT |
| Auditoría e instantes de historial compatibles con DATETIME(6) | ShipmentAuditIT, ShipmentPersistenceIT |
| Atomicidad y logs de éxito solo después de commit | ShipmentTransactionIT, BusinessLogIT |
| Dos clientes/dos repartidores, propios/ajenos, tracking/historial y roles combinados | ShipmentSecurityIT |
| JWT alterado/vencido, algoritmo, emisor, audiencia y sub de usuario | JwtSecurityTest, ShipmentSecurityIT |
| Asociaciones administrativas verificadas e inmutables; sin reclamo por email | ShipmentIdentityIT, ShipmentSecurityIT |
| Lecturas independientes de courier y security | ShipmentSecurityIT, ShipmentCourierBoundaryIT |
| ApiError, OpenAPI tipado y Swagger | OpenApiIT, ShipmentSecurityIT, ShipmentIdentityIT |
| Configuración dev/prod obligatoria, precedencia y secretos externos | RemoteConfigurationIT |
| Copia de repartidores preserva UUID y auditoría, con reversión controlada | 19 pruebas Python del workflow de migración |
| Escenario REST reproducible con proveedores reales | Colección Postman Sprint02: 16 solicitudes, datos de prueba aislados |

## Comandos

```sh
./mvnw -B -ntp verify javadoc:javadoc
python3 -m unittest discover -s scripts/migracion/tests -p 'test_*.py' -v
npx --yes --package=newman@6.2.3 newman run postman/LogiTrack-Sprint02.postman_collection.json \
  -e /ruta/a/entorno-privado.json --bail
```

La suite Java utiliza MySQL temporales y proveedores HTTP controlados. Las pruebas de
configuración usan un Config Server real con Git temporal y snapshots SHA-256; no
requieren GitHub. El ensayo Python usa copias aisladas y su guía explica la utilidad JPA
del proveedor. La colección REST requiere shipment/courier y un JWT ADMIN vigente;
los tokens se guardan solo en el entorno privado. Los escenarios de pertenencia y errores
remotos se reutilizan desde sus IT, sin duplicarlos como requisito manual de este cierre.

## Auditoría y transacciones

Spring Data usa instantes UTC truncados a microsegundos. Las escrituras hacen flush
antes de convertir a DTO; el flush aplica callbacks y SQL dentro de la transacción,
sin adelantar el commit ni los logs de éxito. Un fallo posterior mantiene el rollback.
Una asignación idéntica sin cambio de fila puede conservar updatedAt. Los datos históricos
y el SQL congelado del Sprint01 no se reescriben para adaptar esta precisión.

No se agregan endpoints para leer datos internos ni consultas remotas a las lecturas de
negocio. Compose general y publicación de imágenes corresponden a #28/#29.
