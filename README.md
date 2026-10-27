# shipment-service — LogiTrack Atom

Servicio de envíos, clientes, direcciones e historial. La administración de repartidores
pertenece a [courier-service](https://github.com/LogiTrack-Atom/courier-service).
Java 25, Spring Boot 4.1.1, MySQL 8.4 y Maven Wrapper.

## Estado de la extracción del Sprint02

La issue #23 reemplaza la relación JPA a repartidor por `repartidorId` (UUID nullable).
**Cambio de JSON:** desaparece el objeto `repartidor`; las respuestas de envíos
incluyen `repartidorId`, también cuando es `null`. No se enriquecen respuestas con
llamadas remotas: consulta por UUID, tracking, listado e historial funcionan sin courier.
Los endpoints `/api/v1/repartidores` se trasladaron a courier, puerto 8081.

La integración REST (#24) comprueba disponibilidad al asignar y al entrar en EN_RUTA.
Solo consulta `GET /api/v1/repartidores/{id}/disponibilidad`, con timeouts configurables
(2 s de conexión y 3 s de lectura) y sin seguir redirecciones. No reserva repartidores,
no escribe en courier ni aprueba operaciones ante fallas. La disponibilidad puede cambiar
después de la consulta: se vuelve a comprobar al iniciar ruta.

**Autorización de esta etapa:** antes de llamar a courier se exige una única cabecera
Bearer, firma RS256 local, emisor/audiencia/UUID/roles y tiempos válidos, y rol ADMIN.
El JWT validado se reenvía únicamente al origen configurado. No se acepta token por query
ni formulario. La matriz completa de permisos y pertenencia de CLIENTE/REPARTIDOR,
y el filtro general de la API, siguen en #25. Esta validación del transporte no protege
todavía todos los endpoints de shipment. Config Client y Compose general siguen en #26/#28.

| Situación al verificar disponibilidad | HTTP local |
| --- | --- |
| JWT ausente/inválido o sin ADMIN | 401 / 403, sin llamada remota |
| INACTIVO (`200`, disponible false) | 422 |
| Repartidor inexistente (`404`) | 404 |
| Conexión, timeout, `429` o `5xx` | 503 |
| JSON/identidad/estado incoherente, redirección u otro HTTP inesperado (incluido `401/403`) | 502 |

Ante rechazo no cambian asignación, estado, auditoría ni historial. Solo se aceptan los
tres campos acordados con tipos correctos, un UUID coincidente y estado/disponibilidad
coherentes; el cuerpo está limitado a 64 KiB. No se vuelcan tokens ni respuestas remotas
a mensajes públicos o logs de negocio.

## Ejecución y esquema

Con Java 25, configurar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `COURIER_SERVICE_URL`
y `JWT_PUBLIC_KEY_LOCATION=file:/ruta/externa/jwt-public.pem` en el proceso.
La clave debe ser pública RSA X.509 de al menos 2048 bits; no se versiona y no se
requiere la clave privada del emisor. Un archivo ausente o inválido impide arrancar:

```sh
bash mvnw spring-boot:run
```

La API escucha en 8080. Swagger UI: `/swagger-ui/index.html`; OpenAPI: `/v3/api-docs`.
`ddl-auto=validate` comprueba el esquema, sin crearlo ni modificarlo.
El Compose existente arranca shipment y su MySQL; todavía no orquesta el Sprint02.
Usar `.env.example`, completar contraseñas, `JWT_PUBLIC_KEY_FILE` (archivo público
externo legible por el contenedor) y `COURIER_SERVICE_URL` accesible desde Docker.
Luego ejecutar `docker compose up --build -d`. La clave se monta solo lectura, no se
incluye en la imagen. Para MySQL únicamente: `docker compose up -d mysql` también
requiere definir esas variables porque Compose valida el archivo completo.
No versionar `.env` ni eliminar el volumen del equipo para adaptar su esquema.

- **Base nueva:** `docker/mysql/init/001-schema.sql` crea clientes, envios e historial.
  `repartidor_id BINARY(16)` conserva su índice y no tiene FK a courier.
- **Base existente:** respaldar y ensayar la restauración; verificar primero la copia
  de repartidores mediante el [procedimiento de migración](docs/sprint02/migracion/README.md).
  Después aplicar administrativamente `docker/mysql/migrations/002-separar-repartidores.sql`.
  El ALTER solo retira la FK antigua. Conserva todas las filas y la tabla de respaldo
  `repartidores`; la aplicación ya no la consulta. El DDL tiene commit implícito.
- **Reversión:** detener escrituras, comprobar que todos los UUID asignados existen
  en la tabla local de respaldo y reconstruir sus faltantes desde una copia verificada.
  Solo entonces restablecer la FK y desplegar el código anterior. No basta con volver
  al JAR anterior si se asignaron UUID nuevos. El ensayo automatizado comprueba
  retirada/restauración de FK sin modificar los datos iniciales.

No se ha aplicado el ALTER ni la migración a la base compartida del equipo.

## API local

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/envios` | Registro PENDIENTE con tracking e historial inicial |
| GET | `/api/v1/envios?estado=PENDIENTE` | Lista local, filtro opcional |
| GET | `/api/v1/envios/{id}` | DTO con `repartidorId` nullable |
| GET | `/api/v1/envios/seguimiento/{codigoSeguimiento}` | Consulta por tracking |
| GET | `/api/v1/envios/{id}/historial` | Eventos por fecha ascendente |
| PUT | `/api/v1/envios/{id}/repartidor` | Solo PENDIENTE, ADMIN y disponibilidad REST comprobada |
| PATCH | `/api/v1/envios/{id}/estado` | Máquina de estados; EN_RUTA exige disponibilidad |
| PATCH | `/api/v1/envios/{id}/cancelacion` | Solo PENDIENTE → CANCELADO |

Peso positivo DECIMAL(10,3), dimensiones positivas DECIMAL(10,2).
Errores uniformes `ApiError`: timestamp, status, error, mensaje, detalles.
La asignación no cambia estado ni agrega historial. Estado e historial se confirman
juntos; un error de persistencia revierte ambos. Los logs de éxito se emiten tras commit.

## Pruebas y Postman

```sh
# Sin Docker ni credenciales externas.
bash mvnw clean test
# Unitarias y Spring/MySQL aislados, con Docker disponible.
bash mvnw clean verify
# Migración sobre dos MySQL temporales, conservando el origen Sprint01.
python3 -m unittest discover -s scripts/migracion/tests -p 'test_*.py' -v
```

Las IT usan el SQL nuevo y un doble exclusivamente de `CourierClient`: no sustituyen
repositorios, transacciones ni MySQL. Comprueban las 16 transiciones, precisión,
UUID/auditoría, FK locales, ausencia de FK remota, rollback e independencia de lecturas.
`ShipmentSchemaMigrationIT` arranca desde el SQL congelado del Sprint01, aplica el
ALTER y comprueba con JPA `validate` asignaciones nulas/repetidas e historial sin cambios.
El ensayo Python conserva la copia/exportación/importación y su verificación contra courier;
la comprobación JPA del proveedor requiere las variables documentadas en su guía.

[Colección Sprint02](postman/LogiTrack-Sprint02.postman_collection.json): ejecutar
completa y en orden con shipment y courier disponibles, la misma clave pública
configurada en ambos y un JWT ADMIN vigente emitido por security. Importar el
[entorno de ejemplo](postman/LogiTrack-Local.postman_environment.json), configurar
`baseUrl`, `courierBaseUrl` y `adminToken` localmente. No exportar tokens ni credenciales.
No hace falta copiar UUID: se capturan desde las respuestas de ambos servicios.

```sh
npx --yes --package=newman@6.2.3 newman run postman/LogiTrack-Sprint02.postman_collection.json \
  -e /ruta/a/entorno-local-no-versionado.json --bail
```

La colección comprueba el rechazo sin token, inexistente e inactivo, las consultas,
la auditoría sin cambios ante rechazo, la reactivación y la entrega con tres eventos.
Crea un cliente, un envío y un repartidor; no elimina datos. La colección Sprint01
se conserva como antecedente y requiere su versión del código. Los fallos de red,
protocolo, contrato, JWT y rollback se reproducen además en `CourierRestIT` mediante
servidor HTTP controlado, validador JWT y MySQL reales, sin mocks de shipment.

## Plan y antecedentes

- [Contratos técnicos](docs/sprint02/Contratos_tecnicos.md) y [backlog](docs/sprint02/Backlog.md).
- [Guía histórica Sprint01](docs/sprint01/README.md), con pruebas y autorías de esa etapa.
- Integración por PR hacia `develop`; `main` mantiene su función actual.
- Todas las issues se siguen en el [Project común](https://github.com/orgs/LogiTrack-Atom/projects/1).
