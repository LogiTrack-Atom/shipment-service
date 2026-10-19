package com.logitrack_atom.shipment_service.integration;

import com.jayway.jsonpath.JsonPath;
import com.logitrack_atom.shipment_service.entity.Repartidor;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Web real de Spring (MockMvc) con servicios, repositorios y MySQL sin reemplazos. */
class ShipmentApiIT extends MySqlIntegrationSupport {

    @Test
    void completaEntregaYCancelacionIndependienteConHistorialPersistido() throws Exception {
        String envio = registrarEnvio();
        String codigo = shipmentService.obtenerPorId(UUID.fromString(envio)).codigoSeguimiento();
        String repartidor = identificador(mvc.perform(post("/api/v1/repartidores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","tipoVehiculo":"MOTO"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVO")));
        mvc.perform(put("/api/v1/envios/{id}/repartidor", envio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repartidorId\":\"" + repartidor + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repartidor.id").value(repartidor));
        for (String estado : new String[]{"EN_RUTA", "ENTREGADO"}) {
            mvc.perform(patch("/api/v1/envios/{id}/estado", envio)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"" + estado + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
        }
        mvc.perform(get("/api/v1/envios/seguimiento/{codigo}", codigo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(envio));
        mvc.perform(get("/api/v1/envios/{id}", envio))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENTREGADO"));
        mvc.perform(get("/api/v1/envios").param("estado", "ENTREGADO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains(envio)));
        mvc.perform(get("/api/v1/envios/{id}/historial", envio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].estadoNuevo", contains("PENDIENTE", "EN_RUTA", "ENTREGADO")));
        String cancelado = registrarEnvio();
        mvc.perform(patch("/api/v1/envios/{id}/cancelacion", cancelado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
        mvc.perform(patch("/api/v1/envios/{id}/cancelacion", envio))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.status").value(422));
        nuevaTransaccion(() -> {
            entityManager.clear();
            assertThat(envios.findById(UUID.fromString(envio)).orElseThrow().getEstado()).isEqualTo(EstadoEnvio.ENTREGADO);
            assertThat(envios.findById(UUID.fromString(cancelado)).orElseThrow().getEstado()).isEqualTo(EstadoEnvio.CANCELADO);
            assertThat(historiales.findByEnvioIdOrderByFechaCambioAsc(UUID.fromString(cancelado)))
                    .extracting("estadoNuevo").containsExactly(EstadoEnvio.PENDIENTE, EstadoEnvio.CANCELADO);
            assertThat(clientes.count()).isEqualTo(1);
            assertThat(historiales.count()).isEqualTo(5);
            return null;
        });
    }

    @ParameterizedTest(name = "MySQL: {0}={1} no debe crear filas")
    @MethodSource("medidasNoRepresentables")
    void regresionDePrecisionRechazaAntesDePersistir(String campo, String valor) throws Exception {
        Map<String, String> medidas = new HashMap<>(Map.of(
                "pesoKg", "1.250", "anchoCm", "25.25", "altoCm", "15.50", "largoCm", "30.75"));
        medidas.put(campo, valor);
        mvc.perform(post("/api/v1/envios").contentType(MediaType.APPLICATION_JSON)
                        .content(envioJson(medidas.get("pesoKg"), medidas.get("anchoCm"), medidas.get("altoCm"), medidas.get("largoCm"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalles", hasItem(startsWith(campo + ":"))));
        comprobarBaseVacia();
    }

    @ParameterizedTest
    @MethodSource("medidasRepresentables")
    void conservaMedidasExactasEnMySql(String peso, String ancho, String alto, String largo) throws Exception {
        String id = identificador(mvc.perform(post("/api/v1/envios").contentType(MediaType.APPLICATION_JSON)
                        .content(envioJson(peso, ancho, alto, largo)))
                .andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/envios/{id}", id)).andExpect(status().isOk());
        String[] columnas = {"peso_kg", "ancho_cm", "alto_cm", "largo_cm"};
        String[] valores = {peso, ancho, alto, largo};
        for (int i = 0; i < columnas.length; i++) {
            assertThat(jdbc.queryForObject("SELECT " + columnas[i] + " FROM envios", BigDecimal.class))
                    .isEqualByComparingTo(valores[i]);
        }
        assertThat(envios.count()).isEqualTo(1);
        assertThat(historiales.count()).isEqualTo(1);
    }

    @ParameterizedTest(name = "400: {0} {1}")
    @MethodSource("solicitudesInvalidas")
    void erroresDeJsonUuidEnumYValidacionResponden400(String metodo, String ruta, String body) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), ruta).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        comprobarBaseVacia();
    }

    @ParameterizedTest
    @MethodSource("rutasInexistentes")
    void recursosInexistentesResponden404(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        comprobarBaseVacia();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEnvio.class, names = {"EN_RUTA", "ENTREGADO"})
    void transicionesInvalidasNoAlteranEstadoNiHistorial(EstadoEnvio destino) throws Exception {
        String id = registrarEnvio();
        mvc.perform(patch("/api/v1/envios/{id}/estado", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"" + destino + "\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.status").value(422));
        assertThat(shipmentService.obtenerPorId(UUID.fromString(id)).estado()).isEqualTo(EstadoEnvio.PENDIENTE);
        assertThat(shipmentService.obtenerHistorial(UUID.fromString(id))).hasSize(1);
    }

    @Test
    void noAsignaRepartidorInexistenteNiInactivo() throws Exception {
        String id = registrarEnvio();
        mvc.perform(put("/api/v1/envios/{id}/repartidor", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repartidorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        var repartidor = new Repartidor();
        repartidor.setNombre("Luis");
        repartidor.setTipoVehiculo("MOTO");
        repartidor.setEstado(EstadoRepartidor.INACTIVO);
        UUID repartidorId = repartidores.saveAndFlush(repartidor).getId();
        mvc.perform(put("/api/v1/envios/{id}/repartidor", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repartidorId\":\"" + repartidorId + "\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.status").value(422));
        assertThat(shipmentService.obtenerPorId(UUID.fromString(id)).repartidor()).isNull();
        assertThat(historiales.count()).isEqualTo(1);
    }

    @Test
    void violacionRealDeLongitudDeColumnaResponde409YSinAltasParciales() throws Exception {
        mvc.perform(post("/api/v1/envios").contentType(MediaType.APPLICATION_JSON)
                        .content(envioJson().replace("Ana Pérez", "A".repeat(256))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        comprobarBaseVacia();
    }

    private String registrarEnvio() throws Exception {
        return identificador(mvc.perform(post("/api/v1/envios").contentType(MediaType.APPLICATION_JSON)
                .content(envioJson())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")));
    }

    private static String identificador(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static Stream<Arguments> medidasNoRepresentables() {
        return Stream.of("pesoKg", "anchoCm", "altoCm", "largoCm")
                .flatMap(campo -> Stream.of(campo.equals("pesoKg") ? "0.0001" : "0.001",
                                campo.equals("pesoKg") ? "10000000" : "100000000")
                        .map(valor -> Arguments.of(campo, valor)));
    }

    private static Stream<Arguments> medidasRepresentables() {
        return Stream.of(Arguments.of("1.250", "25.25", "15.50", "30.75"),
                Arguments.of("0.001", "0.01", "0.01", "0.01"),
                Arguments.of("9999999.999", "99999999.99", "99999999.99", "99999999.99"),
                Arguments.of("1", "1", "1", "1"));
    }

    private static Stream<Arguments> solicitudesInvalidas() {
        String base = "/api/v1/envios/00000000-0000-0000-0000-000000000001";
        return Stream.of(
                Arguments.of("POST", "/api/v1/envios", "{"),
                Arguments.of("GET", "/api/v1/envios/no-es-uuid", ""),
                Arguments.of("GET", "/api/v1/envios?estado=DESCONOCIDO", ""),
                Arguments.of("PATCH", base + "/estado", "{\"estado\":\"DESCONOCIDO\"}"),
                Arguments.of("PUT", base + "/repartidor", "{\"repartidorId\":\"no-es-uuid\"}"),
                Arguments.of("POST", "/api/v1/envios", "{}"),
                Arguments.of("POST", "/api/v1/envios", envioJson().replace("Ana Pérez", "")));
    }

    private static Stream<String> rutasInexistentes() {
        String id = "00000000-0000-0000-0000-000000000001";
        return Stream.of("/api/v1/envios/" + id, "/api/v1/envios/seguimiento/NO-EXISTE",
                "/api/v1/envios/" + id + "/historial", "/api/v1/repartidores/" + id);
    }
}
