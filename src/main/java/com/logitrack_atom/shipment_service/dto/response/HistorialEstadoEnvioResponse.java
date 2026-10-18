package com.logitrack_atom.shipment_service.dto.response;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

import java.time.Instant;
import java.util.UUID;

public record HistorialEstadoEnvioResponse(
        UUID id,
        EstadoEnvio estadoAnterior,
        EstadoEnvio estadoNuevo,
        Instant fechaCambio) {
}