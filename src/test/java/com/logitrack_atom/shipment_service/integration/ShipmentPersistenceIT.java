package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.entity.BaseEntity;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica UUID remotos y restricciones locales mediante MySQL real.
 * @author MaxDz
 * @version 0.2.0
 */
class ShipmentPersistenceIT extends MySqlIntegrationSupport {

    @Test
    void recuperaUuidAuditoriaRelacionesYDireccionesTrasFlushClear() {
        var envio = shipmentService.registrar(solicitud("persistencia@example.test"));
        UUID repartidor = UUID.randomUUID();
        shipmentService.asignarRepartidor(envio.id(), repartidor);

        nuevaTransaccion(() -> {
            entityManager.flush();
            entityManager.clear();
            Envio leido = entityManager.find(Envio.class, envio.id());
            comprobarAuditoria(leido);
            comprobarAuditoria(leido.getCliente());
            var historial = historiales.findByEnvioIdOrderByFechaCambioAsc(envio.id());
            assertThat(historial).hasSize(1);
            comprobarAuditoria(historial.getFirst());
            assertThat(historial.getFirst().getEnvio().getId()).isEqualTo(leido.getId());
            assertThat(historial.getFirst().getEstadoAnterior()).isNull();
            assertThat(historial.getFirst().getEstadoNuevo()).isEqualTo(EstadoEnvio.PENDIENTE);
            assertThat(historial.getFirst().getFechaCambio()).isNotNull();
            assertThat(leido.getCliente().getEmail()).isEqualTo("persistencia@example.test");
            assertThat(leido.getCliente().getTelefono()).isNull();
            assertThat(leido.getRepartidorId()).isEqualTo(repartidor);
            assertThat(leido.getOrigen()).extracting("calle", "numero", "ciudad", "codigoPostal")
                    .containsExactly("Belgrano", "100", "Jujuy", "Y4600");
            assertThat(leido.getDestino()).extracting("calle", "numero", "ciudad", "codigoPostal")
                    .containsExactly("Alvear", "200", "Salta", "A4400");
            assertThat(leido.getPesoKg()).isEqualByComparingTo("1.250");
            assertThat(leido.getAnchoCm()).isEqualByComparingTo("25.25");
            assertThat(leido.getAltoCm()).isEqualByComparingTo("15.50");
            assertThat(leido.getLargoCm()).isEqualByComparingTo("30.75");
            return null;
        });
        for (String table : new String[]{"clientes", "envios", "historial_estados_envio"}) {
            assertThat(jdbc.queryForObject("SELECT OCTET_LENGTH(id) FROM " + table, Integer.class)).isEqualTo(16);
        }
    }

    @Test
    void actualizaAuditoriaSinModificarFechaDeCreacion() {
        UUID id = clientes.saveAndFlush(cliente("auditoria@example.test")).getId();
        Instant creado = clientes.findById(id).orElseThrow().getCreatedAt();
        Instant actualizado = clientes.findById(id).orElseThrow().getUpdatedAt();
        nuevaTransaccion(() -> {
            Cliente cliente = entityManager.find(Cliente.class, id);
            cliente.setTelefono("3880000000");
            entityManager.flush();
            entityManager.clear();
            Cliente leido = entityManager.find(Cliente.class, id);
            assertThat(leido.getCreatedAt()).isEqualTo(creado);
            assertThat(leido.getUpdatedAt()).isAfter(actualizado);
            assertThat(leido.getTelefono()).isEqualTo("3880000000");
            return null;
        });
    }

