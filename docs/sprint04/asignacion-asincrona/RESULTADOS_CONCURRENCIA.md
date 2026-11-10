# Resultado y concurrencia — #79

Shipment consume `assignment.availability.checked.v1` desde su cola de resultados con
usuario de servicio restringido. Valida esquema/metadatos antes de SQL y confirma el
mensaje después del commit local de inbox y efecto. No espera HTTP/AMQP manteniendo una
transacción de base abierta. Los fallos permanentes van a DLQ; transitorios usan copia
retry persistente confirmada antes del ACK original, hasta 3 intentos, según #76/#77.

## Guardas y orden de bloqueos

Siempre envío → operación. La aplicación del resultado vuelve a leer ambas filas bajo
bloqueo y valida operación, envío, repartidor, generación, causationId del comando y
expiresAt original. Solo aplica AVAILABLE si la operación sigue PENDING/vigente, el
envío está PENDIENTE y no venció. El reloj se consulta después de tomar los bloqueos.
No crea un evento de estado ni cambia PENDIENTE. INACTIVE/NOT_FOUND marcan REJECTED sin
cambiar el repartidor existente. Operación terminal o resultado viejo no pisa datos nuevos.

Inbox conserva messageId/huella de cuerpo; reentrega idéntica no produce otro efecto.
Misma identidad con otro cuerpo es inválida. El efecto y la marca de inbox se confirman
o revierten juntos: incluso una falla de inserción de inbox revierte la asignación y
el estado de la operación. No se elimina historial de deduplicación durante el Sprint.

PUT síncrono, cancelación y transiciones comparten el bloqueo existente. Solo después
de validar negocio/disponibilidad incrementan generación, limpian el puntero y marcan
la pendiente SUPERSEDED dentro de su misma transacción. Si falla disponibilidad o la
persistencia de estado/historial, no se invalida el pendiente. Feign conserva su ruta y
semántica; no se transforma en espera AMQP.

Estas guardas no se omiten por deshabilitar MQ: una operación pendiente previa todavía
podría llegar cuando se reactive. Se detecta el esquema y solo se memoriza su presencia;
un negativo se vuelve a consultar para soportar una migración/otra instancia habilitada.
Un núcleo sin el esquema aditivo sigue admitiendo su flujo síncrono original.

Si AVAILABLE confirma antes de una cancelación concurrente, la cancelación puede
conservar la referencia que ya existía, como el contrato síncrono original; el envío queda
CANCELADO y no se asigna después de cancelar. Si cancelación confirma primero, el
resultado no asigna. Frente a PUT, prevalece el reemplazo explícito tanto si el resultado
confirmó antes como si llegó después. Ningún resultado crea un evento de transición.

## Vencimiento sin broker

Finalizador local acotado a 50 operaciones por ciclo con el módulo habilitado; cada una
toma sus locks y revalida fecha/estado. GET concilia también el vencimiento local antes
de responder, incluso con transportes deshabilitados. EXPIRED/DEADLINE_EXCEEDED limpia
solo su propio puntero y no borra una asignación anterior. El consumidor compara vigencia
por su cuenta, así que un finalizador demorado no permite aplicar un resultado vencido.
El reloj UTC inyectado permite probar tardíos sin dormir cinco minutos.

## Migración y configuración

Aplicar una vez `docker/mysql/mq/005-assignment-results-inbox.sql` después de 004,
con copia/backup probado y checksum registrado en el manifiesto #80. Agrega solo inbox
local y su FK a operaciones; no altera usuarios ni historiales. Se prueba la instalación
sobre el esquema 004 con una operación pendiente y su outbox ya existentes. DDL MySQL
implica commits; revisar estado ante interrupción, no reejecutar a ciegas ni borrar datos.
El consumidor falla explícitamente al iniciar si falta el inbox requerido.

```sh
sha256sum docker/mysql/mq/005-assignment-results-inbox.sql
docker compose --env-file /ruta/privada/core.env -f compose.sprint04.yaml exec -T shipment-mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' \
  < docker/mysql/mq/005-assignment-results-inbox.sql
```

ASSIGNMENT_ENABLED sigue false por defecto. Con módulo activo, publisher-enabled y
consumer-enabled controlan cada transporte; si ambos son false, API/consulta/expiración
funcionan sin crear conexión AMQP ni exigir su contraseña. Si al menos uno está activo,
usa la credencial externa de lt_shipment. El overlay pasa ambos flags a los dos servicios.
Sin RabbitAdmin, prefetch 10 y un consumidor por flujo; el broker no integra salud crítica.
No activar como entrega completa antes de aceptación/release #80 y frontend#10.

## Verificación

8 pruebas unitarias nuevas de contrato/listener, 23 casos MySQL nuevos para aplicación,
rechazo, duplicados/carreras, cancelación/PUT/reemplazo, rollback y vencimiento; las
53 pruebas unitarias existentes del servicio también pasan. Se prueba modo MQ deshabilitado
con pendiente previo y lectura de vencimiento local. El corte de ACK de esta matriz es
un canal controlado unido al commit real MySQL: no se presenta como corte de red real.
Courier#21 ya tiene su ensayo de conexión nativa; #80 prueba la cadena completa.

La regresión completa de Java/Javadoc y CI deben aprobar antes de cerrar la issue.
Núcleo P0, imágenes publicadas, main y ramas históricas se conservan.
