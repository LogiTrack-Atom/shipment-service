package com.logitrack_atom.shipment_service.dto.response;

import java.util.UUID;

public record ClienteResponse(
        UUID id,
        String nombre,
        String email,
        String telefono) {
}