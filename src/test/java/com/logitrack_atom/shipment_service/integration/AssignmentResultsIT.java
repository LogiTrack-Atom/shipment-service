package com.logitrack_atom.shipment_service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.logitrack_atom.shipment_service.assignment.*;
import com.logitrack_atom.shipment_service.support.SecurityTestActors;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** Consistencia real MySQL ante resultados, duplicados, vencimientos y competencia con comandos síncronos. */
@SpringBootTest(properties = {"logitrack.assignment.enabled=true", "logitrack.assignment.publisher-enabled=false", "logitrack.assignment.consumer-enabled=false"})
class AssignmentResultsIT extends MySqlIntegrationSupport {
    @Autowired AssignmentService assignments;
    @Autowired AssignmentResultsService results;
    @Autowired AssignmentStore store;
    @Autowired AssignmentSchema schema;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    private UUID envio;
    private final UUID rider = UUID.randomUUID();
    private AssignmentOperation operation;

    @BeforeEach
    void scenario() throws Exception {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_operations'", Integer.class) == 0) apply("004-assignment-outbox.sql");
        envio = shipmentService.registrar(IntegrationFixtures.solicitud("result@example.test")).id();
        operation = assignments.accept(envio, rider, UUID.randomUUID().toString());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_inbox'", Integer.class) == 0) apply("005-assignment-results-inbox.sql");
        assertThat(assignments.find(operation.operationId()).operationId()).isEqualTo(operation.operationId());
    }

