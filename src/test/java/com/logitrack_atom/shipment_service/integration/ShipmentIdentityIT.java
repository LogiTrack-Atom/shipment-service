package com.logitrack_atom.shipment_service.integration;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.solicitud;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.logitrack_atom.shipment_service.support.SecurityTestActors;
import com.logitrack_atom.shipment_service.support.TestRsaKeys;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifica asociaciones con proveedores HTTP controlados, JWT real y commits MySQL.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class ShipmentIdentityIT extends MySqlIntegrationSupport {
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static volatile int remoteStatus = 200;
    private static volatile String securityBody, courierBody, lastBearer, lastPath;
    private static final HttpServer SERVER = server();

    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        CALLS.incrementAndGet();
                        lastBearer = exchange.getRequestHeaders().getFirst("Authorization");
                        lastPath = exchange.getRequestURI().getPath();
                        byte[] body =
                                (lastPath.contains("/usuarios/") ? securityBody : courierBody)
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(remoteStatus, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            server.start();
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(() -> server.stop(0), "limpieza-proveedores-identidad"));
            return server;
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry r) {
        String url = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        r.add("clients.security.base-url", () -> url);
        r.add("clients.courier.base-url", () -> url);
        r.add("clients.security.connect-timeout", () -> "200ms");
        r.add("clients.security.read-timeout", () -> "300ms");
    }

    private UUID clienteId, user, repartidor;

    @Override
    protected boolean autenticarPorDefecto() {
        return false;
    }

    @BeforeEach
    void providerState() {
        CALLS.set(0);
        remoteStatus = 200;
        lastBearer = null;
        lastPath = null;
        var envio = shipmentService.registrar(solicitud("historico@example.test"));
        clienteId =
                nuevaTransaccion(
                        () -> envios.findById(envio.id()).orElseThrow().getCliente().getId());
        user = UUID.randomUUID();
        repartidor = UUID.randomUUID();
        securityBody = account("CLIENTE");
        courierBody = "{\"repartidorId\":\"" + repartidor + "\",\"usuarioId\":\"" + user + "\"}";
    }

    private String account(String role) {
        return "{\"id\":\"" + user + "\",\"roles\":[\"" + role + "\"]}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder link(
            boolean courier, UUID id, UUID account, String role) {
        return put(
                        courier
                                ? "/api/v1/asociaciones/repartidores/{id}"
                                : "/api/v1/clientes/{id}/usuario",
                        id)
                .header(
                        "Authorization",
                        "Bearer " + TestRsaKeys.token(SecurityTestActors.ADMIN, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":\"" + account + "\"}");
    }

    @Test
    void adminVinculaClienteExistenteSinCambiarDatosYRepetirEsLocal() throws Exception {
        var before = clientes.findById(clienteId).orElseThrow();
        mvc.perform(link(false, clienteId, user, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(user.toString()));
        assertThat(clientes.findById(clienteId).orElseThrow().getEmail())
                .isEqualTo(before.getEmail());
        assertThat(CALLS.get()).isEqualTo(1);
        assertThat(lastPath).isEqualTo("/api/v1/usuarios/" + user);
        assertThat(lastBearer).startsWith("Bearer ");
        remoteStatus = 503;
        mvc.perform(link(false, clienteId, user, "ADMIN")).andExpect(status().isOk());
        assertThat(CALLS.get()).isEqualTo(1);
        mvc.perform(link(false, clienteId, UUID.randomUUID(), "ADMIN"))
                .andExpect(status().isConflict());
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void repartidorRequiereDosVerificacionesYProyeccionPermaneceLocal() throws Exception {
        securityBody = account("REPARTIDOR");
        mvc.perform(link(true, repartidor, user, "ADMIN")).andExpect(status().isOk());
        assertThat(CALLS.get()).isEqualTo(2);
        assertThat(lastPath).isEqualTo("/api/v1/repartidores/" + repartidor + "/usuario");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repartidor_usuarios", Long.class))
                .isEqualTo(1);
        remoteStatus = 503;
        mvc.perform(link(true, repartidor, user, "ADMIN")).andExpect(status().isOk());
        assertThat(CALLS.get()).isEqualTo(2);
        mvc.perform(link(true, repartidor, UUID.randomUUID(), "ADMIN"))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "REPARTIDOR"})
    void rolesNoAdministrativosNoPuedenVincularYNoLlamanProveedores(String role) throws Exception {
        mvc.perform(link(false, clienteId, user, role)).andExpect(status().isForbidden());
        mvc.perform(link(true, repartidor, user, role)).andExpect(status().isForbidden());
        assertThat(CALLS.get()).isZero();
        assertThat(clientes.findById(clienteId).orElseThrow().getUsuarioId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    void errorRemotoNoVinculaNiAlteraDatos(int code) throws Exception {
        remoteStatus = code;
        int expected = code == 404 ? 404 : code == 429 || code >= 500 ? 503 : 502;
        mvc.perform(link(false, clienteId, user, "ADMIN"))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.status").value(expected));
        assertThat(clientes.findById(clienteId).orElseThrow().getUsuarioId()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repartidor_usuarios", Long.class))
                .isZero();
    }

    @Test
    void rolIncompatibleEIdentidadDistintaSonRechazados() throws Exception {
        securityBody = account("ADMIN");
        mvc.perform(link(false, clienteId, user, "ADMIN"))
                .andExpect(status().isUnprocessableContent());
        securityBody = account("REPARTIDOR");
        courierBody =
                "{\"repartidorId\":\""
                        + repartidor
                        + "\",\"usuarioId\":\""
                        + UUID.randomUUID()
                        + "\"}";
        mvc.perform(link(true, repartidor, user, "ADMIN")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repartidor_usuarios", Long.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "[]",
                "{",
                "{\"id\":\"ajeno\",\"roles\":[\"CLIENTE\"]}",
                "{\"id\":null,\"roles\":[\"CLIENTE\"]}"
            })
    void contratoRemotoInvalidoSeRechazaSinEscritura(String body) throws Exception {
        securityBody = body;
        mvc.perform(link(false, clienteId, user, "ADMIN")).andExpect(status().isBadGateway());
        assertThat(clientes.findById(clienteId).orElseThrow().getUsuarioId()).isNull();
    }

    @Test
    void cuentaUnicaNoPuedeVincularDosClientes() throws Exception {
        mvc.perform(link(false, clienteId, user, "ADMIN")).andExpect(status().isOk());
        var segundo = shipmentService.registrar(solicitud("segundo@example.test"));
        UUID id =
                nuevaTransaccion(
                        () -> envios.findById(segundo.id()).orElseThrow().getCliente().getId());
        mvc.perform(link(false, id, user, "ADMIN")).andExpect(status().isConflict());
        assertThat(clientes.findById(id).orElseThrow().getUsuarioId()).isNull();
    }
}
