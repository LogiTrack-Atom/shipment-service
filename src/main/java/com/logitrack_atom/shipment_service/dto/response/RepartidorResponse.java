package com.logitrack_atom.shipment_service.dto.response;

import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;

import java.util.UUID;

public record RepartidorResponse(
        UUID id,
        String nombre,
        String tipoVehiculo,
        EstadoRepartidor estado) {
}