package com.logitrack_atom.shipment_service.assignment;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/** Publica outbox fuera de la transacción SQL y tolera cortes mediante reentrega estable. */
public class AssignmentPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentPublisher.class);
    private final OutboxStore outbox;
    private final RabbitTemplate rabbit;

    /**
     * Recibe acceso SQL y transacciones locales.
     * @param outbox mensajes pendientes locales
     * @param rabbit canal con mandatory/confirm/returns
     */
    public AssignmentPublisher(OutboxStore outbox, RabbitTemplate rabbit) {
        this.outbox = outbox;
        this.rabbit = rabbit;
    }

    /** Publica hasta diez filas por ciclo; ningún lock SQL permanece abierto esperando AMQP. */
    @Scheduled(fixedDelayString = "${logitrack.assignment.poll-delay-ms:2000}")
    public void publishPending() {
        for (int n = 0; n < 10; n++) {
            var candidate = outbox.claim();
            if (candidate.isEmpty()) return;
            var row = candidate.get();
            try {
                var properties = new MessageProperties();
                properties.setContentType("application/json");
                properties.setContentEncoding("UTF-8");
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId(row.messageId().toString());
                properties.setCorrelationId(row.operationId().toString());
                var correlation = new CorrelationData(row.messageId() + ":" + row.leaseToken());
                rabbit.send("lt.assignment.commands.v1", row.routingKey(),
                        new Message(row.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.ack() || correlation.getReturned() != null)
                    throw new IllegalStateException("Publicación no confirmada o sin ruta");
                outbox.confirmed(row);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                outbox.failed(row);
                return;
            } catch (Exception failure) {
                outbox.failed(row);
                LOG.warn("assignment.publish_pending messageId={} reason=unconfirmed", row.messageId());
                return;
            }
        }
    }
}
