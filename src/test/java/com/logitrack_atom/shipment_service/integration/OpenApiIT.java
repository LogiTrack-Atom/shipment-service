package com.logitrack_atom.shipment_service.integration;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.doThrow;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.envioJson;
import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.solicitud;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contrasta el contrato publicado con respuestas reales de shipment.
 * @author MaxDz
 * @version 0.2.0
 */
class OpenApiIT extends MySqlIntegrationSupport {
    private static final String MISSING = "00000000-0000-0000-0000-000000000001";
    private static final Map<String, Set<String>> CONTRACTS = Map.ofEntries(
            Map.entry("post /api/v1/envios", Set.of("201", "400", "409")),
            Map.entry("get /api/v1/envios", Set.of("200", "400")),
            Map.entry("get /api/v1/envios/{id}", Set.of("200", "400", "404")),
            Map.entry("get /api/v1/envios/seguimiento/{codigoSeguimiento}", Set.of("200", "404")),
            Map.entry("get /api/v1/envios/{id}/historial", Set.of("200", "400", "404")),
            Map.entry("patch /api/v1/envios/{id}/estado", Set.of("200", "400", "404", "422", "401", "403", "502", "503")),
            Map.entry("patch /api/v1/envios/{id}/cancelacion", Set.of("200", "400", "404", "422")),
            Map.entry("put /api/v1/envios/{id}/repartidor", Set.of("200", "400", "404", "409", "422", "401", "403", "502", "503")));

