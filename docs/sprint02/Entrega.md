# Entrega técnica — Sprint02

La versión `sprint02-1.0.0` integra los cuatro servicios y tres bases MySQL. Se entrega desde `develop`; no requiere fusionar con `main`. Las asignaciones de JMatiasF y DiazGab se conservan en las issues.

## Versiones publicadas

| Componente | Imagen Docker Hub | Commit de fuentes |
| --- | --- | --- |
| shipment-service | `maxdz10/shipment-service:sprint02-1.0.0` | `b2dda870e479` |
| courier-service | `maxdz10/courier-service:sprint02-1.0.0` | `33ffdd06441b` |
| security-service | `maxdz10/security-service:sprint02-1.0.0` | `9d29a30ef320` |
| configserver | `maxdz10/configserver:sprint02-1.0.0` | `8e5cc4747027` |

Los digests de registro se conservan en [versiones.json](entrega/versiones.json). Las imágenes se construyeron para **linux/amd64**; no se declara soporte nativo ARM. Las etiquetas se consideran inmutables: una corrección posterior debe publicar una versión nueva. La documentación de cierre posterior no cambia los binarios de esta versión.

La configuración usa el commit `b40bd4d0eecd5eff6e5ef0a7b23d0a106a39ba13` de configserver-ms, también identificado por el tag anotado `sprint02-1.0.0`. Los clientes solicitan explícitamente ese commit; develop continúa siendo la rama de trabajo.

## Arranque desde cero

1. Clonar únicamente shipment-service y seleccionar `develop` o el tag de entrega.
2. Preparar un par RSA externo de 2048 bits o superior. No incluir claves en el repositorio. La privada corresponde exclusivamente a security; los tres servicios usan la pública del mismo par.
3. Copiar `.env.example` a un archivo privado y completar seis contraseñas independientes, rutas absolutas de las claves y KEYS_GID. El usuario del contenedor sigue siendo 10001; el grupo suplementario debe poder leer las claves. Usar 0640 para la privada y permisos de tránsito de grupo en el directorio.
4. Para el primer arranque habilitar bootstrap ADMIN y proporcionar email/password privados. El registro público solo crea CLIENTE. Desactivar bootstrap y recrear security después del aprovisionamiento, conservando su volumen.
5. Descargar y arrancar:

```sh
docker compose --env-file /ruta/privada/logitrack.env config --quiet
docker compose --env-file /ruta/privada/logitrack.env pull
docker compose --env-file /ruta/privada/logitrack.env up -d --wait
docker compose --env-file /ruta/privada/logitrack.env ps
```

El archivo base usa imágenes publicadas; no usa build, JAR del host ni repositorios hermanos. La [guía de Compose](Compose.md) explica puertos, salud, secretos y persistencia. Las bases no publican puertos. Las APIs quedan en loopback 8080, 8081 y 8082; Config Server, en 8085.

## Flujo reproducible en Postman

Importar [colección integrada](../../postman/LogiTrack-Sprint02-Integrado.postman_collection.json) y [entorno de ejemplo](../../postman/LogiTrack-Sprint02-Integrado.postman_environment.json). Completar una copia privada con las tres URLs, email/password del ADMIN existente y una contraseña válida de prueba (mínimo 12 caracteres y máximo 72 bytes UTF-8). Ejecutar toda la colección en orden.

El flujo crea sus propios usuarios y UUID; no necesita copiarlos manualmente. Los tokens se conservan solo en variables locales de ejecución. Usar un entorno aislado: la colección crea registros y no los elimina. No exportar entornos o sesiones con credenciales.

```sh
npx --yes --package=newman@6.2.3 newman run postman/LogiTrack-Sprint02-Integrado.postman_collection.json \
  -e /ruta/privada/postman-integrado.json --bail
```

La colección de regresión anterior permanece disponible para estados, disponibilidad y auditoría. Los rechazos por JWT vencido y la caída de courier se registran en el ensayo integrado de entrega.

## Resultado verificado — 27/10/2026

