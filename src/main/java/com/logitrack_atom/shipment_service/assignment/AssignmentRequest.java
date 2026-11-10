package com.logitrack_atom.shipment_service.assignment;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Intención mínima de una solicitud; la identidad del actor procede exclusivamente del JWT.
 * @param repartidorId UUID de dominio de Courier que será consultado de forma asíncrona
 */
public record AssignmentRequest(@NotNull UUID repartidorId) {
    /**
     * Rechaza campos desconocidos en este contrato sin modificar el mapper global.
     * @param name nombre no admitido
     * @param ignored valor que no se conserva ni se incorpora al error
     */
    @JsonAnySetter
    public void rejectUnknown(String name, Object ignored) {
        throw new IllegalArgumentException("Campo no admitido en la solicitud de asignación");
    }
}
