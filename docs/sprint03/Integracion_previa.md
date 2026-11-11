# Integración previa de infraestructura — 03/11/2026

Antecedente fechado: sus bloqueos se corrigieron el 04/11/2026. Consultar
[Aceptacion_final.md](Aceptacion_final.md) para el estado de entrega.

Seguimiento de [#44](https://github.com/LogiTrack-Atom/shipment-service/issues/44)
y [PR #56](https://github.com/LogiTrack-Atom/shipment-service/pull/56).

## Referencias probadas

| Componente | Referencia |
| --- | --- |
| Config Server | `maxdz10/configserver:sprint02-1.0.0`; aplicación sin cambios en Sprint03 |
| Configuración Git | `d32e67296efe2f8ad7d4f82ea08bab3f8e3de58e` |
| Eureka Server | `bafb3b09bb046e1b4cef7530a0a8a42d1a1783c9` de develop |
| API Gateway | `939bf4b196abde1ae54b39a6e65e1cccf3e7ac3b` de develop |
| Docker Engine | 29.8.0 |

Eureka y Gateway se construyeron localmente desde snapshots Git de esos commits,
con sus Dockerfiles y sin modificar sus checkouts. Las imágenes resultantes son
de ensayo local; no representan imágenes publicadas de entrega. La construcción
omite las pruebas Java: este ensayo comprueba infraestructura y no reemplaza sus suites.

El proyecto temporal tiene nombre propio, red independiente y puertos de diagnóstico
dinámicos en loopback. La clave pública se ubicó en `target/`, dentro del workspace
compartido por Docker Desktop, ya que esta instalación no admite binds desde `/tmp`. No incorpora MySQL ni volúmenes del equipo. Gateway recibe
únicamente una clave pública RSA efímera; la privada no se monta en contenedores.

## Resultados de Config Server y Eureka

- Config Server: `/actuator/health` devuelve `UP`.
- Eureka: `/actuator/health` devuelve `UP` tras importar configuración remota.
- `/eureka/apps` devuelve cero aplicaciones; el servidor no se autorregistra.
- Las quince consultas de configuración devuelven la revisión Git fijada.
- Doce combinaciones cumplen los contratos comprobados: default/dev/prod de Eureka,
  Shipment, Courier y Security.
- Las tres combinaciones de Gateway tienen contratos pendientes.

El [resumen sanitizado](evidencias/config_preflight_2026-11-03.json) registra la matriz.
Puede repetirse contra un Config Server disponible con el comando descrito en
[Compose.md](Compose.md#comprobación-previa-de-configuración-remota).

## Contratos pendientes de Gateway

En `api-gateway.yml` de la revisión indicada:

1. Las rutas están bajo `spring.cloud.gateway.routes`; el contrato requiere
   `spring.cloud.gateway.server.webflux.routes`.
2. Los predicados `/auth/**`, `/couriers/**` y `/shipments/**` no coinciden con los
   prefijos `/applications/apisecurity/**`, `/applications/apicourier/**` y
   `/applications/apishipment/**` autorizados por Gateway. Falta `StripPrefix=2`.
3. Falta `logitrack.security.access-token-ttl: 15m`, obligatorio para `JwtProperties`.
4. El puerto predeterminado es 8060 y el acordado es 8066. El Compose suministra
   `SERVER_PORT=8066`, por lo que esta diferencia afecta la configuración de referencia
   y la ejecución independiente, no el puerto forzado por nuestro Compose.

El arranque real de Gateway con esta configuración termina con código 1. Se comprobó
el rechazo de `JwtProperties` por la duración JWT ausente; no se publican logs completos
ni material de claves.

En una segunda fase se suministró `LOGITRACK_SECURITY_ACCESS_TOKEN_TTL=15m`
exclusivamente al contenedor temporal. Gateway quedó saludable (`UP`), pero el POST
a `/applications/apisecurity/api/v1/auth/login` devolvió 404. Con la ruta declarada
y sin instancia del proveedor debería intentar resolverla y devolver 503. Esta
comprobación confirma que suplir el TTL no completa el enrutamiento público. No se
modificó el repositorio de configuración ni se acreditó un login de negocio.

La corrección de rutas corresponde a api-gateway#2 y su configuración compartida.
Esta PR conserva el trabajo de los otros integrantes.

## Ajuste de Compose y aceptación restante

El Compose ahora suministra `EUREKA_URL`, el placeholder consumido por la configuración
integrada. El validador y una prueba de regresión rechazan el modelo anterior que solo
suministraba `EUREKA_DEFAULT_ZONE`.

Eureka todavía no enlaza explícitamente `CONFIG_LABEL` en su configuración de arranque.
El ensayo utiliza el mismo SHA como valor predeterminado del backend de Config Server,
por lo que su arranque no demuestra que seleccione otra revisión mediante esa variable.

Falta completar shipment-service#40, las rutas de api-gateway#2 y las correcciones
de configuración antes de validar las PR #54/#55 contra servicios reales. Luego se
comprueban nueve contenedores saludables, cuatro clientes registrados, JWT/roles,
asignación por Feign, rechazos sin mutación, recuperación y persistencia.

El preflight no certifica consumo efectivo de configuración en todos los clientes.
Tampoco sustituye la evidencia de configserver-ms#14 ni cierra #44 o #47.
