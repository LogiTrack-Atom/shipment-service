# Consulta de disponibilidad con OpenFeign

Preparación de [shipment-service#41](https://github.com/LogiTrack-Atom/shipment-service/issues/41).
La integración con Eureka real requiere las issues de registro y configuración pendientes.
Esta preparación no acredita todavía la arquitectura final de Sprint03.

## Flujo

`ShipmentService` conserva `CourierClient` como frontera y utiliza `FeignCourierClient`.
Antes de llamar se mantienen autorización, pertenencia, estado permitido y bloqueo del envío.
El proxy `CourierAvailabilityApi` consulta
`GET /api/v1/repartidores/{id}/disponibilidad` del nombre lógico `courier-service`.
La anotación y las propiedades operativas de Feign no fijan una URL.

OpenFeign 5.0.4 y Feign 13.6.1 se obtienen del BOM Spring Cloud 2025.1.4.
Se incorpora explícitamente LoadBalancer; la dependencia Eureka de shipment corresponde
a #40. Cuando esté integrada, su DiscoveryClient suministrará las instancias de courier
registradas en #17. El tráfico de negocio se dirige a la instancia elegida, sin atravesar
Eureka. La aceptación con descubrimiento controlado demuestra cambio de destino sin
reconstruir el cliente; no equivale a probar el registro real.

## Contrato y seguridad

El DTO conserva solo UUID, estado y disponibilidad. El decodificador exige JSON con
los tres campos y sus tipos exactos, sin duplicados ni contenido adicional, y un cuerpo
máximo de 64 KiB. El adaptador comprueba el UUID solicitado y la coherencia entre
ACTIVO/INACTIVO y disponible. INACTIVO conserva el error de negocio 422.

El Bearer se obtiene del contexto autenticado mediante CourierBearerProvider.
No se copian cabeceras entrantes. La configuración propia del cliente está fuera del
escaneo principal para evitar aplicarla a otros transportes. Fija redirecciones
deshabilitadas, Retryer.NEVER_RETRY, Logger.Level.NONE y los timeouts tipados existentes:
conexión máxima de diez segundos, lectura máxima de treinta, valores predeterminados
2 s y 3 s provenientes de configuración externa. La política local de Feign prevalece
sobre sus propiedades genéricas; LoadBalancer tiene reintentos desactivados explícitamente.
Los errores HTTP se construyen sin leer ni copiar el cuerpo privado del proveedor.

Se preserva la matriz de Sprint02: 404 inexistente, 503 conexión/timeout/ausencia de
instancia/429/5xx y 502 para contrato o HTTP inesperado. La lectura lenta del cuerpo
también se trata como transporte, aunque ya se hayan recibido cabeceras 200.
La issue #42 completa la aceptación específica de resiliencia sobre la integración final.
Su preparación y los escenarios de caída/recuperación se describen en [Resiliencia.md](Resiliencia.md).

`clients.courier.base-url` y COURIER_SERVICE_URL continúan disponibles exclusivamente
para `IdentidadClient`, que verifica asociaciones administrativas mediante REST.
Los timeouts de `clients.courier` se comparten con disponibilidad; su base-url no se usa
para resolver esta consulta Feign. No configurar
`spring.cloud.openfeign.client.config.courierAvailability.url` ni
`spring.cloud.openfeign.client.config.courier-service.url`: omitirían descubrimiento.

## Pruebas y dependencias

```bash
./mvnw -B -ntp test javadoc:javadoc
./mvnw -B -ntp verify javadoc:javadoc
```

El primer comando ejecuta pruebas sin Docker. `CourierDiscoveryTest` inicia Feign y
LoadBalancer reales, con un DiscoveryClient controlado y proveedores HTTP loopback:
destino dinámico, Bearer, ausencia de instancia, recuperación, timeouts y contrato.
La URL administrativa de courier apunta deliberadamente a otro puerto para comprobar
que la disponibilidad no la reutiliza.

El segundo requiere Docker para las pruebas MySQL. `CourierFeignIT` conserva los
escenarios existentes de asignación, reasignación, EN_RUTA, rechazo sin mutaciones,
JWT firmado y concurrencia. Su SimpleDiscoveryClient se configura solo dentro del
fixture; no representa Eureka real. Cuando se incorpore Eureka, aislar ese cliente en
pruebas mediante `eureka.client.enabled=false`, conservando descubrimiento en los
fixtures Feign que lo necesitan.

Antes de integrar y cerrar #41 deben estar disponibles las implementaciones de
eureka-server#1, configserver-ms#13, courier-service#17 y shipment-service#40.
Comprobar entonces asignación y EN_RUTA con courier real anunciado en Eureka, cambiar
su destino anunciado y verificar que shipment continúe consultando sin recompilar.
Actualizar las pruebas de configuración con la revisión nueva del repositorio remoto.
El Compose e imágenes inmutables de Sprint02 se conservan como regresión histórica;
el Compose final de Sprint03 se integra en #44.

Referencias de implementación: [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/)
y [Spring Cloud LoadBalancer](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html).
