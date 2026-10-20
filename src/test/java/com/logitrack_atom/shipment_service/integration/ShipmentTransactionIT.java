package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import org.junit.jupiter.api.Test;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.solicitud;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipmentTransactionIT extends MySqlIntegrationSupport {

    @Test
    void revierteEstadoEHistorialCuandoMySqlRechazaElNuevoEvento() throws Exception {
        var envio = shipmentService.registrar(solicitud("rollback@example.test"));
        var repartidor = repartidorService.registrar(new RepartidorRequest("Luis", "MOTO"));
        shipmentService.asignarRepartidor(envio.id(), repartidor.id());
        var actualizadoAntes = shipmentService.obtenerPorId(envio.id()).updatedAt();
        crearFalloDeHistorial();
        try {
            var error = assertThrows(RuntimeException.class,
                    () -> shipmentService.cambiarEstado(envio.id(), EstadoEnvio.EN_RUTA));
            assertThat(causaSql(error).getErrorCode()).isEqualTo(1644);
            assertThat(causaSql(error).getMessage()).contains("Fallo de historial inducido por prueba");
            nuevaTransaccion(() -> {
                entityManager.clear();
                var recuperado = envios.findById(envio.id()).orElseThrow();
                assertThat(recuperado.getEstado()).isEqualTo(EstadoEnvio.PENDIENTE);
                assertThat(recuperado.getUpdatedAt()).isEqualTo(actualizadoAntes);
                assertThat(recuperado.getRepartidor().getId()).isEqualTo(repartidor.id());
                assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(envio.id()))
                        .extracting("estadoNuevo").containsExactly(EstadoEnvio.PENDIENTE);
                return null;
            });
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fallo_historial_prueba");
        }
        // El mismo cambio debe completarse una vez retirada la falla de la base.
        shipmentService.cambiarEstado(envio.id(), EstadoEnvio.EN_RUTA);
        assertThat(shipmentService.obtenerPorId(envio.id()).estado()).isEqualTo(EstadoEnvio.EN_RUTA);
        assertThat(shipmentService.obtenerHistorial(envio.id())).hasSize(2);
    }

    @Test
    void revierteTambienClienteYEnvioSiFallaElHistorialInicial() throws Exception {
        crearFalloDeHistorial();
        try {
            var error = assertThrows(RuntimeException.class,
                    () -> shipmentService.registrar(solicitud("alta-fallida@example.test")));
            assertThat(causaSql(error).getErrorCode()).isEqualTo(1644);
            nuevaTransaccion(() -> {
                entityManager.clear();
                assertThat(clientes.count()).isZero();
                assertThat(envios.count()).isZero();
                assertThat(historiales.count()).isZero();
                return null;
            });
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fallo_historial_prueba");
        }
    }

    private void crearFalloDeHistorial() throws Exception {
        ejecutarDdlComoAdministrador("""
                CREATE TRIGGER fallo_historial_prueba BEFORE INSERT ON historial_estados_envio
                FOR EACH ROW SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Fallo de historial inducido por prueba'
                """);
    }
}