[Verificación integrada](entrega/verificacion-integrada.json):

- Cuatro referencias de imágenes retiradas localmente y recuperadas desde Docker Hub mediante pull sin credenciales. Se reutilizaron capas ya presentes; no se presenta como descarga de todas las capas en una máquina vacía.
- Siete contenedores saludables, sin construcción local y con secretos efímeros externos.
- Colección integrada de Postman ejecutada con Newman: 15 solicitudes y 20 aserciones aprobadas.
- 36 solicitudes: registro/login real, CLIENTE y REPARTIDOR, asociación administrativa verificada, envío, asignación, EN_RUTA, ENTREGADO e historial.
- Acceso ajeno 403; ausencia de token, firma alterada y token vencido 401; courier detenido produce 503 al asignar y conserva el envío sin cambios.
- Seis respuestas de configuración dev/prod con revisión, fuentes, marcadores y placeholders; aplicaciones ejecutadas primero en dev y después en prod.
- Tres usuarios MySQL aislados: cada uno solo ve su base de negocio. Se recreó el entorno completo sin borrar volúmenes: persistieron usuarios, UUID de repartidor, envío entregado e historial; el segundo arranque tuvo bootstrap desactivado.
- Config Server también se ejecutó aislado, sin MySQL: seis consultas del tag, usuario 10001, salud UP, diagnósticos no expuestos y aplicación inexistente 404. Git inaccesible rechazó el arranque con salida 1.

CI de shipment PR #37: 283 pruebas Java, 19 de migración y Javadoc aprobados. La prueba Maven de Config Server y su Javadoc aprobaron localmente; no se atribuye una ejecución remota inexistente a ese repositorio. Las verificaciones históricas adicionales conservan su fecha y referencia en [Regresión](Regresion.md) y en los PR de cada servicio.

Los recursos de ensayo fueron aislados y retirados. No se migraron ni borraron datos reales del equipo. Para datos del Sprint01, aplicar el procedimiento de [migración](migracion/README.md), con backup, ensayo y validación; las identidades históricas no se adjudican automáticamente por email.

## Matriz de cumplimiento

| Requisito académico | Implementación y evidencia |
| --- | --- |
| Envíos, reglas e historial conservados | shipment #23–#27; regresión y auditoría transaccional |
| Repartidores independientes y disponibilidad REST | courier #1–#6, #8 y #15; esquema propio, DTOs y OpenAPI |
| Usuarios, hash, JWT y roles | security #1–#4; BCrypt, RS256, ADMIN/CLIENTE/REPARTIDOR |
| Pertenencia y validación local de JWT | shipment #25 y courier #4; flujo real y accesos denegados |
| Configuración Git y ambientes | configserver #4; configserver-ms #5/#9/#11; clientes dev/prod |
| Cuatro Dockerfiles e imágenes públicas | Referencias y digests de esta entrega |
| Compose con MySQL por servicio | shipment #28; siete contenedores y persistencia |
| Publicación y entrega reproducible | shipment #29; guía, versiones y comprobación integrada |

API Gateway y descubrimiento de servicios quedan fuera del alcance del Sprint02.

## Demostración y entrega académica

La implementación técnica y sus comprobaciones no acreditan por sí mismas la presentación a la cátedra. No se dispone todavía de evidencia del video ni de la constancia de entrega del Sprint02.

Para la demostración: mostrar Project/issues y arquitectura; ejecutar pull/up/ps; registrar/login como cliente; crear y vincular un repartidor desde ADMIN; asignar el envío; avanzar EN_RUTA/ENTREGADO con su usuario y consultar historial. Mostrar un 401, un 403 de recurso ajeno y los perfiles en Config Server. Finalizar recreando sin borrar volúmenes y leyendo el mismo envío.

El equipo debe adjuntar el enlace del video, los repositorios, las imágenes, la versión de configuración y la constancia de entrega cuando estén disponibles. No incluir contraseñas, JWT ni claves privadas en la grabación o en capturas.
