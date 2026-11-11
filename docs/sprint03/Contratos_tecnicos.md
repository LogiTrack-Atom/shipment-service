# Contratos técnicos de Sprint03

Estado: acuerdos de implementación iniciales. Fecha: 2 de noviembre de 2026.
La integración pendiente se registra en shipment-service#39. Este documento describe
la arquitectura objetivo; no afirma que los componentes pendientes ya funcionen juntos.

## Base, versiones y repositorios

Se parte de develop y de los commits de [base_verificada.json](base_verificada.json).
La entrega histórica sprint02-1.0.0 se conserva inmutable. El label de configuración
de esa entrega apunta a b40bd4d0eecd5eff6e5ef0a7b23d0a106a39ba13; una entrega Sprint03
debe usar una revisión nueva que contenga las propiedades incorporadas.

Se mantienen Java 25, Spring Boot 4.1.1 y el BOM Spring Cloud 2025.1.4.
Se conserva el verificador de compatibilidad. Gateway usa WebFlux/Netty con
spring-cloud-starter-gateway-server-webflux; los servicios de negocio conservan MVC.

| Repositorio | Función | Puerto interno de referencia |
| --- | --- | --- |
| configserver | Aplicación Config Server, sin base propia | 8085 |
| configserver-ms | Archivos Git de configuración, sin aplicación ni imagen | No aplica |
| eureka-server | Registro y descubrimiento | 8065 |
| api-gateway | Entrada de negocio, rutas y seguridad perimetral | 8066 |
| shipment-service | Envíos, clientes, asignación, estados e historial | 8080 |
| courier-service | Repartidores y disponibilidad | 8081 |
| security-service | Cuentas, roles y emisión de JWT | 8082 |

## Rutas públicas y descubrimiento

