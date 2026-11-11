package com.logitrack_atom.shipment_service.exception;

/**
 * Indica que un recurso consultado no existe; el handler determina su respuesta pública.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class RecursoNoEncontradoException extends RuntimeException {

    /**
     * Identifica el recurso consultado sin datos internos de persistencia.
     *
     * @param recurso nombre del recurso
     * @param identificador identificador consultado
     */
    public RecursoNoEncontradoException(String recurso, Object identificador) {
        super(recurso + " no encontrado: " + identificador);
    }
}
