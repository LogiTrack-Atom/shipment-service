package com.logitrack_atom.shipment_service.dto.response;

public record DireccionResponse(
        String calle,
        String numero,
        String ciudad,
        String codigoPostal) {
}