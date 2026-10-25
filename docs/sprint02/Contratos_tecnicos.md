# Contratos técnicos del Sprint 02

Estado: propuesta de implementación para revisión del equipo. Fecha: 25 de octubre de 2026.

Este documento fija los acuerdos compartidos antes de implementar los servicios. Sus ejemplos describen el comportamiento objetivo; no representan endpoints ni pruebas ya ejecutados. Los cambios a estos contratos se revisan mediante pull request hacia `develop`, actualizando las issues afectadas.

## 1. Alcance, versiones y estructura

La base de `shipment-service` es el commit `899311e39ec8dd270f1e50d8289e35129b713799`, identificado por `sprint01-baseline`. Se conservan como antecedentes su código, SQL, pruebas y documentación. La refactorización mantiene las reglas de envíos y la auditoría. No incluye API Gateway, Eureka, mensajería, reservas distribuidas ni refresh tokens.

| Repositorio | Responsabilidad | Responsable |
| --- | --- | --- |
| shipment-service | Envíos, clientes, direcciones, asignaciones, estados, historial e integración general | MaxDZ-10 |
| courier-service | Repartidores, estado operativo y disponibilidad | JMatiasF |
| security-service | Usuarios, contraseñas, roles y emisión de JWT | MaxDZ-10 |
| configserver | Aplicación Spring Cloud Config Server | DiazGab |
| configserver-ms | Configuraciones por aplicación y ambiente | DiazGab |

Java 25 y Spring Boot 4.1.1 continúan como base. Se fija `org.springframework.cloud:spring-cloud-dependencies:2025.1.4` como BOM común de Config Server y los clientes. La matriz oficial incluye Boot 4.1.x desde Cloud 2025.1.2; la combinación exacta se verificará al construir y arrancar cada aplicación, sin desactivar el verificador de compatibilidad. No se actualiza Boot incidentalmente durante la separación.

Se utilizan capas controller, service, repository, DTO, mapper y exception, inyección por constructor y transacciones en el servicio. El sistema bancario es referencia de organización y pruebas; el proyecto hotelero, de configuración centralizada. Se adaptan ambos a los contratos existentes de LogiTrack. Los controladores no exponen entidades JPA.

## 2. Datos e identificadores

Los identificadores del dominio y de usuarios son UUID, serializados como cadenas UUID en JSON. Cada servicio de negocio tiene una base MySQL y un usuario de aplicación exclusivo: `shipment_db`/`shipment_app`, `courier_db`/`courier_app`, `security_db`/`security_app`. Cada usuario accede únicamente a su base. La aplicación no utiliza root.

No hay entidades compartidas, claves foráneas entre bases ni consultas SQL de un servicio a la base de otro. `Cliente`, `Direccion`, `Envio` e `HistorialEstadoEnvio` permanecen en shipment. `Repartidor` pasa a courier. Shipment conserva `repartidorId` como UUID nullable y elimina la relación JPA al repartidor. Las FK dentro de la misma base se mantienen cuando corresponda.

La migración preserva UUID, nombre, vehículo, estado y fechas de los repartidores; conserva los UUID de las asignaciones, los envíos, clientes, tracking e historial. Se documentan copia de seguridad, exportación/importación, verificación de recuentos y referencias, y reversión. No se elimina la tabla de origen antes de verificar la copia y las referencias. El procedimiento es repetible y no duplica registros. El esquema se administra con scripts versionados y validación JPA; no se usa `ddl-auto=create` sobre datos existentes.

## 3. Identidad y pertenencia

`sub` identifica al usuario de security-service. No es automáticamente el UUID del cliente ni del repartidor. No se deduce pertenencia de un email recibido en el cuerpo, de un parámetro `usuarioId` del solicitante ni de conocer un tracking.

Se adopta una asociación local: `Cliente.usuarioId` en shipment y `Repartidor.usuarioId` en courier, ambos UUID únicos cuando tienen valor, sin FK a security. Los registros migrados pueden quedar sin usuario asociado; esto no habilita su acceso a cualquier cuenta.

