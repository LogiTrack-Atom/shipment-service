package com.logitrack_atom.shipment_service.exception;

/**
 * Indica que no pudo verificarse una precondición remota de courier.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class CourierNoDisponibleException extends RuntimeException {
    /** Crea un error público sin detalles del transporte ni credenciales. */
    public CourierNoDisponibleException() {
        super("No se pudo verificar la disponibilidad del repartidor");
    }
}
