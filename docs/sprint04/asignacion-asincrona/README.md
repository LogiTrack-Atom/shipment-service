# ADR: asignación asíncrona — #76

Estado: adoptado para la ampliación Sprint04. Propietario: MaxDZ-10.
El 10/11/2026 se autorizó avanzar con RabbitMQ y cerrar #73 con la evidencia técnica
existente, retirando la reproducción por otro integrante como requisito. Esa prueba
no se realizó. G1 técnico aceptado; release del núcleo preservado por digest.

## Decisión y costo

Se implementa una consulta de disponibilidad asíncrona con operación persistente,
outbox en ambos servicios e inbox deduplicada. No sustituye Feign, el PUT actual ni
la verificación al iniciar ruta. AVAILABLE es una lectura puntual de Courier, no
una reserva de capacidad: un repartidor puede cambiar de estado después de observarlo.
El núcleo funciona con MQ deshabilitado por defecto; un broker caído no bloquea
consultas/operaciones síncronas. No se agrega Kubernetes, pagos ni correo a esta ampliación.

Costo restante planificado: #77 broker (3 puntos), #78 operación/outbox (8), Courier#21
inbox/resultado (5), #79 aplicación/concurrencia (8), frontend#10 interfaz y #80 fallas/release
(5), además de operación #81. Son estimaciones, no plazos garantizados. Se acepta este
alcance porque el núcleo ya está publicado y puede entregarse por separado. Si las
pruebas de consistencia o la capacidad disponible impiden terminar, conservar release
P0 y registrar diferimiento de MQ; no entregar una integración parcial como completa.

## API para frontend#10 y Gateway

- `POST /api/v1/envios/{envioId}/solicitudes-asignacion`, exclusivamente ADMIN.
  Cabecera obligatoria `Idempotency-Key`: UUID canónico en minúsculas. Body:
  `request.schema.json`. Antes del commit validar JWT/rol, UUID, estado PENDIENTE y
  existencia local del envío; no consultar Courier ni esperar confirmación AMQP.
- Responder **202** solo después del commit de operación y outbox; body conforme a
  `operation.schema.json`, Location `/api/v1/operaciones-asignacion/{operationId}`.
  A través del Gateway ambas rutas llevan el prefijo `/applications/apishipment`.
- Misma clave y actor (`sub`) con el mismo envío/repartidor devuelve 202 con la misma
  operación y su estado actual, incluso terminal; no crea otro comando. Clave igual
  con otra intención devuelve 409. La unicidad es `(actor_id, idempotency_key)` entre
  todos los envíos. Consultar replay después de autenticar, antes de validar el estado
  actual del envío. Las carreras se resuelven por constraint y nueva lectura tras rollback.
- `GET /api/v1/operaciones-asignacion/{operationId}`: ADMIN, 200/404; consulta local
  sin broker. 400 para clave/UUID/cuerpo inválidos; 401 sin JWT; 403 sin rol; 404 envío
  inexistente; 422 envío no PENDIENTE; 409 reutilización incompatible. Si MQ está
  deshabilitado, POST devuelve 503 sin insertar; GET sigue mostrando operaciones guardadas.
- Gateway y autorización local deben proteger las dos rutas, con el mismo contrato.
  No colocar JWT, cookies, correos, direcciones ni datos personales en AMQP o logs.
  Autorizar al aceptar: el procesamiento posterior no exige que el access token siga vivo.

## Commit local y competencia

Shipment bloquea la fila del envío con `findByIdForUpdate`, ya existente. Agregar
`assignment_version BIGINT NOT NULL DEFAULT 0` y `active_assignment_operation_id BINARY(16)`
nullable. Una solicitud nueva incrementa versión, crea operación PENDING con vencimiento
UTC a 300 segundos y fija su ID como vigente, sin modificar repartidor/historial.
Si había otra PENDING, pasa a SUPERSEDED/REPLACED en esa transacción.

PUT síncrono exitoso y toda transición de estado incrementan esa misma versión,
limpian la operación vigente y marcan la pendiente SUPERSEDED (REPLACED o STATE_CHANGED).
Hacerlo bajo el bloqueo ya compartido; un PUT rechazado no invalida una operación.
No introducir esperas AMQP ni transacciones distribuidas. No remodelar Feign en #76.

El resultado se aplica en una transacción local corta: deduplicar messageId, bloquear
el envío y comprobar operación vigente, versión exacta, tuple envío/repartidor y
causationId del comando registrado, PENDING y `ahora < expiresAt`. AVAILABLE asigna y
marca APPLIED sin evento de estado; INACTIVE/NOT_FOUND marca REJECTED sin asignar.
Si hubo cancelación/reasignación, registrar SUPERSEDED sin tocar los datos nuevos.
Un resultado repetido o de una operación terminal no vuelve a producir efectos.
Un finalizador local marca EXPIRED/DEADLINE_EXCEEDED al vencer, limpia el puntero
solo si sigue siendo propio y nunca cambia el repartidor. El resultado tardío se consume
sin aplicar. Vencimiento de negocio se comprueba en SQL/aplicación; no depende del TTL AMQP.
Todas las rutas que toman varios bloqueos usan envío → operación; deduplicación por índice
único en la misma transacción y rollback/reintento acotado para deadlocks.

## Persistencia prevista (implementación en #78, Courier#21 y #79)