- Para CLIENTE, shipment busca el cliente por el `sub` validado. En el primer registro puede crear el cliente y asociarlo al usuario autenticado. Si el email ya pertenece a un cliente histórico sin asociación, se devuelve 409 y se requiere vinculación administrativa; no se reclama ese cliente automáticamente por email. Para un cliente ya asociado, el cuerpo no permite cambiar de identidad ni sobrescribir los datos de otro cliente.
- ADMIN vincula explícitamente una cuenta existente mediante `PUT /api/v1/clientes/{id}/usuario` en shipment o `PUT /api/v1/repartidores/{id}/usuario` en courier, con cuerpo `{"usuarioId":"<UUID>"}`. La operación comprueba existencia y rol compatible de la cuenta mediante la consulta administrativa a security antes de guardar; no consulta security en cada petición de negocio. Una cuenta no se vincula a dos clientes o a dos repartidores. Una asociación existente a otro usuario no se reemplaza silenciosamente: 409.
- Las respuestas que no son administrativas no exponen asociaciones ni datos de otros usuarios innecesariamente.

## 4. API de repartidores

Se conserva `/api/v1/repartidores` y el vocabulario español del Sprint 01. `RepartidorRequest` contiene `nombre` y `tipoVehiculo`, ambos obligatorios y no vacíos. La respuesta de dominio conserva `id`, `nombre`, `tipoVehiculo`, `estado`, `createdAt` y `updatedAt`. Los cuerpos inválidos o UUID malformados devuelven 400; un UUID inexistente, 404.

| Método y ruta | Comportamiento | Acceso |
| --- | --- | --- |
| POST /api/v1/repartidores | Registrar ACTIVO; 201 y Location | ADMIN |
| GET /api/v1/repartidores?estado=ACTIVO | Lista; filtro opcional; sin coincidencias `[]` | ADMIN |
| GET /api/v1/repartidores/{id} | Consultar por UUID; 200 | ADMIN o REPARTIDOR asociado a ese registro |
| PUT /api/v1/repartidores/{id} | Actualizar nombre y tipoVehiculo; no cambia id, usuario ni estado; 200 | ADMIN |
| PATCH /api/v1/repartidores/{id}/estado | Cuerpo `{"estado":"ACTIVO"}` o INACTIVO; repetir el valor es válido; 200 | ADMIN |
| GET /api/v1/repartidores/{id}/disponibilidad | Consulta sin efectos; respuesta indicada debajo | ADMIN o REPARTIDOR asociado a ese registro |
| PUT /api/v1/repartidores/{id}/usuario | Asociación administrativa de identidad; 200 | ADMIN |

Respuesta de disponibilidad:

```json
{
  "repartidorId": "b4a52c50-b363-4b95-b365-58302f12cd91",
  "estado": "ACTIVO",
  "disponible": true
}
```

En este sprint, `disponible` significa `estado == ACTIVO`, conservando la regla del Sprint 01. No implica reserva, exclusividad ni cálculo de carga. Un repartidor INACTIVO responde 200 con `disponible:false`; no se confunde con un 404. Courier no necesita llamar a shipment para responder.

## 5. API de envíos e integración REST

Se conservan los endpoints de `/api/v1/envios`, incluyendo seguimiento, historial, cancelación, asignación y cambios de estado. Se mantienen precisión de medidas, tracking único, reutilización de clientes autorizada, estados terminales y atomicidad entre cambio de estado e historial.

La asignación sigue siendo `PUT /api/v1/envios/{id}/repartidor` con `{"repartidorId":"<UUID>"}`. Solo se admite para un envío PENDIENTE y un repartidor disponible. Asignar o reemplazar un repartidor no cambia el estado ni agrega historial. Se vuelve a consultar disponibilidad al pasar a EN_RUTA. Desactivar un repartidor no modifica automáticamente sus envíos históricos.

Se adopta un cambio explícito de respuesta: `EnvioResponse.repartidor` anidado se reemplaza por `repartidorId`, nullable. Las lecturas de envíos no enriquecen datos mediante llamadas a courier, de modo que siguen funcionando si courier cae. Se actualizan OpenAPI, Postman y las pruebas afectadas; no se conserva un objeto de repartidor ficticio o desactualizado. Este cambio de JSON debe quedar destacado en las notas del Sprint 02.

