package com.logitrack_atom.shipment_service.exception;

public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Object identificador) {
        super(recurso + " no encontrado: " + identificador);
    }
}