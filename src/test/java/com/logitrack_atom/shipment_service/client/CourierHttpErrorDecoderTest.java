package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.exception.CourierRespuestaInvalidaException;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.security.CourierBearerProvider;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Prueba la clasificación HTTP sin leer cuerpos privados y el límite entre HTTP y transporte.
 *
 * @author MaxDz
 * @version 0.3.0
 */
class CourierHttpErrorDecoderTest {
    private static final String PRIVATE = "contenido-privado-que-no-debe-exponerse";

    @ParameterizedTest
    @CsvSource({"300,502", "302,502", "307,502", "400,502", "401,502", "403,502", "404,404",
            "409,502", "422,502", "429,503", "500,503", "502,503", "503,503", "504,503", "599,503", "600,502"})
    void clasificaSinConsumirElCuerpoNiReintentarPorRetryAfter(int remote, int expected) throws Exception {
        var request = request(UUID.randomUUID());
        var body = new InputStream() {
            @Override public int read() { throw new AssertionError("El ErrorDecoder no debe leer el cuerpo remoto."); }
        };
        try (var response = Response.builder().request(request).status(remote).reason(PRIVATE)
                .headers(Map.of("Retry-After", List.of("3600"), "Set-Cookie", List.of(PRIVATE)))
                .body(body, null).build()) {
            Exception exception = new CourierHttpErrorDecoder().decode("consultar", response);
            assertThat(exception).isNotInstanceOf(RetryableException.class).hasCause(null)
                    .hasMessageNotContaining(PRIVATE);
            if (expected == 404) {
                assertThat(exception).isInstanceOf(FeignException.class);
                assertThat(((FeignException) exception).status()).isEqualTo(404);
                assertThat(((FeignException) exception).responseHeaders()).isEmpty();
            } else {
                assertThat(exception).isInstanceOf(expected == 503
                        ? CourierNoDisponibleException.class : CourierRespuestaInvalidaException.class);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 200, 503})
    void fallaDeTransporteConserva503AunqueYaExistanCabeceras(int status) {
        UUID id = UUID.randomUUID();
        var api = mock(CourierAvailabilityApi.class);
        var bearer = mock(CourierBearerProvider.class);
        when(bearer.obtener()).thenReturn(PRIVATE);
        when(api.consultar(id, "Bearer " + PRIVATE)).thenThrow(
                new RetryableException(status, PRIVATE, Request.HttpMethod.GET, (Long) null, request(id)));
        var client = new FeignCourierClient(api, bearer);
        assertThatThrownBy(() -> client.verificarDisponible(id))
                .isInstanceOf(CourierNoDisponibleException.class).hasCause(null).hasMessageNotContaining(PRIVATE);
        verify(api).consultar(id, "Bearer " + PRIVATE);
        verifyNoMoreInteractions(api);
    }

    @Test
    void inexistenteSoloExponeElUuidSolicitado() throws Exception {
        UUID id = UUID.randomUUID();
        var api = mock(CourierAvailabilityApi.class);
        var bearer = mock(CourierBearerProvider.class);
        when(bearer.obtener()).thenReturn(PRIVATE);
        try (var response = Response.builder().request(request(id)).status(404).reason(PRIVATE)
                .headers(Map.of()).body(PRIVATE, StandardCharsets.UTF_8).build()) {
            when(api.consultar(id, "Bearer " + PRIVATE)).thenThrow(
                    new CourierHttpErrorDecoder().decode("consultar", response));
        }
        assertThatThrownBy(() -> new FeignCourierClient(api, bearer).verificarDisponible(id))
                .isInstanceOf(RecursoNoEncontradoException.class).hasCause(null)
                .hasMessageContaining(id.toString()).hasMessageNotContaining(PRIVATE);
    }

    private static Request request(UUID id) {
        return Request.create(Request.HttpMethod.GET,
                "http://courier-service/api/v1/repartidores/" + id + "/disponibilidad",
                Map.of("Authorization", List.of("Bearer " + PRIVATE)), null, StandardCharsets.UTF_8, null);
    }
}
