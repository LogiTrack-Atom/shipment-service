# Operación y demo RabbitMQ · #81

Preparar el laboratorio aislado según el [release MQ](../release-mq/README.md). Usar la plantilla MQ, sus migraciones y las imágenes por digest del [manifiesto](../release-mq/published.json). El [release P0](../release/published.json) tiene su propio procedimiento de arranque.

| Flujo | Productor | Consumidor | Cola principal | Reintentos y DLQ |
| --- | --- | --- | --- | --- |
| Comando de disponibilidad | Shipment | Courier | `lt.courier.assignment.v1` | `lt.courier.assignment.retry.v1` / `lt.courier.assignment.dead.v1` |
| Resultado de disponibilidad | Courier | Shipment | `lt.shipment.assignment.v1` | `lt.shipment.assignment.retry.v1` / `lt.shipment.assignment.dead.v1` |

La [topología](topology.json) fija exchanges, claves de enrutamiento y colas quorum. El 202 confirma que Shipment guardó la operación y su outbox en la base local. El publisher confirm acredita la aceptación por el broker. El publicador comprueba también los mensajes devueltos por falta de ruta antes de marcar su outbox. El ACK del consumidor ocurre después del commit de la inbox y del efecto local. Una pérdida del ACK puede producir redelivery. La inbox protege el efecto local frente a duplicados.

## Demostración de caída y recuperación

1. Con el entorno saludable, entrar al portal como ADMIN y crear un envío y un repartidor activo ficticios. Seleccionar la asignación asíncrona en el detalle del envío.
2. Antes de solicitarla, detener únicamente `courier-service` con `docker compose … stop courier-service`, usando el mismo proyecto, archivos y env del laboratorio. Enviar la solicitud una vez. Registrar el ID de la operación mostrado por el portal. El 202 deja el seguimiento pendiente.
3. Consultar en el panel local del broker o ejecutar `docker compose … exec -T rabbitmq rabbitmqctl list_queues -p logitrack-s04 name messages_ready messages_unacknowledged consumers`. La cola de comandos muestra un mensaje listo para entrega y cero consumidores después de detener Courier. Usar una operación nueva y observar el cambio respecto del conteo inicial.
4. Arrancar Courier con `docker compose … start courier-service` antes del vencimiento de 300 segundos. Esperar que consuma el comando y que Shipment aplique el resultado. Usar «Consultar ahora» si la espera visual terminó. Verificar el estado confirmado y el repartidor del envío. Contrastar la cola drenada y la asignación final.
5. Si se vence el plazo, registrar el estado terminal. La demostración puede repetirse con un envío ficticio nuevo. Si el POST resulta incierto, reconciliar con la misma clave de idempotencia.

Para ensayar fallas adicionales en un laboratorio descartable, ejecutar `scripts/mq/acceptance.py` siguiendo la guía del release. El escenario de Courier detenido, el de broker detenido y el de retry/DLQ están cubiertos en la [evidencia técnica de #80](../release-mq/backend-evidence.json).

## Correlación y operación

El `correlationId` AMQP identifica la operación. Los warnings del release registran `messageId` y motivo, y algunos incluyen hash. En Shipment, consultar por ID de operación las filas de `amqp_outbox` y `assignment_inbox` para obtener el ID del comando y el del resultado. En Courier, el outbox del resultado también conserva el ID de operación. Leer únicamente identificadores y estados, y filtrar los logs por el ID de mensaje correspondiente. Usar los UUID del laboratorio en la ficha del ensayo y omitir cuerpos de mensaje, encabezados de autorización, cookies y credenciales.

Observar `messages_ready`, `messages_unacknowledged` y `consumers` en las colas principales y el conteo de las DLQ. Cruzar una cola creciente con los consumidores activos y el conteo SQL de `amqp_outbox` donde `published_at IS NULL`. Separar los pendientes de publicación de los que ya están en el broker.

Los fallos transitorios tienen hasta tres reintentos con espera de cinco segundos. Un contrato inválido o un fallo agotado va a DLQ. Antes de reejecutar, diagnosticar y corregir la causa, verificar que la operación siga vigente y registrar la decisión. El ensayo de #80 prueba una reejecución administrativa controlada tras reparar el fallo SQL. Reenvía por el exchange y routing key del flujo original, con el payload original y los identificadores de mensaje y correlación intactos. Una operación vencida o reemplazada se resuelve por el flujo de negocio. El broker no tiene redrive automático de DLQ. Conservar el registro de la revisión y evitar purgas para ocultar pendientes.

La entrega admite redelivery y aplica idempotencia local. Una instancia de RabbitMQ con volumen persistente es un punto único de falla. La disponibilidad consultada no reserva al repartidor. Los detalles de recuperación y permisos están en [BROKER.md](BROKER.md).

## Constancia independiente

Registrar integrante, fecha, digest, estado inicial y final de la operación, cambio de conteos en la cola y evidencia sanitizada. El integrante explica la diferencia entre 202, publisher confirm y ACK, y describe qué haría con una DLQ. La evidencia de #80 queda como referencia para comparar el resultado de ese ensayo.
