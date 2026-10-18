package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DireccionRequest(
        @NotBlank String calle,
        @NotBlank String numero,
        @NotBlank String ciudad,
        @NotBlank String codigoPostal) {
}