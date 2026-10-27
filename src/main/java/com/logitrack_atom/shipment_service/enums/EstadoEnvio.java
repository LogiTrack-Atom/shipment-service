package com.logitrack_atom.shipment_service.enums;

/**
 * Máquina de estados del Sprint01, conservada después de separar los servicios.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public enum EstadoEnvio {
    /** Envío registrado que admite asignación, inicio de ruta o cancelación. */
    PENDIENTE,
    /** Reparto iniciado que solo puede finalizar como ENTREGADO. */
    EN_RUTA,
    /** Estado terminal de una entrega completada. */
    ENTREGADO,
    /** Estado terminal de una cancelación desde PENDIENTE. */
    CANCELADO;

    /**
     * Evalúa únicamente la transición local; el servicio verifica permisos y disponibilidad.
     *
     * @param nuevo estado solicitado
     * @return verdadero si existe la transición; repetir un estado se rechaza
     */
    public boolean puedeTransicionarA(EstadoEnvio nuevo) {
        return switch (this) {
            case PENDIENTE -> nuevo == EN_RUTA || nuevo == CANCELADO;
            case EN_RUTA -> nuevo == ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }
}
