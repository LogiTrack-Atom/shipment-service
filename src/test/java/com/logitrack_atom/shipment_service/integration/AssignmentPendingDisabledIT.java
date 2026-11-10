package com.logitrack_atom.shipment_service.integration;

import static org.assertj.core.api.Assertions.*;
import com.logitrack_atom.shipment_service.assignment.*;
import com.logitrack_atom.shipment_service.security.ShipmentAccess;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** Las guardas de pendientes previos siguen siendo válidas con el transporte/API de MQ deshabilitados. */
class AssignmentPendingDisabledIT extends MySqlIntegrationSupport {
    @Autowired AssignmentStore store;
    @Autowired AssignmentSchema schema;
    @Autowired AssignmentResultsService results;
    @Autowired AssignmentService disabled;
    @Autowired ShipmentAccess access;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    private UUID envio;
    private final UUID rider = UUID.randomUUID();
    private AssignmentOperation pending;

    @BeforeEach
    void previousPendingFixture() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_operations'", Integer.class) == 0) apply("004-assignment-outbox.sql");
        envio = shipmentService.registrar(IntegrationFixtures.solicitud("pending-disabled@example.test")).id();
        // Simula un registro confirmado por una instancia habilitada anterior; no abre la API deshabilitada.
        pending = new AssignmentService(store, access, mapper, manager, results, true).accept(envio, rider, UUID.randomUUID().toString());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_inbox'", Integer.class) == 0) apply("005-assignment-results-inbox.sql");
        assertThat(store.find(pending.operationId()).orElseThrow().status()).isEqualTo("PENDING");
    }

    private void apply(String file) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.FileSystemResource(Path.of("docker/mysql/mq", file)));return null;
        });
    }

    @Test
    void disabledModuleStillInvalidatesPriorPendingWhenPutSucceeds() throws Exception {
        assertThatThrownBy(() -> disabled.accept(envio, rider, UUID.randomUUID().toString())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("503");
        UUID replacement = UUID.randomUUID();shipmentService.asignarRepartidor(envio, replacement);
        assertThat(disabled.find(pending.operationId()).status()).isEqualTo("SUPERSEDED");
        String commandJson = jdbc.queryForObject("SELECT payload FROM amqp_outbox", String.class);
        Map<String, Object> body = new LinkedHashMap<>(mapper.readValue(commandJson, Map.class));
        body.put("causationId", body.get("messageId"));body.put("messageId", UUID.randomUUID().toString());body.put("type", "assignment.availability.checked.v1");
        body.put("availability", "AVAILABLE");body.put("occurredAt", Instant.now().toString());
        var delivery = com.logitrack_atom.shipment_service.assignment.AssignmentResultFixtures.message(body, mapper);
        results.process(AvailabilityResult.read(delivery, mapper), MessageDigest.getInstance("SHA-256").digest(delivery.getBody()));
        assertThat(envios.findById(envio).orElseThrow().getRepartidorId()).isEqualTo(replacement);
    }

    @Test
    void localReadMaterializesExpiryWithoutEnablingTransport() {
        var expiredClock = Clock.fixed(pending.expiresAt().plusSeconds(1), ZoneOffset.UTC);
        var localResults = new AssignmentResultsService(store, schema, manager, expiredClock);
        var localView = new AssignmentService(store, access, mapper, manager, localResults, false);
        assertThat(localView.find(pending.operationId()).status()).isEqualTo("EXPIRED");
        assertThat(envios.findById(envio).orElseThrow().getRepartidorId()).isNull();
        assertThat(jdbc.queryForObject("SELECT active_assignment_operation_id FROM envios", byte[].class)).isNull();
    }
}
