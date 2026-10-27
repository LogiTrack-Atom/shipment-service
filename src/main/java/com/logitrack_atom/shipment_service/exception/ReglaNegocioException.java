package com.logitrack_atom.shipment_service.exception;

/**
 * Expresa una precondición de negocio incumplida, traducida a HTTP 422.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class ReglaNegocioException extends RuntimeException {

    /**
     * Construye un rechazo con un mensaje seguro de negocio.
     *
     * @param mensaje regla incumplida
     */
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
