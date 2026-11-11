# Contratos para preparar el frontend · S04-01 / #71

**Contrato técnico conciliado; ADR de sesión renovable adoptado por el equipo.** Actualizado: 10/11/2026. Responsable: MaxDZ-10. Organización: LogiTrack-Atom.

Las pantallas de autenticación, cliente HTTP, Shipment, Courier y administración ya están integradas en `logitrack-frontend/develop`. La integración real y la persistencia del núcleo se verificaron por Gateway y Nginx en shipment-service#72. Este paquete conserva modelos y ejemplos estáticos, y distingue esa simulación de las comprobaciones ejecutadas con servicios.

S04-00/G0 está retirado como requisito. No es necesario reabrir la auditoría para consumir estos contratos. La sesión ahora se recupera al recargar mediante renovación segura; es una decisión técnica del equipo del 10/11/2026, conforme a la interpretación más exigente de persistencia de la consigna. No se atribuye aprobación individual del profesor. El hosting público es opcional.

Ver [conciliación y cobertura efectiva](CONCILIACION.md) e [identificación del entorno probado](integracion-verificada.json).

## Archivos e interfaces

- `modelos.schema.json`: 25 definiciones JSON Schema 2020-12 de solicitudes/respuestas. Usar `#/$defs/<Modelo>` para validar un payload; el documento raíz es un registro de modelos. Interfaces independientes de la estructura que elija Gab para React/TypeScript.
- `escenarios.mock.json`: 25 escenarios con actor, método, ruta, código, cuerpo y modelo; solo ejemplos estáticos para conectar al adaptador mock de la base. `allowRealRequests: false` es metadato de intención, no un bloqueo de red implementado. Sin servidor ni interceptores instalados en este paquete.
- Este documento: rutas/modelos, flujos por rol, ADR implementado provisionalmente y criterios pendientes.

Los esquemas de solicitudes limitan lo que debe construir el cliente; no afirman que todos los DTO del servidor rechacen cada campo adicional. Las respuestas admiten extensión de campos para no inventar un contrato cerrado. Las reglas de autorización, estado, unicidad y máximo de bytes UTF-8 de password requieren lógica adicional: JSON Schema no sustituye el backend. Validar decimales con aritmética exacta para evitar falsos errores de multipleOf binario.

## Referencias contrastadas

