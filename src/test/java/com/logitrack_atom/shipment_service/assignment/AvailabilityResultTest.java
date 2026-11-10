package com.logitrack_atom.shipment_service.assignment;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Contrato de resultados: validez estructural independiente de la vigencia que decide SQL. */
class AvailabilityResultTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsObservationAfterDeadlineForLocalExpiryDecision() {
        var body = AssignmentResultFixtures.body(UUID.randomUUID());
        body.put("expiresAt", Instant.now().minusSeconds(10).toString());
        assertThat(AvailabilityResult.read(AssignmentResultFixtures.message(body, mapper), mapper).availability()).isEqualTo("AVAILABLE");
    }

    @Test
    void rejectsUnknownAvailabilityAndFields() {
        var body = AssignmentResultFixtures.body(UUID.randomUUID());body.put("availability", "RESERVED");
        assertThatThrownBy(() -> AvailabilityResult.read(AssignmentResultFixtures.message(body, mapper), mapper)).isInstanceOf(InvalidAssignmentResultException.class);
        body.put("availability", "AVAILABLE");body.put("accessToken", "fixture");
        assertThatThrownBy(() -> AvailabilityResult.read(AssignmentResultFixtures.message(body, mapper), mapper)).isInstanceOf(InvalidAssignmentResultException.class);
    }

    @Test
    void rejectsInvalidCausationAndMismatchedCorrelation() {
        var body = AssignmentResultFixtures.body(UUID.randomUUID());body.put("causationId", "invalid");
        assertThatThrownBy(() -> AvailabilityResult.read(AssignmentResultFixtures.message(body, mapper), mapper)).isInstanceOf(InvalidAssignmentResultException.class);
        body.put("causationId", UUID.randomUUID().toString());
        var message = AssignmentResultFixtures.message(body, mapper);message.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
        assertThatThrownBy(() -> AvailabilityResult.read(message, mapper)).isInstanceOf(InvalidAssignmentResultException.class);
    }
}
