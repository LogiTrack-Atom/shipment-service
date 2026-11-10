# Solicitud y outbox — #78

La ampliación implementa POST 202/GET local, ADMIN en Gateway y Shipment, clave UUID
canónica por actor e intención, operación/outbox atómicos y publicador con lease,
mandatory y publisher confirms. No consume resultados ni modifica todavía el repartidor:
Courier#21 y #79 completan ese flujo antes del release #80. No activar esta ampliación
como entrega funcional completa hasta terminar sus dependencias.

La API guarda un UUID remoto sin comprobar su existencia/disponibilidad por HTTP:
Courier lo responderá por AMQP. Se mantiene Feign/PUT y la semántica de estados. Nueva
solicitud invalida la pendiente anterior; invalidación por PUT/cancelación y vencimiento
local definitivo se implementan en #79 antes de habilitar aplicación de resultados.

## Instalación/migración

El SQL está separado de la inicialización del núcleo: no modifica 001 ni se aplica al
Compose P0 por defecto. Para nueva instalación MQ, crear las bases con 001 y después
aplicar `docker/mysql/mq/004-assignment-outbox.sql`. Para actualizar, detener escrituras,
tomar backup y ensayar primero en una copia independiente. Aplicar el mismo archivo
una sola vez antes de activar las nuevas rutas/publicador. El publicador verifica
las tablas/columnas al iniciar y falla explícitamente si falta el esquema.

Ejemplo desde el repo y con env privado configurado, sin exponer MySQL al host:

```sh
sha256sum docker/mysql/mq/004-assignment-outbox.sql
docker compose --env-file /ruta/privada/core.env -f compose.sprint04.yaml exec -T shipment-mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' \
  < docker/mysql/mq/004-assignment-outbox.sql
```

Registrar el checksum y el resultado en el manifiesto de la ampliación #80. El ledger
de repartidores existente es específico de Courier y no se reutiliza para Shipment.
El DDL de MySQL hace commits implícitos: no se promete rollback transaccional de una
migración interrumpida. La reaplicación falla ante la columna existente; ante un fallo
parcial inspeccionar tablas/columnas y reanudar de forma revisada, sin borrar datos ni
usar CREATE IF NOT EXISTS para ocultar diferencias. Conservar backup y release P0.

La prueba de migración usa MySQL aislada con cliente/envío previos y verifica sus
identidades, timestamps, medidas, tracking y estado, generación inicial 0, tablas
vacías y rechazo de reaplicación. La aceptación de API prueba esquema instalado,
constraints de clave, rollback de operación/generación si falla insertar outbox y
carrera de dos solicitudes con la misma clave.

## Activación y credenciales

Mantener ASSIGNMENT_ENABLED=false por defecto. Para habilitar, completar en el env
privado ASSIGNMENT_ENABLED=true y MQ_SHIPMENT_PASSWORD con la credencial de lt_shipment
creada por prepare_broker.py; nunca usar la de lt_operator ni enviarla por chat.
ASSIGNMENT_PUBLISHER_ENABLED=true es el valor normal; false permite diagnóstico de
aceptación local sin publicar y conserva pendientes, no completa asignaciones.

```sh
docker compose --env-file /ruta/privada/core.env --env-file /ruta/privada/broker/broker.env \
  -f compose.sprint04.yaml -f compose.sprint04-mq.yaml -f compose.sprint04-assignment.yaml \
  --profile mq up -d --wait --wait-timeout 480
```

Usar imágenes nuevas de Shipment/Gateway que contengan #78, identificadas por build y
por digest en #80; las imágenes públicas P0 permanecen intactas y no ofrecen esta API.
El archivo opcional no agrega depends_on al broker: un broker caído no impide commit
local ni arranque del portal. No hay RabbitAdmin, declaración por aplicaciones ni
indicador de salud Rabbit incorporado al núcleo cuando MQ está deshabilitado.

El publicador reclama una fila por vez con lease de 30 s y SKIP LOCKED, cierra el commit
SQL y espera confirm hasta 5 s. Solo marca publicada con ACK positivo sin return.
Un fallo conserva payload/messageId y programa otro intento con backoff máximo 256 s;
un lease abandonado expira y puede recuperarse. El lease token evita que un intento
viejo marque el trabajo de uno nuevo. Crash entre confirm y marca SQL admite duplicados:
los consumidores deben usar inbox, como define #76. No es exactly-once.

READ COMMITTED limita el snapshot a cada consulta de aceptación; se vuelve a leer
replay después de tomar el bloqueo de envío. Si compiten dos envíos por actor/clave,
la constraint decide y se reconcilia tras rollback en otra transacción. No capturar el
conflicto dentro de rollback-only. Replays terminales conservan la misma operación.

Location usa el prefijo conocido del Gateway cuando este informa X-Forwarded-Prefix;
se ignoran otros prefijos para no construir una URL con valores arbitrarios. Gateway
permite Idempotency-Key en CORS, expone Location y exige ADMIN para ambas rutas.

## Evidencia y límites

Pruebas de API/MySQL (10), núcleo sin MQ (1), decisiones de publisher con dobles (4) y
publicador real con MySQL/RabbitMQ (1) se cuentan por separado. El ensayo real verifica
confirm, mandatory return sin ruta, outbox pendiente con broker caído y publicación
reanudada con la misma fábrica después de reiniciar el broker, sin reiniciar la app.
El fixture conserva un puerto local estable para no confundir cambio de endpoint con
fallo de reconexión. La prueba adicional de migración usa otra copia MySQL descartable.

Gateway: 172 pruebas/Javadoc locales, incluidos seis casos de roles y un preflight nuevo.
La regresión completa del servicio y CI de ambas PR deben aprobar antes de cerrar #78.
#80 conserva pruebas de consumidores/ACK, DLQ y caídas conjuntas aún pendientes.


Courier #21 completa el overlay de ambos servicios. El preparador ahora crea application.env privado con las dos credenciales restringidas y MQ deshabilitado; ver [BROKER.md](BROKER.md). No usar imágenes P0 para activar estas rutas.
