# Contratos provisionales para los mocks de React

Fuente: los SHA del [manifiesto](manifiesto.json), controladores, DTO, reglas de seguridad y rutas de `configserver-ms/api-gateway.yml`. **Extracción estática para #66; no es el contrato final de #71 ni una prueba de integración.** CORS, disponibilidad del hosting, persistencia de sesión y tolerancia a fallos se validan en sus issues. No agregar endpoints que no existen para facilitar la pantalla.

## Acceso y prefijos

La URL base del frontend será configurable. Usar siempre el Gateway para el negocio; los prefijos siguientes ya incluyen `/api/v1` y `StripPrefix=2` elimina `/applications/<servicio>` al reenviar:

| Alias usado abajo | Prefijo público |
|---|---|
| S | `/applications/apishipment/api/v1` |
| C | `/applications/apicourier/api/v1` |
| I | `/applications/apisecurity/api/v1` |

Enviar `Authorization: Bearer <token>` en operaciones protegidas. Los roles son `ADMIN`, `CLIENTE`, `REPARTIDOR`. Los UUID de usuario (`sub`) y de dominio (`cliente.id`, `repartidor.id`) son identidades distintas. Ocultar acciones por rol es presentación; el backend conserva la autorización y la pertenencia. Los mocks deben simular ambas.

## Operaciones existentes

**A** = ADMIN, **Cl** = CLIENTE, **R** = REPARTIDOR. Los roles de la tabla no sustituyen la comprobación de propiedad del recurso en los servicios. Éxito y respuestas reflejan el código inspeccionado; los errores concretos se contrastarán con pruebas en #67/#71.

| Método | Ruta después del alias | Acceso | Solicitud | Éxito / respuesta |
|---|---|---|---|---|
| POST | I `/auth/register` | Público | `{email,password}` | 201 Usuario |
| POST | I `/auth/login` | Público | `{email,password}` | 200 Token |
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

No hay listado general de usuarios, listado general de clientes, actualización/baja de usuarios, borrado de envíos, refresh ni logout remoto en estos controladores. #S04-09 debe diseñar el flujo administrativo alrededor de las operaciones disponibles. Las comprobaciones adicionales de gateway, esquema y seguridad se validarán antes de integrar.

## Modelos y validaciones

| Modelo | Campos JSON |
|---|---|
| Token | `accessToken: string`, `tokenType: string` (Bearer), `expiresIn: number` (segundos) |
| Usuario | `id: UUID`, `email: string`, `roles: string[]`, `createdAt`, `updatedAt` |
| Cliente | `id: UUID`, `nombre: string`, `email: string`, `telefono: string|null` |
| Dirección | `calle`, `numero`, `ciudad`, `codigoPostal` como strings |
| Envio | `id`, `codigoSeguimiento`, `cliente: Cliente`, `repartidorId: UUID|null`, `pesoKg`, `anchoCm`, `altoCm`, `largoCm`, `estado`, `origen: Dirección`, `destino: Dirección`, `createdAt`, `updatedAt` |
| Historial | `id: UUID`, `estadoAnterior: EstadoEnvio|null`, `estadoNuevo: EstadoEnvio`, `fechaCambio` |
| Repartidor | `id: UUID`, `nombre`, `tipoVehiculo`, `estado: ACTIVO|INACTIVO`, `createdAt`, `updatedAt` |
| Disponibilidad | `repartidorId: UUID`, `estado: ACTIVO|INACTIVO`, `disponible: boolean` |
| IdentidadRepartidor | `repartidorId: UUID`, `usuarioId: UUID|null` |
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

La configuración común declara TTL de 15 minutos y tolerancia de reloj de 60 segundos; comprobar configuración efectiva y comportamiento en #67/#71. La propuesta es JWT en memoria y volver al login al recargar, pendiente de confirmar el requisito del profesor sobre persistencia en #S04-04. La UI termina la sesión al `exp`; el margen del servidor no es una ampliación deliberada de sesión. No existe revocación automática del Bearer copiado al cerrar sesión local. No cambiar el reloj del host para probar vencimiento.

RabbitMQ y el endpoint asíncrono todavía no forman parte de esta base: sus issues comienzan después de G1. No simular respuestas 202 como si el flujo ya estuviera implementado.

## Rutas de evidencia en cada snapshot

- `shipment-service`: `src/main/java/com/logitrack_atom/shipment_service/controller/{EnvioController,AsociacionUsuarioController}.java`, `dto/{request,response}/`, `config/SecurityConfig.java`, `security/ShipmentAccess.java` y `enums/EstadoEnvio.java`.
- `courier-service`: `src/main/java/com/logitrack_atom/courier_service/controller/{RepartidorController,ConfiguracionController}.java`, `dto/{request,response}/`, `config/SecurityConfig.java`, `security/RepartidorAccess.java` y `service/impl/RepartidorServiceImpl.java`.
- `security-service`: `src/main/java/com/logitrack_atom/security_service/controller/{AuthController,UsuarioController}.java`, `dto/{request,response}/` y `config/SecurityConfig.java`.
- `configserver-ms`: `api-gateway.yml` para rutas, JWT y reescritura del header Location. La aplicación remota efectiva depende del perfil y label, pendientes de revalidación.
