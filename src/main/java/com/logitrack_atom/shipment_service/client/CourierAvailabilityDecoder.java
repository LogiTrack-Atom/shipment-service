package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.exception.CourierRespuestaInvalidaException;
import feign.Response;
import feign.RetryableException;
import feign.codec.Decoder;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Decodifica el DTO mínimo con límites de tamaño y tipos JSON estrictos.
 *
 * <p>Rechaza duplicados, contenido adicional, Content-Type incorrecto y UUID no canónicos.
 * La coincidencia con el identificador solicitado se comprueba en FeignCourierClient.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
public final class CourierAvailabilityDecoder implements Decoder {
    private static final int MAX_BODY = 65_536;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /** Crea el decodificador exclusivo de disponibilidad. */
    public CourierAvailabilityDecoder() { }

    /** {@inheritDoc} */
    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.status() != 200 || type != DisponibilidadCourier.class || response.body() == null)
            throw new CourierRespuestaInvalidaException();
        var types = response.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("Content-Type"))
                .flatMap(entry -> entry.getValue().stream()).toList();
        try {
            if (types.size() != 1 || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(types.getFirst())))
                throw new CourierRespuestaInvalidaException();
            byte[] body = response.body().asInputStream().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) throw new CourierRespuestaInvalidaException();
            var node = mapper.readTree(body);
            if (node == null || !node.isObject() || node.size() != 3
                    || !node.path("repartidorId").isString() || !node.path("estado").isString()
                    || !node.path("disponible").isBoolean()) throw new CourierRespuestaInvalidaException();
            String id = node.path("repartidorId").asString();
            UUID uuid = UUID.fromString(id);
            if (!uuid.toString().equalsIgnoreCase(id)) throw new CourierRespuestaInvalidaException();
            return new DisponibilidadCourier(uuid, node.path("estado").asString(), node.path("disponible").asBoolean());
        } catch (JacksonException | IllegalArgumentException ex) {
            throw new CourierRespuestaInvalidaException();
        } catch (IOException ex) {
            // El timeout de cuerpo también es una falla de transporte, aunque las cabeceras sean 200.
            throw new RetryableException(-1, "No se pudo leer la respuesta del proveedor.",
                    response.request().httpMethod(), (Long) null, response.request());
        }
    }
}
