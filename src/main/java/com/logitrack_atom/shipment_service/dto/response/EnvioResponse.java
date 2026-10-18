package com.logitrack_atom.shipment_service.dto.response;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnvioResponse(
        UUID id,
        String codigoSeguimiento,
        ClienteResponse cliente,
        RepartidorResponse repartidor,
        BigDecimal pesoKg,
        BigDecimal anchoCm,
        BigDecimal altoCm,
        BigDecimal largoCm,
        EstadoEnvio estado,
        DireccionResponse origen,
        DireccionResponse destino,
        Instant createdAt,
        Instant updatedAt) {
}