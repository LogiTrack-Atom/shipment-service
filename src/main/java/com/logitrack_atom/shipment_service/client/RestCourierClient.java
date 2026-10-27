package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.config.CourierClientProperties;
import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.exception.CourierRespuestaInvalidaException;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.exception.ReglaNegocioException;
import com.logitrack_atom.shipment_service.security.CourierBearerProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.HttpClient;
import java.util.UUID;

/**
 * Consulta disponibilidad por REST con JWT validado, tiempos finitos y errores públicos uniformes.
 *
 * <p>Solo envía GET al origen configurado. No sigue redirecciones, escribe en courier,
 * registra credenciales ni aprueba asignaciones ante fallas. Una lectura positiva
 * no reserva al repartidor; su estado puede cambiar después de la consulta.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
@EnableConfigurationProperties(CourierClientProperties.class)
public class RestCourierClient implements CourierClient {
    private static final int MAX_BODY = 65_536;
    private final RestClient client;
    private final CourierBearerProvider bearerProvider;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /**
     * Construye el transporte con redirecciones desactivadas y timeouts configurados.
     * @param properties origen y límites de espera
     * @param bearerProvider verificación local de las credenciales del solicitante
     */
    public RestCourierClient(CourierClientProperties properties, CourierBearerProvider bearerProvider) {
        this.bearerProvider = bearerProvider;
        var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        client = RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
    }

    /** {@inheritDoc} */
    @Override
    public void verificarDisponible(UUID repartidorId) {
        if (repartidorId == null) throw new IllegalArgumentException("El identificador es obligatorio.");
        String bearer = bearerProvider.obtener();
        try {
            client.get().uri("/api/v1/repartidores/{id}/disponibilidad", repartidorId)
                    .headers(headers -> headers.setBearerAuth(bearer)).accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 404) throw new RecursoNoEncontradoException("Repartidor", repartidorId);
                        if (code == 429 || (code >= 500 && code < 600)) throw new CourierNoDisponibleException();
                        if (code != 200) throw new CourierRespuestaInvalidaException();
                        try {
                            if (response.getHeaders().getContentType() == null
                                    || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())) {
                                throw new CourierRespuestaInvalidaException();
                            }
                        } catch (IllegalArgumentException ex) {
                            throw new CourierRespuestaInvalidaException();
                        }
                        byte[] body = response.getBody().readNBytes(MAX_BODY + 1);
                        if (body.length > MAX_BODY) throw new CourierRespuestaInvalidaException();
                        var availability = leer(body, repartidorId);
                        if (!availability.disponible()) throw new ReglaNegocioException("El repartidor no está activo");
                        return null;
                    });
        } catch (RestClientException ex) {
            throw new CourierNoDisponibleException();
        }
    }

    private DisponibilidadCourier leer(byte[] body, UUID requested) {
        try {
            var node = mapper.readTree(body);
            if (node == null || !node.isObject() || node.size() != 3
                    || !node.path("repartidorId").isString() || !node.path("estado").isString()
                    || !node.path("disponible").isBoolean()) throw new CourierRespuestaInvalidaException();
            String id = node.path("repartidorId").asString();
            String state = node.path("estado").asString();
            boolean available = node.path("disponible").asBoolean();
            if (!requested.toString().equalsIgnoreCase(id)
                    || !("ACTIVO".equals(state) || "INACTIVO".equals(state))
                    || available != "ACTIVO".equals(state)) throw new CourierRespuestaInvalidaException();
            return new DisponibilidadCourier(requested, state, available);
        } catch (JacksonException ex) {
            throw new CourierRespuestaInvalidaException();
        }
    }
}
