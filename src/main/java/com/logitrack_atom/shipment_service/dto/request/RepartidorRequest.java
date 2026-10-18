package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RepartidorRequest(
        @NotBlank String nombre,
        @NotBlank String tipoVehiculo) {
}