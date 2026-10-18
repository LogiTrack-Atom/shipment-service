package com.logitrack_atom.shipment_service.exception;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

public class TransicionEstadoInvalidaException extends ReglaNegocioException {

    public TransicionEstadoInvalidaException(EstadoEnvio actual, EstadoEnvio nuevo) {
        super("No se puede pasar de " + actual + " a " + nuevo);
    }
}