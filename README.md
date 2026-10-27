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

**Autorización (#25):** todas las operaciones de negocio requieren una única cabecera
`Authorization: Bearer ...`, firma RS256 local, emisor, audiencia, UUID, roles y tiempos
válidos. No se aceptan credenciales por query, formulario o cookies. Las lecturas no
llaman a security ni a courier. Config Client y Compose general siguen en #26/#28.

| Operación | ADMIN | CLIENTE | REPARTIDOR |
| --- | --- | --- | --- |
| Crear envío | Sí | Para su cliente asociado | No |
| Listar, detalle, tracking e historial | Todos | Solo propios | Solo asignados |
| Asignar o cancelar | Sí, con las reglas de negocio | No | No |
| Cambiar estado | Transiciones válidas | No | EN_RUTA y ENTREGADO, solo sus asignaciones |
| Vincular cuentas | Sí | No | No |

`sub` identifica al usuario de security, nunca automáticamente al cliente o al repartidor.
Los listados se restringen en SQL. Consultar un envío ajeno o inexistente como CLIENTE o
REPARTIDOR devuelve 403 sin datos; ADMIN recibe 404 ante un recurso inexistente.
Conocer el tracking o enviar UUID ajenos por cuerpo/query no concede acceso.

| Situación al verificar disponibilidad | HTTP local |
| --- | --- |
| JWT ausente/inválido o permiso local insuficiente | 401 / 403, sin llamada remota |
| INACTIVO (`200`, disponible false) | 422 |
| Repartidor inexistente (`404`) | 404 |
| Conexión, timeout, `429` o `5xx` | 503 |
| JSON/identidad/estado incoherente, redirección u otro HTTP inesperado (incluido `401/403`) | 502 |

Las asignaciones, cancelaciones y cambios de estado bloquean la fila del envío
hasta confirmar o revertir la transacción. Esto impide que una escritura concurrente
sobrescriba estado/asignación mientras se comprueba disponibilidad por REST; el límite
de espera remoto sigue siendo finito. No se realiza una transacción distribuida.

Ante rechazo no cambian asignación, estado, auditoría ni historial. Solo se aceptan los
tres campos acordados con tipos correctos, un UUID coincidente y estado/disponibilidad
coherentes; el cuerpo está limitado a 64 KiB. No se vuelcan tokens ni respuestas remotas
a mensajes públicos o logs de negocio.

## Ejecución y esquema

Con Java 25, configurar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `COURIER_SERVICE_URL`, `SECURITY_SERVICE_URL`
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
externo legible por el contenedor), `COURIER_SERVICE_URL` y `SECURITY_SERVICE_URL` accesibles desde Docker.
Luego ejecutar `docker compose up --build -d`. La clave se monta solo lectura, no se
incluye en la imagen. Para MySQL únicamente: `docker compose up -d mysql` también
requiere definir esas variables porque Compose valida el archivo completo.
No versionar `.env` ni eliminar el volumen del equipo para adaptar su esquema.

- **Base nueva:** `docker/mysql/init/001-schema.sql` crea clientes, envios, historial y referencias de autorización `repartidor_usuarios`.
  `repartidor_id BINARY(16)` conserva su índice y no tiene FK a courier.
- **Base existente:** respaldar y ensayar la restauración; verificar primero la copia
  de repartidores mediante el [procedimiento de migración](docs/sprint02/migracion/README.md).
  Después aplicar administrativamente `docker/mysql/migrations/002-separar-repartidores.sql`.
  El ALTER solo retira la FK antigua. Conserva todas las filas y la tabla de respaldo
  `repartidores`; la aplicación ya no la consulta. Después aplicar `003-identidades-autorizacion.sql`,
  que agrega `clientes.usuario_id` nullable único y la tabla `repartidor_usuarios` vacía.
  No se infieren identidades por email. Ambos DDL tienen commit implícito.
- **Reversión:** detener escrituras, comprobar que todos los UUID asignados existen
  en la tabla local de respaldo y reconstruir sus faltantes desde una copia verificada.
  Solo entonces restablecer la FK y desplegar el código anterior. No basta con volver
  al JAR anterior si se asignaron UUID nuevos. El ensayo automatizado comprueba
  retirada/restauración de FK sin modificar los datos iniciales.

No se ha aplicado el ALTER ni la migración a la base compartida del equipo.

## Asociaciones de identidad

ADMIN vincula clientes históricos con `PUT /api/v1/clientes/{id}/usuario`, cuerpo
`{"usuarioId":"<UUID de cuenta>"}`. Shipment comprueba en security que la cuenta existe
con rol CLIENTE antes de guardar la asociación única. Un cliente nuevo creado por CLIENTE
se vincula al `sub` validado; un email histórico o perteneciente a otro cliente devuelve
409, nunca adjudica su historial. El contacto de un cliente ya asociado no se sobrescribe.

Para habilitar a un repartidor:

1. Crear una cuenta REPARTIDOR en security y el repartidor en courier como ADMIN.
2. Vincularlos con `PUT /api/v1/repartidores/{id}/usuario` en courier.
3. Registrar la referencia en shipment mediante `PUT /api/v1/asociaciones/repartidores/{id}`,
   con el mismo cuerpo `{"usuarioId":"<UUID de cuenta>"}`.

El tercer paso verifica el rol en security y la asociación vigente mediante
`GET /api/v1/repartidores/{id}/usuario` de courier, exclusivamente ADMIN. Shipment guarda
solo ambos UUID y fecha de comprobación, sin entidad remota, estado, nombre ni FK entre bases.
Esta referencia permite lecturas autorizadas con ambos proveedores desconectados.
No basta con que exista un repartidor activo para que cualquier cuenta pueda utilizarlo.

Las asociaciones son inmutables mediante estas APIs: repetir la misma es idempotente,
reemplazarla o reutilizar la cuenta en otro recurso produce 409. La revinculación requiere
un procedimiento nuevo coordinado en ambos servicios; no se permite editar solo una copia.
404 significa recurso inexistente; 422 rol incompatible; 502 contrato remoto inválido;
503 proveedor no disponible. Ante un fallo no se guarda la asociación. Las verificaciones
administrativas usan HTTP de lectura con timeouts finitos, fuera de la transacción de escritura.

Consultar [la decisión arquitectónica](docs/sprint02/ADR-001-identidad-local.md) y
[los contratos compartidos](docs/sprint02/Contratos_tecnicos.md).

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
| PATCH | `/api/v1/envios/{id}/cancelacion` | ADMIN, solo PENDIENTE → CANCELADO |
| PUT | `/api/v1/clientes/{id}/usuario` | Asociación administrativa CLIENTE |
| PUT | `/api/v1/asociaciones/repartidores/{id}` | Referencia administrativa verificada de REPARTIDOR |

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

Las IT usan MySQL real aislada y JWT RS256 firmados con claves efímeras. Las regresiones
de dominio usan un doble del puerto `CourierClient`; `CourierRestIT` usa HTTP real contra
un proveedor controlado. `ShipmentIdentityIT` verifica por HTTP las asociaciones y los
rechazos de proveedores. `ShipmentSecurityIT` no reemplaza filtros, decoder ni pertenencia.
No se sustituyen repositorios, transacciones ni MySQL. Comprueban las 16 transiciones, precisión,
UUID/auditoría, FK locales, ausencia de FK remota, rollback e independencia de lecturas.
`ShipmentSchemaMigrationIT` arranca desde el SQL congelado del Sprint01, aplica el
DDL 002/003 y comprueba con JPA `validate` asignaciones nulas/repetidas e historial sin cambios.
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
