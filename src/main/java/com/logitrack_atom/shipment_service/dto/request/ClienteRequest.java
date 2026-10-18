package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClienteRequest(
        @NotBlank String nombre,
        @NotBlank @Email String email,
        String telefono) {
}