Shipment utiliza un `CourierClient` con `RestClient`, URL configurable y timeouts iniciales de conexión de 2 segundos y lectura de 3 segundos. Reenvía el Bearer del solicitante exclusivamente al servicio configurado; no registra el token. La consulta es de solo lectura y no abre una transacción distribuida. Una transacción local no garantiza que el estado remoto permanezca igual después de consultar; esa limitación se documenta. No se reintentan escrituras automáticamente.

| Resultado remoto durante asignación o paso a EN_RUTA | Resultado de shipment |
| --- | --- |
| 200, disponible true y respuesta válida/coherente | Continuar con la regla local |
| 200, disponible false | 422 de negocio, sin cambios parciales |
| 404 del repartidor | 404, sin asignación ni evento |
| Conexión rechazada, timeout, 429 o 5xx remoto | 503, sin persistir cambios |
| JSON inválido/inconsistente o 401/403 inesperado del servicio remoto tras autorizar localmente | 502, sin propagar detalles internos |

La autorización local se evalúa antes de invocar courier. Los errores de entrada del usuario siguen siendo 400; no se pasan al usuario como errores de upstream. En los casos fallidos se conserva el estado y el historial de la base local.

## 6. Autenticación y autorización

Security ofrece `POST /api/v1/auth/register` (201, registro CLIENTE), `POST /api/v1/auth/login` (200) y `GET /api/v1/auth/me` (200, autenticado). Register recibe email y password; no permite elegir ADMIN ni REPARTIDOR, ni asociarse un UUID de dominio. El email es único y se normaliza de manera consistente. Una cuenta existente produce 409; credenciales incorrectas producen 401 con mensaje genérico. Se usa `PasswordEncoder` de Spring Security con hash adaptativo y pruebas de coincidencia; nunca se devuelve el hash.

ADMIN administra cuentas mediante `POST /api/v1/usuarios` y consulta `GET /api/v1/usuarios/{id}` para las vinculaciones. El alta administrativa admite un rol de ADMIN, CLIENTE o REPARTIDOR, email y password. La primera cuenta ADMIN se prepara mediante un procedimiento idempotente con credenciales suministradas fuera de Git, sin contraseña compartida o embebida. No se requiere un servidor OAuth/OIDC completo para este sprint.

Se utiliza Bearer JWT con RS256. La clave RSA privada solo está disponible en security mediante archivo montado de forma segura; shipment y courier disponen de la clave pública y validan localmente firma, algoritmo permitido, `iss`, `aud`, `exp` y `nbf` cuando corresponda. No se utiliza discovery ni introspección HTTP para validar cada token. Si se configura `issuer-uri`, no se presupone que security expone discovery: se configura explícitamente el decoder con la clave pública y los validadores.

Claims mínimos: `sub` (UUID de usuario), `roles` (lista con ADMIN, CLIENTE o REPARTIDOR), `iss` (`logitrack-security`), `aud` (`logitrack-api`), `iat` y `exp`. Vida inicial: 900 segundos; tolerancia de reloj: 60 segundos. Las autoridades se convierten explícitamente a `ROLE_...`. La respuesta de login contiene `accessToken`, `tokenType: "Bearer"` y `expiresIn: 900`. Las contraseñas, claves privadas y tokens no se incorporan a logs, imágenes o Git. Los cambios administrativos de rol se reflejan al emitir un token nuevo; el acceso de un token existente vence con su expiración.

| Operación | ADMIN | CLIENTE | REPARTIDOR |
| --- | --- | --- | --- |
| Registrar envío | Sí | Sí, para su cliente asociado | No |
| Listar/consultar envíos, tracking e historial | Todos | Solo propios | Solo asignados al repartidor asociado |
| Asignar/reasignar repartidor | Sí | No | No |
| Cambiar estado | Transiciones válidas | No | Solo su envío: PENDIENTE a EN_RUTA o EN_RUTA a ENTREGADO |
| Cancelar | Solo PENDIENTE | No | No |
| Administrar repartidores/usuarios/asociaciones | Sí | No | No |