    @Test
    void consultaPorUuidTrackingYEstadoSinReutilizarElContextoDePersistencia() {
        var pendiente = shipmentService.registrar(solicitud("busquedas@example.test"));
        var cancelado = shipmentService.registrar(solicitud("busquedas@example.test"));
        shipmentService.cancelar(cancelado.id());
        assertThat(shipmentService.obtenerPorId(pendiente.id()).codigoSeguimiento())
                .isEqualTo(pendiente.codigoSeguimiento());
        assertThat(shipmentService.obtenerPorCodigoSeguimiento(cancelado.codigoSeguimiento()).id())
                .isEqualTo(cancelado.id());
        assertThat(shipmentService.listarPorEstado(EstadoEnvio.PENDIENTE))
                .extracting("id").containsExactly(pendiente.id());
        assertThat(shipmentService.listarPorEstado(EstadoEnvio.CANCELADO))
                .extracting("id").containsExactly(cancelado.id());
        assertThat(clientes.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "tracking"})
    void unicidadSeImponeEnMySqlYNoDejaFilasParciales(String campo) {
        var original = shipmentService.registrar(solicitud("unico@example.test"));
        var error = assertThrows(DataIntegrityViolationException.class, () -> nuevaTransaccion(() -> {
            if (campo.equals("email")) {
                clientes.saveAndFlush(cliente("unico@example.test"));
            } else {
                Cliente cliente = clientes.findByEmail("unico@example.test").orElseThrow();
                envios.saveAndFlush(envio(cliente, original.codigoSeguimiento()));
            }
            return null;
        }));
        assertThat(causaSql(error).getErrorCode()).isEqualTo(1062);
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(envios.count()).isEqualTo(1);
        assertThat(historiales.count()).isEqualTo(1);
    }

    @Test
    void ordenaElHistorialPorFechaAunqueLasInsercionesLleguenDesordenadas() {
        UUID id = nuevaTransaccion(() -> {
            Cliente cliente = clientes.saveAndFlush(cliente("orden@example.test"));
            Envio envio = envios.saveAndFlush(envio(cliente, "LT-ORDEN"));
            Instant inicio = Instant.parse("2026-10-01T12:00:00Z");
            historiales.save(new HistorialEstadoEnvio(envio, EstadoEnvio.EN_RUTA, EstadoEnvio.ENTREGADO, inicio.plusSeconds(2)));
            historiales.save(new HistorialEstadoEnvio(envio, null, EstadoEnvio.PENDIENTE, inicio));
            historiales.save(new HistorialEstadoEnvio(envio, EstadoEnvio.PENDIENTE, EstadoEnvio.EN_RUTA, inicio.plusSeconds(1)));
            entityManager.flush();
            entityManager.clear();
            return envio.getId();
        });
        var historial = shipmentService.obtenerHistorial(id);
        assertThat(historial).extracting("estadoNuevo")
                .containsExactly(EstadoEnvio.PENDIENTE, EstadoEnvio.EN_RUTA, EstadoEnvio.ENTREGADO);
        assertThat(historial).extracting("fechaCambio").isSorted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "envio"})
    void rechazaClavesForaneasInexistentes(String relacion) {
        UUID clienteId = clientes.saveAndFlush(cliente("fk@example.test")).getId();
        var error = assertThrows(DataIntegrityViolationException.class, () -> nuevaTransaccion(() -> {
            Cliente cliente = entityManager.getReference(Cliente.class, clienteId);
            if (relacion.equals("envio")) {
                Envio inexistente = entityManager.getReference(Envio.class, UUID.randomUUID());
                historiales.saveAndFlush(new HistorialEstadoEnvio(inexistente, null, EstadoEnvio.PENDIENTE, Instant.now()));
            } else {
                Envio nuevo = envio(cliente, "LT-FK");
                if (relacion.equals("cliente")) nuevo.setCliente(entityManager.getReference(Cliente.class, UUID.randomUUID()));
                envios.saveAndFlush(nuevo);
            }
            return null;
        }));
        assertThat(causaSql(error).getErrorCode()).isEqualTo(1452);
        assertThat(envios.count()).isZero();
        assertThat(historiales.count()).isZero();
    }

    private static void comprobarAuditoria(BaseEntity entidad) {
        assertThat(entidad.getId()).isNotNull();
        assertThat(entidad.getCreatedAt()).isNotNull();
        assertThat(entidad.getUpdatedAt()).isAfterOrEqualTo(entidad.getCreatedAt());
    }
}
