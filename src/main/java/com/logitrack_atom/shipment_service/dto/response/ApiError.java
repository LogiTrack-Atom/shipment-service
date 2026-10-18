package com.logitrack_atom.shipment_service.dto.response;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String mensaje,
        List<String> detalles) {
}