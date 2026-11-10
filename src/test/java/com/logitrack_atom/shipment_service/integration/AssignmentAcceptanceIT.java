package com.logitrack_atom.shipment_service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.logitrack_atom.shipment_service.assignment.AssignmentService;
import com.logitrack_atom.shipment_service.assignment.OutboxStore;
import com.logitrack_atom.shipment_service.support.SecurityTestActors;
import com.logitrack_atom.shipment_service.support.TestRsaKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

/** Aceptación real MySQL de commit, rollback, replay y leases; broker/publicador desactivados. */
@SpringBootTest(properties = {"logitrack.assignment.enabled=true", "logitrack.assignment.publisher-enabled=false"})
class AssignmentAcceptanceIT extends MySqlIntegrationSupport {
    @Autowired AssignmentService assignments;
    @Autowired OutboxStore outbox;
    private UUID envio;
    private final UUID rider = UUID.randomUUID();

    @BeforeEach
    void scenario() throws Exception {
        envio = shipmentService.registrar(IntegrationFixtures.solicitud("assignment@example.test")).id();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_operations'", Integer.class) == 0) {
            // Base descartable: DDL aditivo una vez, después de crear el esquema existente.
            for (String statement : Files.readString(Path.of("docker/mysql/mq/004-assignment-outbox.sql")).split(";"))
                if (!statement.isBlank()) jdbc.execute(statement);
            assertThat(envios.findById(envio)).isPresent();
            assertThat(jdbc.queryForObject("SELECT assignment_version FROM envios", Long.class)).isZero();
            assertThat(count("envios")).isEqualTo(1);
        }
    }

    private String endpoint() { return "/api/v1/envios/" + envio + "/solicitudes-asignacion"; }
    private String body(UUID id) { return "{\"repartidorId\":\"" + id + "\"}"; }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    @Test
    void acceptsAndPersistsBefore202WithoutCourierOrBroker() throws Exception {
        var response = mvc.perform(post(endpoint()).header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body(rider)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignmentVersion").value(1)).andReturn();
        String id = JsonPath.read(response.getResponse().getContentAsString(), "$.operationId");
        mvc.perform(get("/api/v1/operaciones-asignacion/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.operationId").value(id));
        assertThat(count("assignment_operations")).isEqualTo(1);
        assertThat(count("amqp_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.type')) FROM amqp_outbox", String.class))
                .isEqualTo("assignment.availability.requested.v1");
        assertThat(jdbc.queryForObject("SELECT published_at FROM amqp_outbox", java.sql.Timestamp.class)).isNull();
        assertThat(envios.findById(envio).orElseThrow().getRepartidorId()).isNull();
        verifyNoInteractions(courierClient);
    }

    @Test
    void replayAndConflictDoNotCreateAnotherOutbox() {
        String key = UUID.randomUUID().toString();
        var operation = assignments.accept(envio, rider, key);
        assertThat(assignments.accept(envio, rider, key)).isEqualTo(operation);
        assertThatThrownBy(() -> assignments.accept(envio, UUID.randomUUID(), key))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThat(count("assignment_operations")).isEqualTo(1);
        assertThat(count("amqp_outbox")).isEqualTo(1);
    }

    @Test
    void newerIntentionSupersedesPendingWithoutChangingShipment() {
        var first = assignments.accept(envio, rider, UUID.randomUUID().toString());
        var second = assignments.accept(envio, UUID.randomUUID(), UUID.randomUUID().toString());
        assertThat(second.assignmentVersion()).isEqualTo(2);
        assertThat(assignments.find(first.operationId()).status()).isEqualTo("SUPERSEDED");
        assertThat(envios.findById(envio).orElseThrow().getRepartidorId()).isNull();
        assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio)).hasSize(1);
    }

    @Test
    void transactionRollsBackOperationAndGenerationIfOutboxCannotPersist() throws Exception {
        ejecutarDdlComoAdministrador("CREATE TRIGGER fallo_outbox BEFORE INSERT ON amqp_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture'");
        try {
            assertThatThrownBy(() -> assignments.accept(envio, rider, UUID.randomUUID().toString())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("assignment_operations")).isZero();
            assertThat(count("amqp_outbox")).isZero();
            assertThat(jdbc.queryForObject("SELECT assignment_version FROM envios", Long.class)).isZero();
        } finally { ejecutarDdlComoAdministrador("DROP TRIGGER fallo_outbox"); }
    }

    @Test
    void concurrentSameKeyReturnsOneOperationAndOneCommand() throws Exception {
        String key = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { SecurityTestActors.admin(); try { return assignments.accept(envio, rider, key); } finally { SecurityTestActors.clear(); } });
            var b = executor.submit(() -> { SecurityTestActors.admin(); try { return assignments.accept(envio, rider, key); } finally { SecurityTestActors.clear(); } });
            assertThat(a.get().operationId()).isEqualTo(b.get().operationId());
        }
        assertThat(count("assignment_operations")).isEqualTo(1);
        assertThat(count("amqp_outbox")).isEqualTo(1);
    }

    @Test
    void leasePreventsAnotherClaimAndExpiredLeaseCanRecover() {
        assignments.accept(envio, rider, UUID.randomUUID().toString());
        var first = outbox.claim().orElseThrow();
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(outbox.claim()).isEmpty();
        jdbc.update("UPDATE amqp_outbox SET lease_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND),next_attempt_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)");
        var second = outbox.claim().orElseThrow();
        outbox.confirmed(first);
        assertThat(jdbc.queryForObject("SELECT published_at FROM amqp_outbox", java.sql.Timestamp.class)).isNull();
        outbox.confirmed(second);
        assertThat(jdbc.queryForObject("SELECT published_at FROM amqp_outbox", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void rejectsInvalidInputAndNonAdminWithoutRows() throws Exception {
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content(body(rider))).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint()).header("Idempotency-Key", "invalid").contentType(MediaType.APPLICATION_JSON).content(body(rider))).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint()).header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"repartidorId\":\""+rider+"\",\"accessToken\":\"ignored\"}"))
                .andExpect(status().isBadRequest());
        String client = TestRsaKeys.token(UUID.randomUUID(), "CLIENTE");
        mvc.perform(post(endpoint()).header("Authorization", "Bearer " + client).header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body(rider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operaciones-asignacion/" + UUID.randomUUID()).header("Authorization", "Bearer " + client)).andExpect(status().isForbidden());
        assertThat(count("assignment_operations")).isZero();
        assertThat(count("amqp_outbox")).isZero();
    }

    @Test
    void replaySurvivesShipmentStateChangeAndLocationUsesOnlyKnownGatewayPrefix() throws Exception {
        String key = UUID.randomUUID().toString();
        var op = assignments.accept(envio, rider, key);
        shipmentService.cancelar(envio);
        assertThat(assignments.accept(envio, rider, key).operationId()).isEqualTo(op.operationId());
        mvc.perform(post(endpoint()).header("Idempotency-Key", key).header("X-Forwarded-Prefix", "/applications/apishipment")
                        .contentType(MediaType.APPLICATION_JSON).content(body(rider)))
                .andExpect(status().isAccepted()).andExpect(header().string("Location", "/applications/apishipment/api/v1/operaciones-asignacion/" + op.operationId()));
    }

    @Test
    void sameKeyCompetingAcrossShipmentsConflictsAndRollsBackLosingGeneration() throws Exception {
        UUID other = shipmentService.registrar(IntegrationFixtures.solicitud("other-assignment@example.test")).id();
        String key = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(java.util.List.of(
                    (java.util.concurrent.Callable<Integer>) () -> acceptStatus(envio, key),
                    (java.util.concurrent.Callable<Integer>) () -> acceptStatus(other, key)));
            assertThat(java.util.List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(202, 409);
        }
        assertThat(count("assignment_operations")).isEqualTo(1);
        assertThat(count("amqp_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(assignment_version) FROM envios", Long.class)).isEqualTo(1);
    }

    private int acceptStatus(UUID id, String key) {
        SecurityTestActors.admin();
        try { assignments.accept(id, rider, key); return 202; }
        catch (org.springframework.web.server.ResponseStatusException error) { return error.getStatusCode().value(); }
        finally { SecurityTestActors.clear(); }
    }

    @Test
    void idempotencyKeyBelongsToActorAndNotToAnotherAdmin() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = assignments.accept(envio, rider, key);
        String secondAdmin = TestRsaKeys.token(UUID.randomUUID(), "ADMIN");
        var result = mvc.perform(post(endpoint()).header("Authorization", "Bearer " + secondAdmin)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body(rider)))
                .andExpect(status().isAccepted()).andReturn();
        String secondId = JsonPath.read(result.getResponse().getContentAsString(), "$.operationId");
        assertThat(secondId).isNotEqualTo(first.operationId().toString());
        assertThat(count("assignment_operations")).isEqualTo(2);
        assertThat(count("amqp_outbox")).isEqualTo(2);
    }

}
