package com.logitrack_atom.shipment_service.assignment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;

/** Crea resultados ficticios de contrato; no contiene usuarios, JWT ni contraseñas del equipo. */
public final class AssignmentResultFixtures {
    private AssignmentResultFixtures() { }

    /**
     * Construye un resultado nuevo para pruebas de contrato.
     * @param rider repartidor local que se consultará
     * @return campos v1 canónicos
     */
    public static Map<String, Object> body(UUID rider) {
        var body = new LinkedHashMap<String, Object>();
        body.put("schemaVersion", 1);
        body.put("causationId", UUID.randomUUID().toString());
        body.put("availability", "AVAILABLE");
        body.put("type", "assignment.availability.checked.v1");
        body.put("messageId", UUID.randomUUID().toString());
        body.put("operationId", UUID.randomUUID().toString());
        body.put("envioId", UUID.randomUUID().toString());
        body.put("repartidorId", rider.toString());
        body.put("assignmentVersion", 1);
        body.put("occurredAt", Instant.now().toString());
        body.put("expiresAt", Instant.now().plusSeconds(300).toString());
        return body;
    }

    /**
     * Representa propiedades de una entrega manual del broker.
     * @param body contenido ficticio
     * @param mapper parser del ensayo
     * @return entrega con metadatos concordantes
     */
    public static Message message(Map<String, Object> body, ObjectMapper mapper) {
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(String.valueOf(body.get("messageId")));
        properties.setCorrelationId(String.valueOf(body.get("operationId")));
        properties.setReceivedDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setDeliveryTag(1);
        return new Message(mapper.writeValueAsBytes(body), properties);
    }
}
