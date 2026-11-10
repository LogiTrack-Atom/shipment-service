package com.logitrack_atom.shipment_service.assignment;

import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import tools.jackson.databind.ObjectMapper;

/** Valida entrega, confirma SQL antes del ACK y separa rechazo permanente de reintento acotado. */
public class AssignmentResultListener implements ChannelAwareMessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentResultListener.class);
    private final AssignmentResultsService service;
    private final ObjectMapper mapper;
    private final RabbitTemplate rabbit;

    /**
     * Recibe procesamiento y transporte ya configurados sin autoridad HTTP del usuario final.
     * @param service commit idempotente de inbox y agregado
     * @param mapper parser de contrato
     * @param rabbit transporte con confirms, returns y mandatory
     */
    public AssignmentResultListener(AssignmentResultsService service, ObjectMapper mapper, RabbitTemplate rabbit) {
        this.service = service;
        this.mapper = mapper;
        this.rabbit = rabbit;
    }

    /**
     * Procesa una entrega manual; un fallo de ACK se propaga para permitir reentrega del broker.
     * @param message cuerpo/propiedades sin copiar datos a logs
     * @param channel canal propietario de la entrega
     * @throws Exception si el canal se interrumpe antes de confirmar o rechazar
     */
    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        int attempt;
        try {
            attempt = retryCount(message);
            var result = AvailabilityResult.read(message, mapper);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(message.getBody());
            service.process(result, hash);
        } catch (InvalidAssignmentResultException invalid) {
            LOG.warn("assignment.result.invalid messageId={} reason=invalid_contract hash={}", safeId(message), hash(message));
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            return;
        } catch (RuntimeException transientFailure) {
            attempt = retryCount(message);
            if (attempt >= 3) {
                LOG.warn("assignment.result.dead messageId={} reason=retry_exhausted hash={}", safeId(message), hash(message));
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            retry(message, channel, attempt);
            return;
        }
        // process() ya retornó después de su commit. No capturar este ACK como fallo de SQL.
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    private void retry(Message message, Channel channel, int attempt) throws Exception {
        try {
            var properties = new MessageProperties();
            properties.setContentType("application/json");
            properties.setContentEncoding("UTF-8");
            properties.setMessageId(message.getMessageProperties().getMessageId());
            properties.setCorrelationId(message.getMessageProperties().getCorrelationId());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setHeader("x-retry-count", attempt + 1);
            var confirmation = new CorrelationData(UUID.randomUUID().toString());
            rabbit.send("lt.assignment.retry.v1", "shipment.retry", new Message(message.getBody(), properties), confirmation);
            var confirm = confirmation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.ack() || confirmation.getReturned() != null) throw new IllegalStateException("Retry sin confirmación/ruta");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            throw interrupted;
        } catch (Exception unavailable) {
            LOG.warn("assignment.result.retry_pending messageId={} reason=retry_unconfirmed", safeId(message));
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            // Sin transacción SQL; evita un bucle activo. La política quorum limita redeliveries a 10.
            Thread.sleep(5000);
            return;
        }
        // Solo confirmar original tras confirm positivo de la copia persistente en retry.
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get("x-retry-count");
        if (value == null) return 0;
        try {
            if (!(value instanceof Number)) throw new InvalidAssignmentResultException();
            int count = new BigDecimal(value.toString()).intValueExact();
            if (count < 0 || count > 3) throw new InvalidAssignmentResultException();
            return count;
        } catch (ArithmeticException invalid) { throw new InvalidAssignmentResultException(); }
    }

    private String safeId(Message message) {
        try { return UUID.fromString(message.getMessageProperties().getMessageId()).toString(); }
        catch (RuntimeException invalid) { return "invalid"; }
    }

    private String hash(Message message) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(message.getBody()));
    }
}
