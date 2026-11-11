package com.logitrack_atom.shipment_service.exception;

/**
 * Indica una respuesta remota incompatible, sin exponer cuerpos ni credenciales.
 * @author MaxDz
 * @version 0.2.0
 */
public class CourierRespuestaInvalidaException extends RuntimeException {
    /** Crea un mensaje público uniforme para errores 502. */
    public CourierRespuestaInvalidaException() { super("El servicio de repartidores devolvió una respuesta inválida"); }
}
