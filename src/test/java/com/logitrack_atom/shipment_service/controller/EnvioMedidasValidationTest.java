package com.logitrack_atom.shipment_service.controller;

import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.exception.GlobalExceptionHandler;
import com.logitrack_atom.shipment_service.service.ShipmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión de la issue #8: valida medidas en la frontera HTTP sin depender de MySQL.
 * Usa Bean Validation real y comprueba que las entradas inválidas no llegan al servicio.
 */
@ExtendWith(MockitoExtension.class)
class EnvioMedidasValidationTest {

    @Mock
    private ShipmentService shipmentService;

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new EnvioController(shipmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void cerrarValidator() {
        validator.close();
    }

    @ParameterizedTest(name = "{0}={1} debe rechazarse antes de llamar al servicio")
    @MethodSource("medidasInvalidas")
    void rechazaMedidasInvalidasSinInvocarElServicio(String campo, String valor) throws Exception {
        Map<String, String> medidas = new HashMap<>(Map.of(
                "pesoKg", "1.250", "anchoCm", "25.00", "altoCm", "15.00", "largoCm", "30.00"));
        medidas.put(campo, valor);

        mockMvc.perform(post("/api/v1/envios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(medidas.get("pesoKg"), medidas.get("anchoCm"),
                                medidas.get("altoCm"), medidas.get("largoCm"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalles", hasItem(startsWith(campo + ":"))));

        verifyNoInteractions(shipmentService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("medidasRepresentables")
    void aceptaMedidasRepresentablesSinAlterarlas(String caso, String peso, String ancho,
                                                  String alto, String largo) throws Exception {
        mockMvc.perform(post("/api/v1/envios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(peso, ancho, alto, largo)))
                .andExpect(status().isCreated());

        ArgumentCaptor<EnvioRequest> captor = ArgumentCaptor.forClass(EnvioRequest.class);
        verify(shipmentService).registrar(captor.capture());
        EnvioRequest request = captor.getValue();
        assertEquals(0, new BigDecimal(peso).compareTo(request.pesoKg()));
        assertEquals(0, new BigDecimal(ancho).compareTo(request.anchoCm()));
        assertEquals(0, new BigDecimal(alto).compareTo(request.altoCm()));
        assertEquals(0, new BigDecimal(largo).compareTo(request.largoCm()));
    }

    private static Stream<Arguments> medidasInvalidas() {
        return Stream.of("pesoKg", "anchoCm", "altoCm", "largoCm")
                .flatMap(campo -> Stream.of(
                                "null", "0", "-1",
                                campo.equals("pesoKg") ? "0.0001" : "0.001",
                                campo.equals("pesoKg") ? "1.2345" : "1.234",
                                campo.equals("pesoKg") ? "10000000" : "100000000")
                        .map(valor -> Arguments.of(campo, valor)));
    }

    private static Stream<Arguments> medidasRepresentables() {
        return Stream.of(
                Arguments.of("Medidas habituales", "1.250", "25.00", "15.00", "30.00"),
                Arguments.of("Mínimos representables", "0.001", "0.01", "0.01", "0.01"),
                Arguments.of("Máximos representables", "9999999.999", "99999999.99", "99999999.99", "99999999.99"),
                Arguments.of("Valores enteros positivos", "1", "1", "1", "1"));
    }

    private static String solicitud(String peso, String ancho, String alto, String largo) {
        return """
                {
                  "cliente": {"nombre": "Ana Pérez", "email": "ana@example.com", "telefono": "+5493880000000"},
                  "origen": {"calle": "Belgrano", "numero": "100", "ciudad": "Jujuy", "codigoPostal": "Y4600"},
                  "destino": {"calle": "Alvear", "numero": "200", "ciudad": "Jujuy", "codigoPostal": "Y4600"},
                  "pesoKg": %s,
                  "anchoCm": %s,
                  "altoCm": %s,
                  "largoCm": %s
                }
                """.formatted(peso, ancho, alto, largo);
    }
}
