# Aceptación técnica de Sprint03 — 04/11/2026

## Resultado

Los escenarios ejecutados aprobaron. Las seis aplicaciones y las tres bases se
integraron con Gateway como entrada de negocio, cuatro clientes Eureka y disponibilidad
por Feign. La grabación, participación de los tres y constancia académica se verifican
por separado; no quedan acreditadas por estas pruebas.

| Verificación | Resultado | Evidencia |
| --- | --- | --- |
| Java + JavaDoc | Shipment 324, Courier 184, Security 182, Gateway 145, Eureka 3, Config Server 1; total 839 | Suites locales por componente y CI de PR vinculadas |
| Python | Demo 23, modelo/configuración 24, sanitización Newman 2; total 49 | Suites correspondientes en scripts/demo, scripts/compose y scripts/verificacion |
| Migración histórica | 19 pruebas aprobadas con MySQL | Workflow de migración de Shipment |
| Arquitectura real | 20 escenarios aprobados | [Aceptación](evidencias/aceptacion_2026-11-04.json) |
| Postman final | 35 pedidos, 61 aserciones, cero fallos | [Postman](evidencias/postman_final_2026-11-04.json) |
| Config Server final | 15 combinaciones default/dev/prod aprobadas | [Configuración](evidencias/configuracion_final_2026-11-04.json) |
| Aislamiento MySQL | 3 accesos propios y 6 rechazos cruzados ERROR 1045 | [Bases](evidencias/aislamiento_mysql_2026-11-04.json) |
| Imágenes públicas | 6 referencias descargadas sin credenciales | [Descarga](evidencias/imagenes_publicas_2026-11-04.json) |
| Contenido de imágenes | Binarios aceptados, publicados y ejecutados coincidentes | [Equivalencia](evidencias/equivalencia_imagenes_2026-11-04.json), [runtime](evidencias/runtime_final_2026-11-04.json) |

Las métricas Java, Python, solicitudes y aserciones son categorías diferentes. No se
suman las ejecuciones repetidas de una misma suite como pruebas nuevas.

## Alcance de las ejecuciones

La aceptación funcional usó configuración `1253290` (SHA completo en su JSON) y las
imágenes candidatas. Probó prepare repetido, perfiles dev/prod, diagnóstico consumido,
nueve contenedores saludables, cuatro registros, Courier detenido con 503 sin mutación,
otra instancia con DNS/puerto distintos sin reiniciar Shipment, entrega/historial,
recreación con persistencia y reset con rotación de claves/credenciales.

La configuración final `a08f95ee8fa0bf412fb30f68776e9a8794f94110` agrega únicamente la
reescritura de Location en las tres rutas. Esa diferencia se verificó con la suite de
Gateway y el Postman final: 17 pedidos y 30 aserciones del flujo, más 18 y 31 de rechazos.
Las quince consultas de Config Server y el aislamiento de bases se repitieron sobre
la demo que ejecuta los digests publicados y esa configuración final.

El JSON de equivalencia demuestra que las imágenes finales tienen el mismo ID de
contenido que las aceptadas. El manifiesto fija los commits de construcción, la revisión
Git de configuración y las referencias públicas por digest. No se presenta el ensayo
parcial del 03/11 como prueba de una versión posterior.

Los logs y reportes privados permanecen fuera de Git. Los JSON públicos contienen
criterios, estados HTTP, métricas y versiones; no incluyen JWT, claves, contraseñas,+cabeceras Authorization ni cuerpos de autenticación.

## Repetición

Usar [Demo_Sprint03.md](Demo_Sprint03.md) y el manifiesto final. El workflow manual
**Aceptación integral Sprint03** prepara imágenes públicas, ejecuta las comprobaciones
en serie y publica únicamente `target/public/`. Está preparado como automatización de
repetición; los resultados anteriores corresponden a las ejecuciones locales y checks
CI identificados, no a una ejecución supuesta de ese workflow nuevo.

No ejecutar Postman mientras corre la aceptación: ambos modifican el catálogo de la
misma demo. Después del flujo positivo, reset permite volver explícitamente al inicio.

## Límites

- Los 500, timeout, 429 y contratos HTTP inválidos se verificaron en pruebas Java con
  HTTP real y MySQL; la caída del Courier de negocio se probó en Docker con 503.
- Disponible significa ACTIVO; no implica reserva o exclusividad.
- Tarifas continúa pendiente de definición docente, sin fórmula inventada.
- No se certifican carga, producción, arquitectura ARM ni participación académica.
