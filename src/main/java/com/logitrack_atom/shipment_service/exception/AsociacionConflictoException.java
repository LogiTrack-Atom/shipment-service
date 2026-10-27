package com.logitrack_atom.shipment_service.exception;

/**
 * Impide reclamar o sustituir una asociación de identidad existente.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class AsociacionConflictoException extends RuntimeException {
    /** Crea un conflicto sin divulgar datos de otra cuenta. */
    public AsociacionConflictoException() {
        super("La asociación de identidad requiere revisión administrativa");
    }
}
