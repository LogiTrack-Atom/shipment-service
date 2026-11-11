package com.logitrack_atom.shipment_service.exception;

import org.springframework.http.HttpStatus;

/**
 * Encapsula fallas de proveedores de identidad sin datos remotos ni credenciales.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class ServicioIdentidadException extends RuntimeException {
    /** Estado HTTP público de la falla remota. */
    private final HttpStatus status;

    /**
     * Crea un rechazo seguro del proveedor.
     *
     * @param status BAD_GATEWAY o SERVICE_UNAVAILABLE
     */
    public ServicioIdentidadException(HttpStatus status) {
        super("No se pudo verificar la asociación de identidad");
        this.status = status;
    }

    /**
     * Obtiene el estado HTTP público del fallo de comprobación.
     *
     * @return estado público de la falla
     */
    public HttpStatus getStatus() {
        return status;
    }
}