Las consultas de listado se filtran por pertenencia en el servicio/repositorio. Las consultas individuales de recursos ajenos se rechazan con 403, sin devolver sus datos. Un token ausente, inválido o vencido devuelve 401. Los casos de pertenencia se prueban también sobre tracking, historial y modificaciones, evitando que una ruta alternativa omita el control. Denegación por defecto para rutas de negocio no declaradas. Se utiliza configuración stateless y se desactiva CSRF solo en la API Bearer sin autenticación por cookies.

## 7. Errores y observabilidad

Los servicios mantienen el formato actual `ApiError`: `timestamp` (Instant UTC), `status` (entero HTTP), `error` (descripción HTTP), `mensaje` y `detalles` (lista de cadenas, vacía cuando no aplica). Los handlers de 401 y 403 de Spring Security también respetan ese formato. Se conservan 400, 404, 409 y 422 según su significado; se incorporan 401, 403, 502 y 503 para los casos definidos. Nunca se incluyen stack traces, SQL ni credenciales en las respuestas.

Se registran eventos relevantes de negocio y fallas remotas con identificadores, sin cuerpos con datos sensibles. OpenAPI documenta Bearer, roles, pertenencia y errores de cada operación. Actuator expone únicamente la salud necesaria; no se publican `/actuator/env`, `/configprops` ni secretos. La prueba de consumo de configuración usa una propiedad no sensible observada desde un bean y una prueba de integración; no requiere un endpoint público de diagnóstico permanente.

## 8. Configuración por ambiente

| Aplicación / nombre de Compose | Puerto | Base | Puerto MySQL local orientativo |
| --- | --- | --- | --- |
| shipment-service | 8080 | shipment_db | 3307 |
| courier-service | 8081 | courier_db | 3308 |
| security-service | 8082 | security_db | 3309 |
| configserver | 8085 | No utiliza base propia | No aplica |

Nombres de contenedores MySQL en la red: `shipment-mysql`, `courier-mysql`, `security-mysql`, todos con puerto interno 3306. Los puertos publicados son para el entorno local y se pueden ajustar por conflicto; no alteran las URLs entre contenedores. No se usa localhost para comunicar contenedores diferentes.

`configserver-ms` tendrá archivos base y por perfil para cada aplicación: `shipment-service.yml`, `shipment-service-dev.yml`, `shipment-service-prod.yml`, y los equivalentes de courier y security. Los archivos base incluyen el nombre canónico y propiedades comunes. Los perfiles contienen diferencias reales y una propiedad demostrativa `logitrack.config.marker` con valor distinguible por aplicación/ambiente.

El nombre de aplicación, la selección de perfil y la ubicación de Config Server deben conocerse antes de pedir la configuración. Por eso el arranque local conserva `spring.application.name`, perfil seleccionado por entorno y `spring.config.import`; el nombre también se registra en la configuración remota, sin cambiarlo de un ambiente a otro.

| Propiedad | Convención |
| --- | --- |
| spring.application.name | shipment-service, courier-service o security-service |
| spring.profiles.active | dev o prod, seleccionado al iniciar |
| spring.config.import | `configserver:${CONFIG_SERVER_URL:http://localhost:8085}`; importación obligatoria para la entrega |
| spring.cloud.config.label | `${CONFIG_LABEL:develop}`; etiqueta inmutable para la entrega reproducible |
| server.port | Valor remoto 8080, 8081 o 8082 según aplicación |
| spring.datasource.url / username / password | `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` en las propiedades remotas; resueltos en el cliente |
| spring.jpa.hibernate.ddl-auto | validate en la entrega con esquema versionado |
| clients.courier.base-url | `${COURIER_SERVICE_URL:http://localhost:8081}` en shipment |
| clients.courier.connect-timeout / read-timeout | 2s / 3s en shipment |
| clients.security.base-url | `${SECURITY_SERVICE_URL:http://localhost:8082}` para las asociaciones administrativas |
| logitrack.security.issuer / audience / access-token-ttl / clock-skew | logitrack-security / logitrack-api / 15m / 60s |
| logitrack.security.public-key-location | `${JWT_PUBLIC_KEY_LOCATION}` en los validadores |
| logitrack.security.private-key-location | `${JWT_PRIVATE_KEY_LOCATION}` solo en security |
| logging.level.root / logging.level.com.logitrack_atom | Remotos y diferenciados por ambiente |
| logitrack.config.marker | Marcador no sensible para verificar consumo real |

