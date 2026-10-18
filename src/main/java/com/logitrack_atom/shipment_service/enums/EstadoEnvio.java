package com.logitrack_atom.shipment_service.enums;

public enum EstadoEnvio {
    PENDIENTE,
    EN_RUTA,
    ENTREGADO,
    CANCELADO;

    public boolean puedeTransicionarA(EstadoEnvio nuevo) {
        return switch (this) {
            case PENDIENTE -> nuevo == EN_RUTA || nuevo == CANCELADO;
            case EN_RUTA -> nuevo == ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }
}