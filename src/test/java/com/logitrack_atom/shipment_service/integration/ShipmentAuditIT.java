package com.logitrack_atom.shipment_service.integration;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.envioJson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Contrasta la auditoría devuelta por HTTP con lecturas nuevas y MySQL DATETIME(6).
 *
 * @author MaxDz
 * @version 0.2.0
 */
class ShipmentAuditIT extends MySqlIntegrationSupport {
    @Test
    void altaDevuelveLasMismasFechasQueSuLecturaConfirmada() throws Exception {
        comprobarRespuesta(crear());
    }

    @Test
    void asignacionReasignacionRutaYEntregaDevuelvenLaAuditoriaPersistida() throws Exception {
        String body = crear();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        Instant created = Instant.parse(JsonPath.read(body, "$.createdAt"));
        for (UUID repartidor : new UUID[] {UUID.randomUUID(), UUID.randomUUID()}) {
            body =
                    mvc.perform(
                                    put("/api/v1/envios/{id}/repartidor", id)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"repartidorId\":\"" + repartidor + "\"}"))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            comprobarRespuesta(body);
            assertThat(shipmentService.obtenerHistorial(id)).hasSize(1);
        }
        for (String estado : new String[] {"EN_RUTA", "ENTREGADO"}) {
            body =
                    mvc.perform(
                                    patch("/api/v1/envios/{id}/estado", id)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"estado\":\"" + estado + "\"}"))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            comprobarRespuesta(body);
        }
        assertThat(shipmentService.obtenerPorId(id).createdAt()).isEqualTo(created);
        assertThat(shipmentService.obtenerHistorial(id))
                .hasSize(3)
                .allSatisfy(
                        event ->
                                assertThat(event.fechaCambio())
                                        .isEqualTo(
                                                event.fechaCambio()
                                                        .truncatedTo(ChronoUnit.MICROS)));
    }

    @Test
    void cancelacionYAsignacionRepetidaConservanFechasCoherentes() throws Exception {
        String body = crear();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        UUID repartidor = UUID.randomUUID();
        EnvioResponse asignado = shipmentService.asignarRepartidor(id, repartidor);
        EnvioResponse repetido = shipmentService.asignarRepartidor(id, repartidor);
        assertThat(repetido).isEqualTo(asignado);
        body =
                mvc.perform(patch("/api/v1/envios/{id}/cancelacion", id))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        comprobarRespuesta(body);
    }

    private String crear() throws Exception {
        return mvc.perform(
                        post("/api/v1/envios")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(envioJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void comprobarRespuesta(String body) throws Exception {
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        String leido =
                mvc.perform(get("/api/v1/envios/{id}", id))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        for (String field : new String[] {"createdAt", "updatedAt"}) {
            Instant returned = Instant.parse(JsonPath.read(body, "$." + field));
            assertThat(Instant.parse(JsonPath.read(leido, "$." + field)))
                    .as(field + " leído después de commit")
                    .isEqualTo(returned);
            assertThat(returned)
                    .as(field + " DATETIME(6)")
                    .isEqualTo(returned.truncatedTo(ChronoUnit.MICROS));
        }
        nuevaTransaccion(
                () -> {
                    entityManager.clear();
                    var stored = envios.findById(id).orElseThrow();
                    assertThat(stored.getCreatedAt())
                            .isEqualTo(Instant.parse(JsonPath.read(body, "$.createdAt")));
                    assertThat(stored.getUpdatedAt())
                            .isEqualTo(Instant.parse(JsonPath.read(body, "$.updatedAt")));
                    return null;
                });
    }
}
