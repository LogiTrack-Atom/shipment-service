# Resiliencia de la consulta de disponibilidad

Implementación de [shipment-service#42](https://github.com/LogiTrack-Atom/shipment-service/issues/42),
integrada mediante PR #55 después de OpenFeign. La evidencia de componentes y
la prueba con Courier/Eureka reales se consolidan en [Aceptacion_final.md](Aceptacion_final.md).

## Clasificación de fallas

| Resultado observado | Tratamiento | HTTP público |
| --- | --- | --- |
| HTTP 404 | ErrorDecoder conserva un 404 sin cuerpo/cabeceras/motivo; el adaptador añade el UUID solicitado | 404 |
| ACTIVO y disponible true coherentes | Se continúa con la operación local | Según operación |
| INACTIVO y disponible false coherentes | Regla de negocio existente | 422 |
| HTTP 429 o 500–599 | CourierHttpErrorDecoder informa indisponibilidad | 503 |
| Conexión rechazada, timeout o interrupción de lectura | El adaptador trata RetryableException como transporte | 503 |
| Ausencia de instancia | LoadBalancer genera HTTP 503 y se aplica ErrorDecoder | 503 |
| JSON, tipos, UUID o estado/disponibilidad incoherentes | Decodificador y adaptador rechazan el contrato | 502 |
| Otros HTTP, incluidos 401/403 y redirecciones | CourierHttpErrorDecoder rechaza la respuesta | 502 |

El ErrorDecoder no lee cuerpos de error, no conserva sus cabeceras ni utiliza
Retry-After para repetir una consulta. Las excepciones públicas no contienen
causas de transporte, mensajes internos, SQL, JWT ni el cuerpo del proveedor.
Un timeout puede ocurrir después de recibir cabeceras 200: continúa siendo una
falla de transporte, sin transformarse en disponibilidad positiva ni en éxito local.

## Límites y consistencia

Se conservan los timeouts validados de `clients.courier`: por defecto 2 s de conexión
y 3 s de lectura, con máximos de 10 s y 30 s respectivamente. Las redirecciones están
desactivadas, Feign utiliza Retryer.NEVER_RETRY y LoadBalancer no reintenta.
Logger.Level.NONE evita registrar intercambios HTTP. No se introduce un fallback que
autorice operaciones; sin disponibilidad confirmada se rechaza la escritura.

Asignación y EN_RUTA consultan antes de persistir. Los rechazos preservan UUID asignado,
estado, createdAt, updatedAt e historial, y conservan el formato ApiError. Una asignación
correcta posterior cambia el repartidor sin crear un evento de estado; EN_RUTA realiza
otra consulta y agrega su evento cuando corresponde.

## Evidencia automatizada

```bash
./mvnw -B -ntp test javadoc:javadoc
./mvnw -B -ntp verify javadoc:javadoc
```

Las pruebas unitarias cubren la matriz HTTP, un cuerpo que falla si se intenta leer,
Retry-After, sanitización y fallas de transporte incluso con estado 200 previo.
CourierDiscoveryTest ejecuta Feign y LoadBalancer con HTTP y descubrimiento controlados,
sin Docker. Sus casos cubren timeout de cabeceras/cuerpo, inexistencia, errores,
recuperación y cambio de destino sin reconstruir el cliente.

CourierFeignIT utiliza MySQL real y JWT firmados. Además de los escenarios existentes,
comprueba:

1. Ausencia de instancias: asignación y EN_RUTA devuelven 503 sin mutaciones. Al anunciar
   una instancia, ambas operaciones posteriores funcionan y el historial es coherente.
2. Proveedor HTTP inicialmente activo: se completa una asignación, se detiene el servidor
   mientras su destino sigue anunciado y se rechazan reasignación/EN_RUTA sin modificar
   la fila. Se inicia otra instancia, se actualiza el destino y las operaciones se recuperan.
3. Los errores de asignación y los escenarios de recuperación conservan ApiError y no
   exponen el Bearer, cuerpos privados ni detalles del transporte.

Las pruebas Java de HTTP controlado se distinguen de la aceptación del Courier de
negocio. La suite completa con MySQL aprobó localmente y en GitHub Actions.

## Aceptación integrada

Con el Courier real detenido, asignación y EN_RUTA devolvieron 503 sin modificar UUID,
estado, fechas ni historial. La recuperación utilizó otra instancia anunciada en Eureka,
con DNS y puerto diferentes, sin reiniciar Shipment. Se completaron entrega, historial,
recreación con persistencia y reset explícito del entorno de demo propio.

Los 500, timeout y contratos malformados se verifican en la suite Java; no se afirma
haber provocado un 500 de negocio artificial en el Courier del proyecto.

Referencia: [configuración y manejo de errores de Spring Cloud OpenFeign 5.0.4](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html).