Las rutas de claves no contienen claves; los archivos montados se distribuyen fuera del repositorio. Los placeholders de secretos no llevan valores por defecto reales. La respuesta de Config Server debe conservar esos placeholders y las aplicaciones deben resolverlos desde su entorno; se comprueba expresamente que Config Server no sustituya ni publique secretos desde su propio proceso.

Config Server arranca con configuración local propia, `@EnableConfigServer`, Actuator, `server.port=8085`, repositorio `https://github.com/LogiTrack-Atom/configserver-ms.git`, `default-label=develop` y `clone-on-start=true`. No depende de sí mismo para iniciar. Primero se verifican las seis consultas `/{application}/{profile}` de los tres servicios en dev/prod; después se integra cada Config Client y se demuestra consumo real. El perfil de pruebas puede desactivar Config Client y usar configuración aislada; eso no sustituye la prueba de integración remota.

## 9. Docker y entrega

Se requieren cuatro Dockerfiles multietapa con Java 25, versiones explícitas de imágenes base, usuario sin privilegios y contextos sin secretos. El repositorio de configuraciones no es una quinta aplicación ni necesita Dockerfile. Cada componente valida su imagen independientemente.

`shipment-service` aloja el Compose general: cuatro aplicaciones, tres MySQL, volúmenes independientes, comprobaciones de salud y orden de arranque. El Compose de entrega usa cuatro imágenes publicadas en Docker Hub con etiquetas explícitas y no necesita `build` local. Un override de desarrollo puede construir imágenes, sin reemplazar la modalidad de entrega. Las credenciales se suministran fuera de Git mediante variables/archivos montados; `.env.example` solo documenta nombres y ejemplos no sensibles.

Los responsables aportan el Dockerfile y la validación de su componente. MaxDZ-10 coordina etiquetas, publicación y verificación desde un entorno limpio. El namespace de Docker Hub se determina al preparar la entrega; no se publica una URL inventada ni se registra como completado un push no ejecutado. Antes de cerrar se fija una etiqueta Git de configuración y una versión explícita para cada imagen.

## 10. Flujo de trabajo y definición de terminado

Las issues pertenecen a su repositorio y se agregan al Project común. Se utilizan Sprint 02, responsable, prioridad y dependencias nativas de bloqueo, además de referencias completas. La coordinadora enlaza todas las tareas. Una dependencia bloquea el cierre integrado, pero permite preparar código y pruebas con contratos o dobles mientras se completa el proveedor.

Las ramas por tarea parten de `develop`; los PR apuntan a `develop`. Un PR abierto corresponde a En revisión cuando sus verificaciones están listas. Para marcar Hecho se requiere alcance y aceptación completos, pruebas relevantes registradas, documentación actualizada y PR integrado. Como main sigue siendo la rama predeterminada, se verifica y realiza el cierre manual de la issue después de integrar en develop; no se presupone que `Closes` la cierre automáticamente.

No se marcan como cumplidas las pruebas de servicios que todavía no existen. El cierre del sprint requiere regresión adaptada del Sprint 01, pruebas de JWT y pertenencia, migración verificada, errores remotos, consumo real de Config Server, arranque mediante imágenes de Docker Hub, persistencia tras reinicio y evidencia de entrega académica cuando corresponda.

## Fuentes técnicas

- [Compatibilidad de Spring Cloud](https://spring.io/projects/spring-cloud/).
- [Publicación de Spring Cloud 2025.1.4](https://spring.io/blog/2026/10/23/spring-cloud-2025-1-4-has-been-released/).
- [Spring Cloud Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html).
- [Validación JWT con Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

La consigna del Sprint 02 define el alcance obligatorio; las anotaciones y los ejemplos docentes orientan su implementación. Se registra este contrato antes del desarrollo funcional y se revisa con los tres responsables.