| Servicio | Datos y restricciones |
|---|---|
| Shipment | `assignment_operations`: UUID PK, envío FK local, actor/clave con UNIQUE, repartidor solicitado, versión, command_message_id UNIQUE, estado/motivo, created/updated/expires DATETIME(6); índice `(status, expires_at)` |
| Shipment | `envios`: versión y puntero local mencionados; sin FK remota ni dependencia a tablas Courier |
| Ambos | `amqp_outbox`: message_id UUID PK, tipo/routing key permitidos, payload JSON, created/next_attempt, attempts, published_at y lease_until; índice de pendientes; lease corta evita dos publicadores concurrentes |
| Ambos | `amqp_inbox`: `(consumer_name, message_id)` UNIQUE, operation_id, huella SHA-256 del cuerpo y processed_at; misma identidad con cuerpo distinto es inválida |
| Courier | Inbox y outbox del resultado en un commit; command_message_id único, un resultado persistido por comando; lectura de su propia base sin HTTP ni modificación/reserva del repartidor |

Migraciones aditivas versionadas con checksum en el manifiesto propio, aplicadas
primero en copias MySQL 8.4 y en instalación vacía; nunca borrar volúmenes para migrar.
No eliminar inbox/idempotency/outbox de esta versión durante el Sprint: borrar registros
permitiría repetir efectos. Una política futura de retención exige una ventana explícita
para replay y descarte de mensajes antiguos, con migración y prueba propias.

## Transporte y recuperación

`topology.json` fija nombres y límites; esquemas v1 y ejemplos son consumidos por ambos
servicios. JSON UTF-8 ≤16 KiB, content-type application/json, delivery_mode=2,
AMQP message_id igual a messageId, correlation_id igual a operationId. Rechazar campos
extra, versión/tipo desconocidos, UUID/fecha inválidos y mensajes de otra topología.

Un nodo con colas quorum durables y volumen: persistencia local, sin afirmar alta
disponibilidad. Aplicaciones con usuarios separados y permisos de publicación/lectura
limitados; un usuario provisionador declara topología y políticas. Management solo en
loopback. Prefetch inicial 10 y un consumidor por flujo, ajustables después de medir.

Publicador de outbox: reclamar filas con lease SQL y cerrar transacción; publicar con
mandatory y publisher confirm; marcar publicada solo con confirm positivo **sin return**.
Timeout, return o corte deja pendiente con backoff acotado. Crash después de confirm y
antes de marcar produce duplicado legítimo, resuelto por inbox. Ninguna transacción SQL
permanece abierta esperando AMQP. Reanudar outbox tras reconexión.

ACK del consumidor solo después del commit local. Fallo transitorio antes del commit:
publicar copia persistente a retry con confirm/mandatory y después ACK original; conservar
messageId/cuerpo, incrementar cabecera x-retry-count (0 inicial, máximo 3). Retry espera
5 segundos y vuelve al flujo original. Si no se confirma esa copia, no ACK y pausar el
consumo con backoff para evitar bucle activo. Tras tres reintentos, o contrato inválido,
rechazar sin requeue hacia DLQ. No copiar cuerpos o excepciones con datos a logs; registrar
código de motivo, messageId seguro y hash. DLQ es evidencia y no se reinyecta automáticamente.

Políticas DLX y TTL fuera del código de negocio; colas quorum con dead-letter-strategy
at-least-once y overflow reject-publish en colas con DLX. Retry sin consumidor, TTL fijo
5000 ms y DLX al exchange original. Main DLX a dead, sin TTL de negocio. Esta configuración
se prueba realmente en #77/#80; no se considera garantizada por este documento.
DLQ llena o broker no disponible exige alerta/intervención; no prometer exactly-once.
Los confirms y ACK resuelven tramos distintos del transporte, según
[RabbitMQ](https://www.rabbitmq.com/docs/confirms). Ver también
[DLX](https://www.rabbitmq.com/docs/dlx) y [TTL](https://www.rabbitmq.com/docs/ttl).

## Verificación y salida

#76 valida esquemas, ejemplos y rechazos estructurales; no afirma integración AMQP.
#77 prueba broker/reinicio/permisos; #78 commit+outbox+idempotencia y Gateway;
Courier#21 commit/ACK y duplicados; #79 carreras con PUT, cancelación y resultados tardíos;
#80 prueba caídas, retry/DLQ y releases con imágenes/migraciones propias. Frontend#10
consume operationId/Location y los estados terminales sin repetir POST con otra clave
tras un resultado incierto. Mantener el mismo Idempotency-Key hasta reconciliar.

MQ se entrega con opt-in de configuración y Compose separado; deshabilitar consumidores,
publicadores y POST asíncrono para volver al núcleo. Antes de volver, conciliar operaciones
pendientes y evitar ejecutar aplicaciones antiguas sobre un modelo que las procese.
El release P0 se conserva como entorno separado; ninguna migración destructiva se revierte
borrando tablas. No cerrar #80 sin evidencia de fallas y regresión del PUT/Feign.


Para validar los contratos sin arrancar servicios:

```sh
python3 -m venv /tmp/logitrack-contracts
/tmp/logitrack-contracts/bin/pip install -r scripts/contracts/requirements.txt
/tmp/logitrack-contracts/bin/python -m unittest discover -s scripts/contracts/tests -v
```

En equipos sin ensurepip/python3-venv, se puede crear el entorno con `uv venv` e
instalar con `uv pip install --python /ruta/al/entorno/bin/python -r ...`.
Dependencias del verificador fijadas a las versiones comprobadas. Ocho pruebas de
contrato aprobadas; los subcasos no se cuentan como pruebas de integración.


Implementación de solicitud/publicador y procedimiento de migración: [#78](SOLICITUD_OUTBOX.md). El ledger histórico de repartidores es exclusivo de Courier.
