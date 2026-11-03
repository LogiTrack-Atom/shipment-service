package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.exception.CourierRespuestaInvalidaException;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.exception.ReglaNegocioException;
import com.logitrack_atom.shipment_service.security.CourierBearerProvider;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Adapta OpenFeign a la frontera de disponibilidad preservando las reglas de Sprint02.
 *
 * <p>El cliente declarativo selecciona una instancia del nombre courier-service.
 * Este adaptador conserva errores públicos sin exponer cuerpos ni credenciales remotas.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@Component
public class FeignCourierClient implements CourierClient {
    private final CourierAvailabilityApi api;
    private final CourierBearerProvider bearerProvider;

    /**
     * Recibe el proxy declarativo y el acceso exclusivo al JWT ya validado.
     * @param api transporte descubierto de courier
     * @param bearerProvider credencial autorizada del solicitante
     */
    public FeignCourierClient(CourierAvailabilityApi api, CourierBearerProvider bearerProvider) {
        this.api = api;
        this.bearerProvider = bearerProvider;
    }

    /** {@inheritDoc} */
    @Override
    public void verificarDisponible(UUID repartidorId) {
        if (repartidorId == null) throw new IllegalArgumentException("El identificador es obligatorio.");
        String bearer = bearerProvider.obtener();
        DisponibilidadCourier availability;
        try {
            availability = api.consultar(repartidorId, "Bearer " + bearer);
        } catch (RetryableException ex) {
            // Conexión y lectura pueden fallar sin respuesta o después de cabeceras 200.
            throw new CourierNoDisponibleException();
        } catch (FeignException ex) {
            int status = ex.status();
            if (status == 404) throw new RecursoNoEncontradoException("Repartidor", repartidorId);
            if (status < 0) throw new CourierNoDisponibleException();
            throw new CourierRespuestaInvalidaException();
        }
        if (availability == null || !repartidorId.equals(availability.repartidorId())
                || !("ACTIVO".equals(availability.estado()) || "INACTIVO".equals(availability.estado()))
                || availability.disponible() != "ACTIVO".equals(availability.estado()))
            throw new CourierRespuestaInvalidaException();
        if (!availability.disponible()) throw new ReglaNegocioException("El repartidor no está activo");
    }
}