| Prefijo público | Destino | Transformación |
| --- | --- | --- |
| /applications/apishipment/** | lb://SHIPMENT-SERVICE | Retirar los dos segmentos iniciales |
| /applications/apicourier/** | lb://COURIER-SERVICE | Retirar los dos segmentos iniciales |
| /applications/apisecurity/** | lb://SECURITY-SERVICE | Retirar los dos segmentos iniciales |

Ejemplo: /applications/apishipment/api/v1/envios conserva /api/v1/envios al llegar
al controller. Se incluyen clientes y asociaciones del lado shipment, usuarios y auth
del lado security, y repartidores y diagnóstico ADMIN del lado courier. Query strings,
métodos y respuestas deben conservar su semántica; probar Location de respuestas 201.

Se definen rutas explícitas bajo spring.cloud.gateway.server.webflux.routes.
No se habilita publicación automática de todos los servicios del registro.
Eureka registra shipment-service, courier-service, security-service y api-gateway.
El servidor autónomo no se autorregistra ni necesita consultar su propio registro.

defaultZone conserva camelCase. Entre contenedores el destino es
http://eureka-server:8065/eureka/; localhost se reserva para ejecución local y sondas.
Eureka provee descubrimiento; LoadBalancer selecciona instancia y el cliente llama
al destino. El tráfico de negocio no atraviesa Eureka.

## Seguridad

Se mantiene el contrato RS256 de Sprint02: sub UUID de usuario, roles como lista,
iss logitrack-security, aud logitrack-api, iat y exp. Se conservan TTL de 900 segundos
y tolerancia de 60 segundos. Validar firma, algoritmo, issuer, audience y tiempos.
La privada permanece exclusivamente en security; Gateway y los servicios verifican
con la pública externa. No se añade introspección HTTP en cada petición.

Spring Security reactivo realiza la validación en Gateway. Los filtros transversales
gestionan el paso de cabeceras sin aceptar claims sin verificación ni registrar JWT.
Los servicios mantienen la validación y la pertenencia a recursos.

| Operación | Permiso en el perímetro |
| --- | --- |
| POST auth/login y auth/register | Público; register crea exclusivamente CLIENTE |
| GET auth/me | Usuario autenticado con rol conocido |
| Administración de usuarios/asociaciones/repartidores | ADMIN |
| POST envios | ADMIN o CLIENTE |
| Lectura de envíos, tracking e historial | ADMIN, CLIENTE o REPARTIDOR; pertenencia en shipment |
| PUT asignación y PATCH cancelación | ADMIN |
| PATCH estado de envío | ADMIN o REPARTIDOR; pertenencia y transición en shipment |
| Consulta individual de repartidor/disponibilidad | ADMIN o REPARTIDOR; pertenencia en courier |
| GET /api/v1/admin/configuracion de courier | ADMIN |

Denegar rutas no declaradas. Mantener errores ApiError en Gateway y servicios.
CORS es deseable y se centraliza si se incorpora, con orígenes y métodos explícitos.
Las APIs de Config/Eureka/Actuator son de administración y diagnóstico; no se convierten
en rutas públicas de negocio por la existencia de un prefijo o de un registro.

## OpenFeign y reglas preservadas

Shipment conserva CourierClient como frontera y reemplaza su transporte RestClient por
OpenFeign. El destino lógico es courier-service; no fijar url en @FeignClient ni en
las propiedades operativas del cliente. Incluir OpenFeign, Eureka y LoadBalancer.

Se consume GET /api/v1/repartidores/{id}/disponibilidad. DTO: repartidorId UUID,
estado ACTIVO/INACTIVO y disponible boolean. Comprobar UUID solicitado y coherencia.
Reenviar únicamente el Bearer validado y autorizado a courier. No seguir redirecciones
que puedan enviar credenciales a otro origen. Mantener límites finitos de espera.

La asignación sigue siendo PUT /api/v1/envios/{id}/repartidor con repartidorId.
Solo se admite en PENDIENTE. Consultar disponibilidad antes de persistir y nuevamente
antes de EN_RUTA. Asignar/reasignar no cambia estado ni crea un evento de historial
de estados. Las lecturas no necesitan enriquecer envíos con llamadas a courier.

Disponible significa estado ACTIVO. No implica reserva ni exclusividad del repartidor.
No se introduce una transacción distribuida, acceso SQL remoto ni relación JPA entre bases.
Las consultas administrativas de identidad existentes conservan sus contratos; la
migración obligatoria a Feign de esta etapa corresponde a disponibilidad shipment→courier.

## Errores y consistencia

| Resultado | Respuesta de shipment |
| --- | --- |
| 200 coherente y disponible true | Continuar con validación y escritura local |
| 200 y disponible false | 422 sin cambios |
| 404 remoto | 404 sin cambios |
| Timeout, conexión rechazada, sin instancia, 429 o 5xx remoto | 503 sin cambios |
| Contrato inválido o 401/403 remoto inesperado tras autorización local | 502 sin cambios |
| Sin credencial local válida | 401 antes de la consulta remota |
| Rol o pertenencia local insuficiente | 403 antes de la operación |

ErrorDecoder trata respuestas HTTP. Las fallas sin respuesta, como timeout, se
traducen en el adaptador de transporte. Fallback o ErrorDecoder satisfacen la opción
de la consigna; nunca devolver disponibilidad positiva si no pudo comprobarse.
Definir explícitamente reintentos y evitar multiplicarlos entre cliente y balanceador.

ApiError mantiene timestamp, status, error, mensaje y detalles. No expone JWT, SQL,
stack traces ni cuerpos internos. Los rechazos preservan repartidorId, estado e historial.

## Configuración, arranque y datos

Cada cliente conoce al arrancar nombre, perfil, URL de Config Server y CONFIG_LABEL.
Dev/prod requieren importación remota; local/test son modalidades explícitas.
Los archivos base y dev/prod coinciden con spring.application.name. Conservar marcadores
logitrack.config.marker para verificar origen y precedencia. El diagnóstico ADMIN
solo devuelve aplicación, perfil y marcador; no devuelve Environment completo.

El orden de aplicación es Config Server → Eureka → dominio → Gateway. Las tres bases
MySQL tienen usuario, esquema y volumen propios. Los healthchecks y depends_on ordenan
el arranque; las fallas posteriores se tratan mediante timeouts y errores de negocio.

El Compose general permanece en shipment-service. La entrada externa de negocio es
Gateway; los puertos de diagnóstico locales se habilitan deliberadamente. Las imágenes
y configuración de entrega tienen referencias explícitas. Construir/publicar componentes
modificados; Config Server puede conservar su imagen si su aplicación no cambia.

La demo usa un entorno aislado con semilla/aprovisionamiento y restablecimiento explícito.
El primer ADMIN recibe credenciales externas. Coordinar identidades de las tres bases.
Dev/prod conservan persistencia; no ejecutar borrado automático ni down -v sobre datos
del equipo. data.sql por sí solo no representa un mecanismo de restablecimiento.

## Aceptación y alcance pendiente

Ejecutar regresión inicial y final, cuatro clientes Eureka registrados, rutas por lb://,
login/registro por Gateway, JWT y roles, pertenencia, asignación con Feign, 404/inactivo,
500/timeout/caída sin mutación y recuperación. Recrear conservando volúmenes y comprobar
las tres bases. Registrar commits y ejecuciones; los fixtures no acreditan integración real.

La demostración recorre alta de envío → asignación → EN_RUTA → ENTREGADO → historial,
además de rechazos. Debe poder explicarla cualquier integrante y mostrar deuda corregida.
Tarifas aparece como responsabilidad en una tabla docente, sin fórmula ni contrato:
requiere aclaración académica antes de abrir implementación. Kubernetes/GCP/tracing
no se agregan al alcance obligatorio de esta entrega.
