package com.logitrack_atom.shipment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;

/** El núcleo arranca sin credenciales/transporte MQ y rechaza nuevas solicitudes con 503. */
class AssignmentDisabledIT extends MySqlIntegrationSupport {
    @Autowired ApplicationContext context;

    @Test
    void noRabbitConnectionIsCreatedAndDisabledPostHasNoEffect() throws Exception {
        var envio = shipmentService.registrar(IntegrationFixtures.solicitud("disabled@example.test")).id();
        mvc.perform(post("/api/v1/envios/" + envio + "/solicitudes-asignacion")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repartidorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(context.getBeansOfType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.class)).isEmpty();
        assertThat(envios.findById(envio).orElseThrow().getRepartidorId()).isNull();
    }
}
