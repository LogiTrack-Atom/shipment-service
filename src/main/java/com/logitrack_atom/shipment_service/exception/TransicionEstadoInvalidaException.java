package com.logitrack_atom.shipment_service.exception;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

/**
 * Rechaza una transición que no pertenece a la máquina de estados del envío.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class TransicionEstadoInvalidaException extends ReglaNegocioException {

    /**
     * Identifica la transición rechazada sin alterar estado ni historial.
     *
     * @param actual estado actual
     * @param nuevo estado solicitado
     */
    public TransicionEstadoInvalidaException(EstadoEnvio actual, EstadoEnvio nuevo) {
        super("No se puede pasar de " + actual + " a " + nuevo);
    }
}
