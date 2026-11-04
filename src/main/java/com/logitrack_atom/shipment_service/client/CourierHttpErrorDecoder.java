package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.exception.CourierRespuestaInvalidaException;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.util.Map;

/**
 * Clasifica errores HTTP de courier sin consumir cuerpos, cabeceras ni motivos privados.
 *
 * <p>El 404 conserva su estado para que el adaptador añada únicamente el UUID solicitado.
 * Las fallas sin respuesta se tratan por separado en FeignCourierClient. Nunca se crea
 * una excepción reintentable a partir de Retry-After ni se autoriza una disponibilidad.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
public final class CourierHttpErrorDecoder implements ErrorDecoder {
    /** Crea el clasificador exclusivo de disponibilidad. */
    public CourierHttpErrorDecoder() { }

    /** {@inheritDoc} */
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == 404) {
            return FeignException.errorStatus(methodKey, response.toBuilder()
                    .body((Response.Body) null).headers(Map.of()).reason(null).build());
        }
        if (status == 429 || (status >= 500 && status < 600)) return new CourierNoDisponibleException();
        return new CourierRespuestaInvalidaException();
    }
}
