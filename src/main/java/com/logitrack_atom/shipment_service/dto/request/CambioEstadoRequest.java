package com.logitrack_atom.shipment_service.dto.request;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoRequest(@NotNull EstadoEnvio estado) {
}