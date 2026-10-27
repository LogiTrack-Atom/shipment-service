package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.client.CourierPendienteClient;
import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.solicitud;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

/**
 * Verifica independencia de consultas, UUID remotos y rechazo sin escrituras.
 * @author MaxDz
 * @version 0.2.0
 */
class ShipmentCourierBoundaryIT extends MySqlIntegrationSupport {
    @Test
    void lecturasConUuidAsignadoNoConsultanCourierAunqueEsteFueraDeServicio() throws Exception {
        var creado = shipmentService.registrar(solicitud("independencia@example.test"));
        UUID remoto = UUID.randomUUID();
        nuevaTransaccion(() -> { envios.findById(creado.id()).orElseThrow().setRepartidorId(remoto); return null; });
        doThrow(new CourierNoDisponibleException()).when(courierClient).verificarDisponible(any());
        for (String ruta : new String[]{"/api/v1/envios/"+creado.id(),
                "/api/v1/envios/seguimiento/"+creado.codigoSeguimiento()}) {
            mvc.perform(get(ruta)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.repartidorId").value(remoto.toString()))
                    .andExpect(jsonPath("$.repartidor").doesNotExist());
        }
        assertThat(shipmentService.listarPorEstado(null)).extracting("repartidorId").containsExactly(remoto);
        assertThat(shipmentService.obtenerHistorial(creado.id())).hasSize(1);
        verifyNoInteractions(courierClient);
        assertThat(entityManager.getMetamodel().getEntities()).extracting(EntityType::getName)
                .doesNotContain("Repartidor");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='repartidores'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='envios'
                AND COLUMN_NAME='repartidor_id' AND REFERENCED_TABLE_NAME IS NOT NULL
                """, Integer.class)).isZero();
        for (String ruta : new String[]{"/api/v1/repartidores", "/api/v1/repartidores/"+remoto}) {
            mvc.perform(get(ruta)).andExpect(status().isNotFound());
        }
    }

    @Test
    void indisponibilidadNoCambiaAsignacionEstadoAuditoriaNiHistorial() throws Exception {
        var creado = shipmentService.registrar(solicitud("sin-mutacion@example.test"));
        UUID anterior = UUID.randomUUID();
        nuevaTransaccion(() -> { envios.findById(creado.id()).orElseThrow().setRepartidorId(anterior); return null; });
        var antes = shipmentService.obtenerPorId(creado.id());
        doAnswer(call -> { new CourierPendienteClient().verificarDisponible(call.getArgument(0)); return null; })
                .when(courierClient).verificarDisponible(any());
        mvc.perform(put("/api/v1/envios/{id}/repartidor", creado.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"repartidorId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensaje").value("No se pudo verificar la disponibilidad del repartidor"));
        assertThrows(CourierNoDisponibleException.class,
                () -> shipmentService.cambiarEstado(creado.id(), EstadoEnvio.EN_RUTA));
        assertThat(shipmentService.obtenerPorId(creado.id())).isEqualTo(antes);
        assertThat(shipmentService.obtenerHistorial(creado.id())).hasSize(1);
    }
}
