package com.logitrack_atom.shipment_service.assignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;

/**
 * Resultado v1 del usuario AMQP de Courier; describe una observación, no una reserva.
 * @param messageId identidad estable de transporte
 * @param operationId correlación de negocio
 * @param envioId UUID local del envío
 * @param repartidorId UUID remoto observado por Courier
 * @param assignmentVersion generación que Shipment revalidará al recibir el resultado
 * @param occurredAt observación de Courier UTC
 * @param expiresAt vencimiento original de la operación
 * @param causationId identidad del comando original
 * @param availability AVAILABLE, INACTIVE o NOT_FOUND
 */
public record AvailabilityResult(UUID messageId, UUID operationId, UUID envioId, UUID repartidorId,
        long assignmentVersion, Instant occurredAt, Instant expiresAt, UUID causationId, String availability) {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "messageId", "operationId", "envioId",
            "repartidorId", "assignmentVersion", "occurredAt", "expiresAt", "type", "causationId", "availability");

    /**
     * Valida tamaño, esquema y correlación antes de comenzar una transacción SQL.
     * @param message cuerpo y propiedades recibidas del broker
     * @param mapper parser JSON estricto de la aplicación
     * @return resultado válido sin campos adicionales
     */
    public static AvailabilityResult read(Message message, ObjectMapper mapper) {
        try {
            if (message.getBody().length == 0 || message.getBody().length > 16384) throw invalid();
            Map<?, ?> body = mapper.readValue(message.getBody(), Map.class);
            if (!body.keySet().equals(FIELDS) || integer(body.get("schemaVersion")) != 1
                    || !"assignment.availability.checked.v1".equals(body.get("type"))) throw invalid();
            var result = new AvailabilityResult(uuid(body.get("messageId")), uuid(body.get("operationId")),
                    uuid(body.get("envioId")), uuid(body.get("repartidorId")), integer(body.get("assignmentVersion")),
                    instant(body.get("occurredAt")), instant(body.get("expiresAt")), uuid(body.get("causationId")), availability(body.get("availability")));
            var properties = message.getMessageProperties();
            var mode = properties.getReceivedDeliveryMode() != null ? properties.getReceivedDeliveryMode() : properties.getDeliveryMode();
            if (mode != org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT
                    || !"application/json".equals(properties.getContentType())
                    || !result.messageId().toString().equals(properties.getMessageId())
                    || !result.operationId().toString().equals(properties.getCorrelationId())) throw invalid();
            return result;
        } catch (RuntimeException error) {
            throw invalid();
        }
    }

    private static String availability(Object value) {
        if (!(value instanceof String text) || !Set.of("AVAILABLE", "INACTIVE", "NOT_FOUND").contains(text)) throw invalid();
        return text;
    }

    private static long integer(Object value) {
        if (!(value instanceof Number)) throw invalid();
        long result = new BigDecimal(value.toString()).longValueExact();
        if (result < 1 || result > 9007199254740991L) throw invalid();
        return result;
    }

    private static UUID uuid(Object value) {
        if (!(value instanceof String text)) throw invalid();
        UUID result = UUID.fromString(text);
        if (!result.toString().equals(text)) throw invalid();
        return result;
    }

    private static Instant instant(Object value) {
        if (!(value instanceof String text) || !text.endsWith("Z")) throw invalid();
        return Instant.parse(text);
    }

    private static InvalidAssignmentResultException invalid() { return new InvalidAssignmentResultException(); }
}