Referencias históricas de código para los DTO y pruebas ejecutadas en [PR #83](https://github.com/LogiTrack-Atom/shipment-service/pull/83), sin repetir suites. Los enlaces siguientes fijan las fuentes del contrato:

| Repositorio | SHA |
| --- | --- |
| [shipment-service](https://github.com/LogiTrack-Atom/shipment-service/tree/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a) | `3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a` |
| [courier-service](https://github.com/LogiTrack-Atom/courier-service/tree/003003d619d0479bc0577d839a580e61e51a8f0b) | `003003d619d0479bc0577d839a580e61e51a8f0b` |
| [security-service](https://github.com/LogiTrack-Atom/security-service/tree/37bb9e3146010fc4f459fcca8a0ae970b5ce1c64) | `37bb9e3146010fc4f459fcca8a0ae970b5ce1c64` |
| [api-gateway](https://github.com/LogiTrack-Atom/api-gateway/tree/283cbf80423705b3768787b77c8d35f9a0fd6305) | `283cbf80423705b3768787b77c8d35f9a0fd6305` |
| [eureka-server](https://github.com/LogiTrack-Atom/eureka-server/tree/49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b) | `49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b` |
| [configserver](https://github.com/LogiTrack-Atom/configserver/tree/4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5) | `4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5` |
| [configserver-ms](https://github.com/LogiTrack-Atom/configserver-ms/tree/a08f95ee8fa0bf412fb30f68776e9a8794f94110) | `a08f95ee8fa0bf412fb30f68776e9a8794f94110` |

La propuesta documental BearerAuth de Courier, diferida sin merge, se conserva en [PR #23](https://github.com/LogiTrack-Atom/courier-service/pull/23); no cambia autorización ni estos DTO. La rectificación del diagnóstico de migración está en [PR #87](https://github.com/LogiTrack-Atom/shipment-service/pull/87). Las PR de auditoría se cerraron sin merge; sus hallazgos y pruebas se conservan como historia, no como dependencia funcional.

## Matriz de flujos por rol

Las clases citadas pertenecen a los SHA anteriores y se ejecutaron en la baseline #67. Los escenarios de este paquete son ejemplos de UI, no nuevas pruebas con servicios.

| Rol / flujo | Contrato y resultado | Rechazo / propiedad | Evidencia y mocks |
| --- | --- | --- | --- |
| Público: registro/login | I auth/register:201 Usuario; auth/login:200 Token | Registro solo CLIENTE; credenciales no se guardan en fixtures | security AutenticacionJwtIT; esquemas RegistroRequest/CredencialesLogin/TokenResponse. Sin JWT ficticio que parezca credencial real. |
| A/Cl/R: identidad actual | I GET auth/me:200 UsuarioResponse | 401 sin credencial; roles no reemplazan permisos | AutenticacionJwtIT, JwtSecurityTest; usuario-actual, sin-sesion. |
| CLIENTE: alta de envío | S POST envios:201 EnvioResponse, inicial PENDIENTE | Propiedad por usuarioId local, no por email; error no deja altas parciales | ShipmentApiIT, ShipmentSecurityIT, ShipmentIdentityIT; crear-envio, validacion/conflicto. |
| A/Cl/R: lista, detalle, tracking, historial | S GET envios y consultas:200 | ADMIN todos, CLIENTE propios, REPARTIDOR asignados; tracking no público | ShipmentSecurityIT, ShipmentAuditIT; lista-admin/cliente/repartidor/vacia, envio-ajeno. |
| ADMIN: asignación | S PUT envios/{id}/repartidor:200 | PENDIENTE, proveedor ACTIVO; no agrega evento de estado | CourierFeignIT, ShipmentTransactionIT; asignar-sin-nuevo-evento, regla-negocio, proveedor-caido. |
| REPARTIDOR: avance | S PATCH estado: EN_RUTA y ENTREGADO | Solo asignaciones propias; CLIENTE no transiciona | ShipmentSecurityIT, ShipmentServiceTest; avanzar-en-ruta/entregado, historial-tres-eventos. |
| ADMIN: cancelación | S PATCH cancelacion sin cuerpo:200 | Solo PENDIENTE; estados terminales no cambian | ShipmentApiIT, ShipmentServiceTest; usar EstadoEnvio y EnvioResponse; escenario de cancelación se agrega al implementar la pantalla. |
| ADMIN / REPARTIDOR: Courier | A lista/alta/edita/estado; R detalle/disponibilidad propios | CLIENTE no accede; ACTIVO no reserva capacidad | RepartidorApiIT, JwtSecurityTest; courier-lista/disponibilidad, cliente-no-admin. |
| ADMIN: identidades en pasos | I POST usuarios; C POST repartidores; C PUT usuario; S PUT asociación | IDs distintos; sin transacción distribuida ni reintento ciego de POST incierto | ShipmentIdentityIT, RepartidorApiIT; esquemas de asociaciones y alta-resultado-incierto como patrón de UX. |

## Acceso y prefijos

La URL base del frontend será configurable. Usar siempre el Gateway para el negocio; los prefijos siguientes ya incluyen `/api/v1` y `StripPrefix=2` elimina `/applications/<servicio>` al reenviar:

| Alias usado abajo | Prefijo público |
|---|---|
| S | `/applications/apishipment/api/v1` |
| C | `/applications/apicourier/api/v1` |
| I | `/applications/apisecurity/api/v1` |

Enviar `Authorization: Bearer <token>` en operaciones protegidas. Los roles son `ADMIN`, `CLIENTE`, `REPARTIDOR`. Los UUID de usuario (`sub`) y de dominio (`cliente.id`, `repartidor.id`) son identidades distintas. Ocultar acciones por rol es presentación; el backend conserva la autorización y la pertenencia. Los mocks deben simular ambas.

## Operaciones existentes

**A** = ADMIN, **Cl** = CLIENTE, **R** = REPARTIDOR. Los roles de la tabla no sustituyen la comprobación de propiedad del recurso en los servicios. Éxito y respuestas reflejan el código inspeccionado; la evidencia de referencia #67 ya fue ejecutada; la aceptación final de #71 continúa pendiente.

| Método | Ruta después del alias | Acceso | Solicitud | Éxito / respuesta |
|---|---|---|---|---|
| POST | I `/auth/register` | Público | `{email,password}` | 201 Usuario |
| POST | I `/auth/login` | Público | `{email,password}` | 200 Token |
| POST | I `/auth/session` | Mismo origen + cabecera CSRF | `{email,password}` | 200 Token + cookie HttpOnly/Secure/Strict |
| POST | I `/auth/session/refresh` | Mismo origen + cabecera CSRF | Cookie de renovación | 200 Token + cookie rotada; 401 limpia cookie |
| POST | I `/auth/session/logout` | Mismo origen + cabecera CSRF | Cookie de renovación | 204 + cookie eliminada |
| GET | I `/auth/me` | A, Cl, R | — | 200 Usuario autenticado |
| POST | I `/usuarios` | A | `{email,password,rol}` | 201 Usuario |
| GET | I `/usuarios/{id}` | A | — | 200 Usuario |
| POST | S `/envios` | A, Cl | EnvioRequest | 201 Envio |
| GET | S `/envios?estado=...` | A, Cl, R | Filtro opcional | 200 Envio[] visibles |
| GET | S `/envios/{id}` | A, Cl, R | — | 200 Envio permitido |
| GET | S `/envios/seguimiento/{codigoSeguimiento}` | A, Cl, R | — | 200 Envio permitido |
| GET | S `/envios/{id}/historial` | A, Cl, R | — | 200 Historial[] |
| PATCH | S `/envios/{id}/estado` | A, R | `{estado}` | 200 Envio |
| PATCH | S `/envios/{id}/cancelacion` | A | Sin cuerpo | 200 Envio |
| PUT | S `/envios/{id}/repartidor` | A | `{repartidorId}` | 200 Envio |
| PUT | S `/clientes/{id}/usuario` | A | `{usuarioId}` | 200 Asociación |
| PUT | S `/asociaciones/repartidores/{id}` | A | `{usuarioId}` | 200 Asociación |
| POST | C `/repartidores` | A | `{nombre,tipoVehiculo}` | 201 Repartidor |
| GET | C `/repartidores?estado=...` | A | Filtro opcional | 200 Repartidor[] |
| GET | C `/repartidores/{id}` | A, R propio | — | 200 Repartidor |
| PUT | C `/repartidores/{id}` | A | `{nombre,tipoVehiculo}` | 200 Repartidor |
| PATCH | C `/repartidores/{id}/estado` | A | `{estado}` | 200 Repartidor |
| GET | C `/repartidores/{id}/disponibilidad` | A, R propio | — | 200 Disponibilidad |
| PUT | C `/repartidores/{id}/usuario` | A | `{usuarioId}` | 200 Repartidor |
| GET | C `/repartidores/{id}/usuario` | A | — | 200 IdentidadRepartidor |
| GET | C `/admin/configuracion` | A | — | 200 Configuración |

No hay listado general de usuarios, listado general de clientes, actualización/baja de usuarios, borrado de envíos, refresh ni logout remoto en los controladores originales; los nuevos endpoints de sesión renuevan/revocan la cookie según la tabla. #S04-09 debe diseñar el flujo administrativo alrededor de las operaciones disponibles. CORS y el proxy del núcleo ya se verificaron; el hosting público conserva su aceptación específica.

## Modelos y validaciones

| Modelo | Campos JSON |
|---|---|
| Token | `accessToken: string`, `tokenType: string` (Bearer), `expiresIn: number` (segundos) |
| Usuario | `id: UUID`, `email: string`, `roles: string[]`, `createdAt`, `updatedAt` |
| Cliente | `id: UUID`, `nombre: string`, `email: string`, `telefono: string\|null` |
| Dirección | `calle`, `numero`, `ciudad`, `codigoPostal` como strings |
| Envio | `id`, `codigoSeguimiento`, `cliente: Cliente`, `repartidorId: UUID\|null`, `pesoKg`, `anchoCm`, `altoCm`, `largoCm`, `estado`, `origen: Dirección`, `destino: Dirección`, `createdAt`, `updatedAt` |
| Historial | `id: UUID`, `estadoAnterior: EstadoEnvio\|null`, `estadoNuevo: EstadoEnvio`, `fechaCambio` |
| Repartidor | `id: UUID`, `nombre`, `tipoVehiculo`, `estado: ACTIVO\|INACTIVO`, `createdAt`, `updatedAt` |
| Disponibilidad | `repartidorId: UUID`, `estado: ACTIVO\|INACTIVO`, `disponible: boolean` |
| IdentidadRepartidor | `repartidorId: UUID`, `usuarioId: UUID\|null` |
| Asociación | `recursoId: UUID`, `usuarioId: UUID` |
| Configuración | `aplicacion: string`, `perfiles: string[]`, `marcador: string` |
| ApiError de los servicios | `timestamp`, `status: number`, `error: string`, `mensaje: string`, `detalles: string[]` |

Las fechas `Instant` se representan como strings ISO-8601 en UTC. Tratar UUID y tracking como strings opacos. Los decimales del envío se serializan como números JSON; la UI debe conservar precisión en la entrada y validar su escala, sin redondear silenciosamente ni cambiar el contrato a strings sin acuerdo.

`EnvioRequest` recibe únicamente `cliente: {nombre,email,telefono?}`, `origen`, `destino`, `pesoKg`, `anchoCm`, `altoCm`, `largoCm`. Peso positivo, hasta 7 enteros y 3 decimales; dimensiones positivas, hasta 8 enteros y 2 decimales. Direcciones con cuatro campos no vacíos, cliente con nombre/correo no vacíos y correo válido. No enviar `id`, estado, `usuarioId`, auditoría o un objeto repartidor dentro del alta. El backend asocia CLIENTE a su identidad local; no se debe inferir propiedad por coincidencia de email.

Credenciales: email no vacío, válido y hasta 254 caracteres; se normaliza recortando extremos y pasando a minúsculas. Login recibe password no vacía de hasta 72 caracteres y 72 bytes UTF-8; registro/alta administrativa exigen además al menos 12 caracteres. La contraseña no se recorta ni normaliza. Registro público solo crea CLIENTE; alta ADMIN recibe un `rol` obligatorio del enum. `roles` en la respuesta es una colección, no el campo singular del alta. Nombre y tipo de vehículo de repartidor: no vacíos y máximo 255 caracteres cada uno.

## Estados y autorización de negocio

- Estado inicial de envío: `PENDIENTE`; transiciones de negocio `PENDIENTE → EN_RUTA|CANCELADO` y `EN_RUTA → ENTREGADO`. `ENTREGADO` y `CANCELADO` son terminales. Repetir estado es error, no éxito idempotente.
- ADMIN puede ejecutar las transiciones permitidas; R solo `EN_RUTA` y `ENTREGADO` en sus asignaciones; Cl no cambia estados. Cancelación separada exclusiva ADMIN y solo pendiente.
- La lista devuelve un array, sin paginación ni orden garantizado: ADMIN ve todos, Cl propios y R asignados. Las lecturas por UUID/tracking exigen pertenencia; conocer tracking no hace pública la consulta.
- El historial está ordenado por `fechaCambio` ascendente; no se garantiza desempate. Asignar repartidor no cambia estado ni agrega historial. La asignación síncrona verifica disponibilidad y solo admite PENDIENTE.
- `disponible` significa únicamente `estado == ACTIVO`: no reserva capacidad ni garantiza exclusividad. No mostrar «reservado» después de esta consulta.
- El flujo de identidad de R requiere alta de usuario, alta de repartidor, asociación en courier y luego referencia verificada en shipment. Conservar IDs de pasos confirmados; tras perder una respuesta de POST, mostrar estado incierto y reconciliar, sin repetir altas automáticamente.

## Errores, sesión y mocks mínimos

Simular como mínimo: 400 validación, 401 credencial ausente/inválida/expirada, 403 rol o pertenencia, 404 recurso no encontrado, 409 conflicto, 422 regla de negocio, 502 proveedor con respuesta inválida y 503 proveedor no disponible; incluir además error de red, cuerpo no JSON y respuesta vacía para el cliente HTTP. No todos estos códigos aplican a todas las operaciones. Un error del Gateway puede tener otro cuerpo: no asumir ApiError universal. No mostrar tokens, contraseñas ni cuerpos sensibles en logs o pantallas de error.

Los mocks deben usar datos ficticios, UUID distintos para identidad/dominio y relaciones coherentes; casos de lista vacía, carga lenta, identidad sin asociación y acciones ajenas. No incluir JWT reales. Preparar estados de UI «sin sesión», «cargando», «vacío», «error», «confirmado» y «resultado incierto». Desactivar doble envío mejora UX, pero no sustituye idempotencia del servidor.

La configuración común declara TTL de 15 minutos y tolerancia de reloj de 60 segundos; la evidencia de #67 confirmó el borde antes/después del skew con reloj controlado; eso no completa la aceptación frontend. El ADR adoptado mantiene JWT en memoria y recupera acceso al recargar mediante cookie de renovación segura. La UI termina la sesión al `exp`; el margen del servidor no es una ampliación deliberada de sesión. No existe revocación automática del Bearer copiado al cerrar sesión local. No cambiar el reloj del host para probar vencimiento.

RabbitMQ y el endpoint asíncrono todavía no forman parte de esta base: sus issues comienzan después de G1. No simular respuestas 202 como si el flujo ya estuviera implementado.

## Rutas de evidencia en cada snapshot

- `shipment-service`: `src/main/java/com/logitrack_atom/shipment_service/controller/{EnvioController,AsociacionUsuarioController}.java`, `dto/{request,response}/`, `config/SecurityConfig.java`, `security/ShipmentAccess.java` y `enums/EstadoEnvio.java`.
- `courier-service`: `src/main/java/com/logitrack_atom/courier_service/controller/{RepartidorController,ConfiguracionController}.java`, `dto/{request,response}/`, `config/SecurityConfig.java`, `security/RepartidorAccess.java` y `service/impl/RepartidorServiceImpl.java`.
- `security-service`: `src/main/java/com/logitrack_atom/security_service/controller/{AuthController,UsuarioController}.java`, `dto/{request,response}/` y `config/SecurityConfig.java`.
- `configserver-ms`: `api-gateway.yml` para rutas, JWT y reescritura del header Location. La configuración efectiva depende del perfil y label; no se infiere de los fixtures. El label probado se registra en integracion-verificada.json.

## ADR-001 · Sesión renovable y red del frontend (adoptado)

**Estado: adoptado por decisión del equipo del 10/11/2026 y comprobado técnicamente.** La consigna exige almacenamiento seguro y persistencia del contexto JWT. Se adopta recuperación tras recarga, cubriendo la interpretación más exigente; no se inventa una aprobación individual del profesor ni se convierte esa aprobación en un trámite adicional no exigido por el texto.

1. JWT de acceso solo en memoria. Security mantiene renovación opaca en cookie `__Host-logitrack_refresh`, HttpOnly/Secure/SameSite=Strict/Path=/, sin Domain. React no puede leerla. El único dato en Web Storage es un marcador sin credenciales para logout pendiente.
2. Login/refresh/logout del navegador requieren Origin exacto y `X-LogiTrack-Session: 1`. Usan el proxy del mismo origen; el negocio conserva Bearer y credentials omit. HTTPS en despliegue; localhost HTTP solo como excepción compatible de pruebas, sin quitar Secure.
3. Recarga restaura una vez, rota cookie y confirma me antes de habilitar la UI. El servidor guarda solo hashes, usa bloqueo MySQL por familia y revoca por reutilización; TTL absoluto 8h por defecto, máximo 24h. El JWT nunca supera su TTL normal ni el límite de familia.
4. Logout limpia datos, aborta/desestima generaciones viejas y revoca renovación. Las pestañas se coordinan mediante Web Locks cuando están disponibles y storage para cierre local. Un logout de red no confirmado no se presenta como revocación exitosa. Un Bearer copiado sigue sujeto a exp: no hay promesa de revocación inmediata.
5. Exp de UI y 401/403 protegidos conservan cierre de sesión; no se suma el skew ni se reintenta negocio. No se introduce renovación automática después del rechazo de una operación.
6. La red verificada es navegador→Nginx→Gateway→servicios. Host público/Kubernetes son mejoras opcionales; el núcleo obligatorio funciona con Compose local. Actualizar tres imágenes y aplicar SQL aditivo antes del despliegue; no mezclar frontend nuevo con las imágenes Security/Gateway Sprint03.

Implementación y pruebas: [Security PR #12](https://github.com/LogiTrack-Atom/security-service/pull/12), [Gateway PR #10](https://github.com/LogiTrack-Atom/api-gateway/pull/10), [frontend PR #22](https://github.com/LogiTrack-Atom/logitrack-frontend/pull/22) y [Compose PR #90](https://github.com/LogiTrack-Atom/shipment-service/pull/90). Ver BROWSER_SESSION.md de Security, docs/SESION_RENOVABLE.md del frontend e integracion-verificada.json. No se afirma inmunidad ante XSS.

## Traspaso y validación inicial — 09/11/2026

Gab puede mapear los escenarios a su adaptador mock, rotular ese modo y mantener la aplicación sin llamadas reales. Los cuerpos usan UUID de usuario distintos de cliente/repartidor y datos example.com. No hay token, password ni datos del equipo. Los escenarios son independientes; no simulan persistencia, filtros de autorización reales, latencia por sí solos ni efectos transaccionales. El de envío ajeno declara que el recurso existe fuera de la vista del actor: no confundir 403 con 404 ni inferir que el catálogo devuelve ese recurso al usuario.

Se validaron 25 definiciones, 24 payloads tipados de 25 escenarios y relaciones críticas (asignación sin transición/evento, secuencia de tres estados, identidad distinta de dominio). Los otros escenarios representan transporte, cuerpos sin contrato o respuestas sin cuerpo. Se verificó que estado inexistente y peso cero sean rechazados por el esquema. Cero errores con jsonschema Draft202012Validator y FormatChecker; decimales cargados como Decimal. No se ejecutó frontend, navegador de aceptación, backend ni suites nuevas de integración.

Reproducción de la validación de payloads, con Python y jsonschema disponibles:

```python
import json
from decimal import Decimal
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

base = Path('docs/sprint04/contratos-frontend')
schema = json.loads((base / 'modelos.schema.json').read_text(), parse_float=Decimal)
fixtures = json.loads((base / 'escenarios.mock.json').read_text(), parse_float=Decimal)
Draft202012Validator.check_schema(schema)
for case in fixtures['escenarios']:
    for field, key in [('body', 'responseModel'), ('requestBody', 'requestModel'), ('historial', None)]:
        model = 'HistorialResponse' if field == 'historial' and field in case else case.get(key)
        if model:
            Draft202012Validator({'$ref': '#/$defs/' + model, '$defs': schema['$defs']},
                                format_checker=FormatChecker()).validate(case[field])
```

La conciliación técnica y el ADR de renovación están implementados y probados; el cierre de #71 se completa al integrar las PR coordinadas. La aceptación transversal restante continúa en frontend#9 y el release con digests en #73, sin auditoría como bloqueo.