    @Test
    void publicaOchoOperacionesConRespuestasTipadasYErroresPertinentes() throws Exception {
        DocumentContext document = documentacion();
        Map<String, Map<String, Map<String, Object>>> paths = document.read("$.paths");
        assertThat(paths.values().stream().mapToInt(Map::size).sum()).isEqualTo(8);
        Set<String> operationIds = new HashSet<>();
        for (var contract : CONTRACTS.entrySet()) {
            String[] key = contract.getKey().split(" ", 2);
            Map<String, Object> operation = paths.get(key[1]).get(key[0]);
            assertThat(operation.get("summary")).isNotNull();
            assertThat(operation.get("description")).isNotNull();
            assertThat(operationIds.add((String) operation.get("operationId"))).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) operation.get("responses");
            assertThat(responses.keySet()).as(contract.getKey()).containsExactlyInAnyOrderElementsOf(contract.getValue());
            for (var response : responses.entrySet()) {
                Map<String, Object> model = response.getValue();
                if (response.getKey().startsWith("2")) {
                    DocumentContext success = JsonPath.parse(model);
                    Map<String, Object> schema = success.read("$.content['application/json'].schema");
                    assertThat(schema).isNotEmpty();
                    assertThat(schema.toString()).doesNotContain("ApiError");
                } else {
                    String ref = (String) model.get("$ref");
                    assertThat(ref).startsWith("#/components/responses/");
                    DocumentContext error = JsonPath.parse(document.read("$.components.responses." + ref.substring(ref.lastIndexOf('/') + 1), Object.class));
                    assertThat(error.read("$.content['application/json'].schema['$ref']", String.class))
                            .isEqualTo("#/components/schemas/ApiError");
                    Map<String, Object> example = error.read("$.content['application/json'].examples.ejemplo.value");
                    assertThat(example.get("status")).isEqualTo(Integer.parseInt(response.getKey()));
                    assertThat(example.keySet()).containsExactlyInAnyOrder("timestamp", "status", "error", "mensaje", "detalles");
                    assertThat(Instant.parse((String) example.get("timestamp"))).isNotNull();
                }
            }
        }
        Map<String, Object> envioProperties = document.read("$.components.schemas.EnvioResponse.properties");
        assertThat(envioProperties).containsKey("repartidorId").doesNotContainKey("repartidor");
        Map<String, Object> referencia = document.read("$.components.schemas.EnvioResponse.properties.repartidorId");
        assertThat(referencia.get("format")).isEqualTo("uuid");
        assertThat(referencia.get("type")).isInstanceOf(List.class);
        List<String> tipos = document.read("$.components.schemas.EnvioResponse.properties.repartidorId.type");
        assertThat(tipos).containsExactlyInAnyOrder("string", "null");
        List<String> required = document.read("$.components.schemas.ApiError.required");
        assertThat(required)
                .containsExactlyInAnyOrder("timestamp", "status", "error", "mensaje", "detalles");
    }

    @Test
    void esquemasYMedidasDeEjemploCoincidenConLaPrecisionDeMySql() throws Exception {
        DocumentContext document = documentacion();
        for (String type : new String[]{"EnvioRequest", "EnvioResponse"}) {
            for (String field : new String[]{"pesoKg", "anchoCm", "altoCm", "largoCm"}) {
                DocumentContext property = JsonPath.parse(document.read("$.components.schemas." + type + ".properties." + field, Object.class));
                boolean peso = field.equals("pesoKg");
                assertThat(property.read("$.description", String.class)).contains(peso ? "kg" : "cm");
                BigDecimal maximum = new BigDecimal(property.read("$.maximum").toString());
                BigDecimal step = new BigDecimal(property.read("$.multipleOf").toString());
                BigDecimal example = new BigDecimal(property.read("$.example").toString());
                assertThat(maximum).isEqualByComparingTo(peso ? "9999999.999" : "99999999.99");
                assertThat(step).isEqualByComparingTo(peso ? "0.001" : "0.01");
                assertThat(example).isPositive().isLessThanOrEqualTo(maximum);
                assertThat(example.remainder(step)).isEqualByComparingTo(BigDecimal.ZERO);
            }
        }
    }

    @Test
    void swaggerUiSirveHtmlConfiguracionYRecursos() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs/swagger-config")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("SwaggerUIBundle")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MedidasInvalidas", "CuerpoInvalido", "UuidInvalido", "EstadoInvalido",
            "EnvioNoEncontrado", "TrackingNoEncontrado", "AsignacionNoEncontrada",
            "TransicionInvalida", "CancelacionInvalida", "AsignacionInvalida", "ConflictoDeDatos"})
    void ejemplosDocumentadosRepresentanErroresReales(String component) throws Exception {
        MockHttpServletRequestBuilder request;
        switch (component) {
            case "MedidasInvalidas" -> request = post("/api/v1/envios").content(envioJson("0.0001", "1", "1", "1"));
            case "CuerpoInvalido" -> request = post("/api/v1/envios").content("{");
            case "UuidInvalido" -> request = get("/api/v1/envios/no-es-uuid");
            case "EstadoInvalido" -> request = get("/api/v1/envios").param("estado", "DESCONOCIDO");
            case "EnvioNoEncontrado" -> request = get("/api/v1/envios/" + MISSING);
            case "TrackingNoEncontrado" -> request = get("/api/v1/envios/seguimiento/LT-NO-EXISTE");
            case "ConflictoDeDatos" -> request = post("/api/v1/envios").content(envioJson().replace("Ana Pérez", "A".repeat(256)));
            default -> {
                var envio = shipmentService.registrar(solicitud("ejemplo@example.test"));
                if (component.equals("AsignacionNoEncontrada")) {
                    doThrow(new RecursoNoEncontradoException("Repartidor", UUID.fromString(MISSING)))
                            .when(courierClient).verificarDisponible(UUID.fromString(MISSING));
                    request = put("/api/v1/envios/{id}/repartidor", envio.id()).content("{\"repartidorId\":\"" + MISSING + "\"}");
                } else if (component.equals("TransicionInvalida")) {
                    request = patch("/api/v1/envios/{id}/estado", envio.id()).content("{\"estado\":\"ENTREGADO\"}");
                } else {
                    nuevaTransaccion(() -> {
                        envios.findById(envio.id()).orElseThrow().setEstado(EstadoEnvio.ENTREGADO);
                        return null;
                    });
                    request = component.equals("CancelacionInvalida")
                            ? patch("/api/v1/envios/{id}/cancelacion", envio.id())
                            : put("/api/v1/envios/{id}/repartidor", envio.id()).content("{\"repartidorId\":\"" + MISSING + "\"}");
                }
            }
        }
        Map<String, Object> expected = documentacion().read("$.components.responses." + component + ".content['application/json'].examples.ejemplo.value");
        var result = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expected.get("status"));
        DocumentContext actual = JsonPath.parse(result.getResponse().getContentAsString());
        for (String field : new String[]{"status", "error", "mensaje", "detalles"}) {
            assertThat(actual.read("$." + field, Object.class)).as(component + ": " + field).isEqualTo(expected.get(field));
        }
    }

    private DocumentContext documentacion() throws Exception {
        return JsonPath.parse(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
