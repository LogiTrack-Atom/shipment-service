package com.logitrack_atom.shipment_service.integration;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.logitrack_atom.shipment_service.entity.RepartidorUsuario;
import com.logitrack_atom.shipment_service.repository.RepartidorUsuarioRepository;
import com.logitrack_atom.shipment_service.support.TestRsaKeys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

/**
 * Prueba autorización HTTP real con JWT firmados y pertenencia persistida en MySQL.
 *
 * <p>No reemplaza el decoder, la cadena de filtros ni el evaluador de acceso. Courier es un doble
 * del puerto de disponibilidad; las lecturas no lo invocan.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class ShipmentSecurityIT extends MySqlIntegrationSupport {
    private final UUID clienteA = UUID.randomUUID(), clienteB = UUID.randomUUID();
    private final UUID usuarioRepartidorA = UUID.randomUUID(),
            usuarioRepartidorB = UUID.randomUUID();
    private final UUID repartidorA = UUID.randomUUID(), repartidorB = UUID.randomUUID();
    @Autowired private RepartidorUsuarioRepository asociaciones;
    private UUID envioA, envioB;
    private String trackingA;

    @Override
    protected boolean autenticarPorDefecto() {
        return false;
    }

    @BeforeEach
    void escenario() {
        var a = shipmentService.registrar(solicitud("cliente-a@example.test"));
        var b = shipmentService.registrar(solicitud("cliente-b@example.test"));
        envioA = a.id();
        envioB = b.id();
        trackingA = a.codigoSeguimiento();
        nuevaTransaccion(
                () -> {
                    var ea = envios.findById(envioA).orElseThrow();
                    ea.getCliente().setUsuarioId(clienteA);
                    ea.setRepartidorId(repartidorA);
                    var eb = envios.findById(envioB).orElseThrow();
                    eb.getCliente().setUsuarioId(clienteB);
                    eb.setRepartidorId(repartidorB);
                    asociaciones.saveAndFlush(
                            new RepartidorUsuario(repartidorA, usuarioRepartidorA));
                    asociaciones.saveAndFlush(
                            new RepartidorUsuario(repartidorB, usuarioRepartidorB));
                    return null;
                });
    }

    private String bearer(UUID user, String... roles) {
        return "Bearer " + TestRsaKeys.token(user, roles);
    }

    @Test
    void clienteSoloConsultaSusEnviosDetalleTrackingHistorialYFiltro() throws Exception {
        comprobarLecturas(clienteA, "CLIENTE");
    }

    @Test
    void repartidorSoloConsultaSusAsignacionesConProveedoresDesconectados() throws Exception {
        comprobarLecturas(usuarioRepartidorA, "REPARTIDOR");
    }

    private void comprobarLecturas(UUID user, String role) throws Exception {
        var auth = bearer(user, role);
        mvc.perform(get("/api/v1/envios").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(envioA.toString()));
        mvc.perform(
                        get("/api/v1/envios")
                                .param("estado", "PENDIENTE")
                                .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(
                        get("/api/v1/envios")
                                .param("estado", "ENTREGADO")
                                .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        for (String path :
                new String[] {
                    "/api/v1/envios/" + envioA,
                    "/api/v1/envios/seguimiento/" + trackingA,
                    "/api/v1/envios/" + envioA + "/historial"
                }) mvc.perform(get(path).header("Authorization", auth)).andExpect(status().isOk());
        String trackingB = shipmentService.obtenerPorId(envioB).codigoSeguimiento();
        for (String path :
                new String[] {
                    "/api/v1/envios/" + envioB,
                    "/api/v1/envios/seguimiento/" + trackingB,
                    "/api/v1/envios/" + envioB + "/historial",
                    "/api/v1/envios/" + UUID.randomUUID()
                })
            mvc.perform(get(path).header("Authorization", auth))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.detalles.length()").value(0));
        verifyNoInteractions(courierClient);
    }

    @Test
    void sinAsociacionNoPuedeUsarSuSubComoUuidDeDominio() throws Exception {
        mvc.perform(
                        get("/api/v1/envios")
                                .header("Authorization", bearer(repartidorA, "REPARTIDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(
                        get("/api/v1/envios/{id}", envioA)
                                .header("Authorization", bearer(repartidorA, "REPARTIDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesCombinadosPermitenLecturaPorUnionPeroNoCambiarEnvioDeOtroRepartidor()
            throws Exception {
        mvc.perform(
                        get("/api/v1/envios")
                                .header("Authorization", bearer(clienteA, "CLIENTE", "REPARTIDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(
                        patch("/api/v1/envios/{id}/estado", envioA)
                                .header("Authorization", bearer(clienteA, "CLIENTE", "REPARTIDOR"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"EN_RUTA\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(courierClient);
    }

    @Test
    void clienteNoAsignaNiCancelaNiCambiaEstadoYNoHayEscrituras() throws Exception {
        var antes = shipmentService.obtenerPorId(envioA);
        long historial = historiales.count();
        var auth = bearer(clienteA, "CLIENTE");
        mvc.perform(
                        patch("/api/v1/envios/{id}/estado", envioA)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"EN_RUTA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/envios/{id}/cancelacion", envioA).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/envios/{id}/repartidor", envioA)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"repartidorId\":\"" + repartidorB + "\"}"))
                .andExpect(status().isForbidden());
        assertThat(shipmentService.obtenerPorId(envioA)).isEqualTo(antes);
        assertThat(historiales.count()).isEqualTo(historial);
        verifyNoInteractions(courierClient);
    }

    @Test
    void repartidorAvanzaSoloSuEnvioYRespetaTransiciones() throws Exception {
        var auth = bearer(usuarioRepartidorA, "REPARTIDOR");
        mvc.perform(
                        patch("/api/v1/envios/{id}/estado", envioB)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"EN_RUTA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/api/v1/envios/{id}/estado", envioA)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(
                        patch("/api/v1/envios/{id}/estado", envioA)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(courierClient);
        mvc.perform(
                        patch("/api/v1/envios/{id}/estado", envioA)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"EN_RUTA\"}"))
                .andExpect(status().isOk());
        verify(courierClient).verificarDisponible(repartidorA);
        mvc.perform(
                        patch("/api/v1/envios/{id}/estado", envioA)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isOk());
        assertThat(shipmentService.obtenerHistorial(envioA)).hasSize(3);
    }

    @Test
    void altaDeClienteAsociaSoloElSubYNoReclamaContactosPreviosPorEmail() throws Exception {
        var user = UUID.randomUUID();
        var auth = bearer(user, "CLIENTE");
        mvc.perform(
                        post("/api/v1/envios")
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        envioJson()
                                                .replace(
                                                        "ana@example.test",
                                                        "cliente-a@example.test")))
                .andExpect(status().isConflict());
        var result =
                mvc.perform(
                                post("/api/v1/envios")
                                        .header("Authorization", auth)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(envioJson()))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        assertThat(
                        nuevaTransaccion(
                                () ->
                                        envios.findById(id)
                                                .orElseThrow()
                                                .getCliente()
                                                .getUsuarioId()))
                .isEqualTo(user);
        mvc.perform(
                        post("/api/v1/envios")
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        envioJson()
                                                .replace("ana@example.test", "otra@example.test")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/envios")
                                .header("Authorization", bearer(usuarioRepartidorA, "REPARTIDOR"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(envioJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuarioId", "clienteId", "repartidorId", "sub", "roles"})
    void selectoresDeIdentidadNoPuedenEntrarPorQueryOCuerpo(String field) throws Exception {
        var auth = bearer(clienteA, "CLIENTE");
        var antes = envios.count();
        mvc.perform(
                        get("/api/v1/envios")
                                .header("Authorization", auth)
                                .param(field, clienteB.toString()))
                .andExpect(status().isForbidden());
        for (String json :
                new String[] {
                    envioJson().replaceFirst("\\{", "{\"" + field + "\":\"" + clienteB + "\","),
                    envioJson()
                            .replace(
                                    "\"nombre\":",
                                    "\"" + field + "\":\"" + clienteB + "\",\"nombre\":")
                })
            mvc.perform(
                            post("/api/v1/envios")
                                    .header("Authorization", auth)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                    .andExpect(status().isForbidden());
        assertThat(envios.count()).isEqualTo(antes);
    }

    @Test
    void identidadNoSeAceptaDesdeCookieNiJwtNoValidoYSesionesNoSeCrean() throws Exception {
        String token = TestRsaKeys.token(clienteA, "CLIENTE");
        for (var request :
                new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                    get("/api/v1/envios"),
                    get("/api/v1/envios")
                            .cookie(new jakarta.servlet.http.Cookie("access_token", token)),
                    get("/api/v1/envios").header("Authorization", "Bearer invalido")
                }) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(
                            result -> assertThat(result.getRequest().getSession(false)).isNull());
        }
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void adminConservaControlCompletoYSinCuentaAsociadaNoHayFiltraciones() throws Exception {
        mvc.perform(
                        get("/api/v1/envios")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + com.logitrack_atom.shipment_service.support
                                                        .SecurityTestActors.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(
                        get("/api/v1/envios")
                                .header("Authorization", bearer(UUID.randomUUID(), "CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tokensVencidosOConFirmaAlteradaSon401AntesDeLeerDatos() throws Exception {
        var now = java.time.Instant.now();
        var claims =
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .subject(clienteA.toString())
                        .issuer("logitrack-security")
                        .audience("logitrack-api")
                        .claim("roles", java.util.List.of("CLIENTE"))
                        .issueTime(java.util.Date.from(now.minusSeconds(2000)))
                        .expirationTime(java.util.Date.from(now.minusSeconds(1000)))
                        .build();
        String valido = TestRsaKeys.token(clienteA, "CLIENTE");
        int signature = valido.lastIndexOf('.') + 1;
        String alterado =
                valido.substring(0, signature)
                        + (valido.charAt(signature) == 'A' ? 'B' : 'A')
                        + valido.substring(signature + 1);
        for (String token : new String[] {TestRsaKeys.firmar(claims), alterado})
            mvc.perform(
                            get("/api/v1/envios/{id}", envioA)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(courierClient);
    }

    @Test
    void repartidorNoAdministraAsignacionesCancelacionesNiAsociaciones() throws Exception {
        var auth = bearer(usuarioRepartidorA, "REPARTIDOR");
        mvc.perform(patch("/api/v1/envios/{id}/cancelacion", envioA).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/envios/{id}/repartidor", envioA)
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"repartidorId\":\"" + repartidorB + "\"}"))
                .andExpect(status().isForbidden());
        for (String path :
                new String[] {
                    "/api/v1/clientes/" + clienteA + "/usuario",
                    "/api/v1/asociaciones/repartidores/" + repartidorA
                })
            mvc.perform(
                            put(path)
                                    .header("Authorization", auth)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"usuarioId\":\"" + usuarioRepartidorA + "\"}"))
                    .andExpect(status().isForbidden());
        assertThat(shipmentService.obtenerHistorial(envioA)).hasSize(1);
        verifyNoInteractions(courierClient);
    }
}