    private void apply(String filename) throws Exception {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.FileSystemResource(Path.of("docker/mysql/mq", filename)));
            return null;
        });
    }

    private Map<String, Object> body(AssignmentOperation operation, String availability) {
        String raw = jdbc.queryForObject("SELECT payload FROM amqp_outbox WHERE operation_id=UUID_TO_BIN(?)", String.class, operation.operationId().toString());
        Map<String, Object> data = new LinkedHashMap<>(mapper.readValue(raw, Map.class));
        data.put("causationId", data.get("messageId"));data.put("messageId", UUID.randomUUID().toString());
        data.put("type", "assignment.availability.checked.v1");data.put("availability", availability);data.put("occurredAt", Instant.now().toString());
        return data;
    }

    private Message message(Map<String, Object> body) {
        var properties = new MessageProperties();properties.setContentType("application/json");
        properties.setMessageId(body.get("messageId").toString());properties.setCorrelationId(body.get("operationId").toString());
        properties.setReceivedDeliveryMode(MessageDeliveryMode.PERSISTENT);properties.setDeliveryTag(1);
        return new Message(mapper.writeValueAsBytes(body), properties);
    }

    private void process(Message message) throws Exception { results.process(AvailabilityResult.read(message, mapper), MessageDigest.getInstance("SHA-256").digest(message.getBody())); }
    private long inboxCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM assignment_inbox", Long.class); }
    private UUID assigned() { return envios.findById(envio).orElseThrow().getRepartidorId(); }
    private AssignmentResultsService afterDeadline() { return new AssignmentResultsService(store, schema, manager, Clock.fixed(operation.expiresAt().plusSeconds(1), ZoneOffset.UTC)); }

    @Test
    void availableAssignsWithoutStateEventOrHttp() throws Exception {
        process(message(body(operation, "AVAILABLE")));
        assertThat(assigned()).isEqualTo(rider);
        assertThat(assignments.find(operation.operationId()).status()).isEqualTo("APPLIED");
        assertThat(envios.findById(envio).orElseThrow().getEstado().name()).isEqualTo("PENDIENTE");
        assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio)).hasSize(1);
        assertThat(inboxCount()).isEqualTo(1);verifyNoInteractions(courierClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "NOT_FOUND"})
    void negativeObservationRejectsWithoutAssigning(String availability) throws Exception {
        process(message(body(operation, availability)));
        assertThat(assigned()).isNull();assertThat(assignments.find(operation.operationId()).status()).isEqualTo("REJECTED");
        assertThat(assignments.find(operation.operationId()).reason()).isEqualTo(availability);
        assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio)).hasSize(1);
    }

    @Test
    void duplicateKeepsOneEffectAndDifferentBodyWithSameIdIsRejected() throws Exception {
        var body = body(operation, "AVAILABLE");var delivery = message(body);process(delivery);
        var updated = envios.findById(envio).orElseThrow().getUpdatedAt();process(delivery);
        assertThat(inboxCount()).isEqualTo(1);assertThat(envios.findById(envio).orElseThrow().getUpdatedAt()).isEqualTo(updated);
        body.put("availability", "INACTIVE");
        assertThatThrownBy(() -> process(message(body))).isInstanceOf(InvalidAssignmentResultException.class);
        assertThat(assigned()).isEqualTo(rider);assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void cancellationWinsOverLateResult() throws Exception {
        var delivery = message(body(operation, "AVAILABLE"));shipmentService.cancelar(envio);process(delivery);
        assertThat(assigned()).isNull();assertThat(assignments.find(operation.operationId()).status()).isEqualTo("SUPERSEDED");
        assertThat(envios.findById(envio).orElseThrow().getEstado().name()).isEqualTo("CANCELADO");
        assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio)).hasSize(2);
    }

    @Test
    void synchronousPutCannotBeOverwritten() throws Exception {
        var delivery = message(body(operation, "AVAILABLE"));UUID replacement = UUID.randomUUID();
        shipmentService.asignarRepartidor(envio, replacement);process(delivery);
        assertThat(assigned()).isEqualTo(replacement);assertThat(assignments.find(operation.operationId()).status()).isEqualTo("SUPERSEDED");
        assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio)).hasSize(1);
    }

    @Test
    void oldResultCannotClearOrOverwriteNewRequest() throws Exception {
        var old = message(body(operation, "AVAILABLE"));UUID replacement = UUID.randomUUID();
        var newer = assignments.accept(envio, replacement, UUID.randomUUID().toString());process(old);
        assertThat(assignments.find(newer.operationId()).status()).isEqualTo("PENDING");
        process(message(body(newer, "AVAILABLE")));assertThat(assigned()).isEqualTo(replacement);
        assertThat(assignments.find(operation.operationId()).status()).isEqualTo("SUPERSEDED");
    }

    @Test
    void failedPutDoesNotInvalidatePendingOperation() throws Exception {
        UUID replacement = UUID.randomUUID();doThrow(new com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException()).when(courierClient).verificarDisponible(replacement);
        assertThatThrownBy(() -> shipmentService.asignarRepartidor(envio, replacement)).isInstanceOf(com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException.class);
        assertThat(assignments.find(operation.operationId()).status()).isEqualTo("PENDING");
        process(message(body(operation, "AVAILABLE")));assertThat(assigned()).isEqualTo(rider);
    }

    @Test
    void failedHistoryWriteRollsBackInvalidationTogetherWithCancellation() throws Exception {
        ejecutarDdlComoAdministrador("CREATE TRIGGER fallo_historial_prueba BEFORE INSERT ON historial_estados_envio FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture'");
        try {
            assertThatThrownBy(() -> shipmentService.cancelar(envio)).satisfies(error -> assertThat(causaSql(error).getSQLState()).isEqualTo("45000"));
            assertThat(assignments.find(operation.operationId()).status()).isEqualTo("PENDING");
            assertThat(envios.findById(envio).orElseThrow().getEstado().name()).isEqualTo("PENDIENTE");
            assertThat(jdbc.queryForObject("SELECT assignment_version FROM envios", Long.class)).isEqualTo(1);
        } finally { ejecutarDdlComoAdministrador("DROP TRIGGER fallo_historial_prueba"); }
        process(message(body(operation, "AVAILABLE")));assertThat(assigned()).isEqualTo(rider);
    }

    @Test
    void expiredResultIsConsumedWithoutAssignment() throws Exception {
        var delivery = message(body(operation, "AVAILABLE"));afterDeadline().process(AvailabilityResult.read(delivery, mapper), MessageDigest.getInstance("SHA-256").digest(delivery.getBody()));
        assertThat(assignments.find(operation.operationId()).status()).isEqualTo("EXPIRED");assertThat(assigned()).isNull();assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void expiryDoesNotNeedBrokerOrResult() {
        afterDeadline().expire(operation.operationId());
        assertThat(assignments.find(operation.operationId()).status()).isEqualTo("EXPIRED");assertThat(assigned()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"causationId", "repartidorId", "envioId", "assignmentVersion", "expiresAt"})
    void rejectsResultThatDoesNotMatchAcceptedIntention(String field) {
        var body = body(operation, "AVAILABLE");body.put(field, field.equals("assignmentVersion") ? 2 : field.equals("expiresAt") ? operation.expiresAt().plusSeconds(1).toString() : UUID.randomUUID().toString());
        assertThatThrownBy(() -> process(message(body))).isInstanceOf(InvalidAssignmentResultException.class);
        assertThat(assigned()).isNull();assertThat(assignments.find(operation.operationId()).status()).isEqualTo("PENDING");assertThat(inboxCount()).isZero();
    }

    @Test
    void concurrentDuplicateProducesOneInboxAndAssignment() throws Exception {
        var delivery = message(body(operation, "AVAILABLE"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { process(delivery);return true; });var b = executor.submit(() -> { process(delivery);return true; });
            assertThat(a.get()).isTrue();assertThat(b.get()).isTrue();
        }
        assertThat(inboxCount()).isEqualTo(1);assertThat(assigned()).isEqualTo(rider);
    }

    @Test
    void concurrentPutAlwaysLeavesTheExplicitReplacement() throws Exception {
        var delivery = message(body(operation, "AVAILABLE"));UUID replacement = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { process(delivery);return true; });
            var b = executor.submit(() -> { SecurityTestActors.admin();try { shipmentService.asignarRepartidor(envio, replacement);return true; } finally { SecurityTestActors.clear(); } });
            assertThat(a.get()).isTrue();assertThat(b.get()).isTrue();
        }
        assertThat(assigned()).isEqualTo(replacement);assertThat(inboxCount()).isEqualTo(1);
        assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio)).hasSize(1);
    }

    @Test
    void concurrentCancelKeepsTerminalStateWithoutNewAssignmentAfterCancellation() throws Exception {
        var delivery = message(body(operation, "AVAILABLE"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { process(delivery);return true; });
            var b = executor.submit(() -> { SecurityTestActors.admin();try { shipmentService.cancelar(envio);return true; } finally { SecurityTestActors.clear(); } });
            assertThat(a.get()).isTrue();assertThat(b.get()).isTrue();
        }
        assertThat(envios.findById(envio).orElseThrow().getEstado().name()).isEqualTo("CANCELADO");
        String status = assignments.find(operation.operationId()).status();assertThat(status).isIn("APPLIED", "SUPERSEDED");
        if (status.equals("SUPERSEDED")) assertThat(assigned()).isNull();else assertThat(assigned()).isEqualTo(rider);
        assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio)).hasSize(2);
    }

    @Test
    void ackFailureAfterCommitCanBeRedeliveredWithoutAnotherEffect() throws Exception {
        var delivery = message(body(operation, "AVAILABLE"));var channel = mock(com.rabbitmq.client.Channel.class);
        doThrow(new java.io.IOException("fixture")).when(channel).basicAck(1, false);
        var listener = new AssignmentResultListener(results, mapper, mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));
        assertThatThrownBy(() -> listener.onMessage(delivery, channel)).isInstanceOf(java.io.IOException.class);
        var updated = envios.findById(envio).orElseThrow().getUpdatedAt();listener.onMessage(delivery, mock(com.rabbitmq.client.Channel.class));
        assertThat(inboxCount()).isEqualTo(1);assertThat(envios.findById(envio).orElseThrow().getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    void inboxFailureRollsBackAssignmentAndOperationStatusTogether() throws Exception {
        ejecutarDdlComoAdministrador("CREATE TRIGGER fallo_result_inbox BEFORE INSERT ON assignment_inbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture'");
        var delivery = message(body(operation, "AVAILABLE"));
        try {
            assertThatThrownBy(() -> process(delivery)).satisfies(error -> assertThat(causaSql(error).getSQLState()).isEqualTo("45000"));
            assertThat(assigned()).isNull();assertThat(inboxCount()).isZero();
            assertThat(assignments.find(operation.operationId()).status()).isEqualTo("PENDING");
        } finally { ejecutarDdlComoAdministrador("DROP TRIGGER fallo_result_inbox"); }
        process(delivery);assertThat(assigned()).isEqualTo(rider);assertThat(inboxCount()).isEqualTo(1);
    }

}
