package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record EnvioRequest(
        @NotNull @Valid ClienteRequest cliente,
        @NotNull @Valid DireccionRequest origen,
        @NotNull @Valid DireccionRequest destino,
        @NotNull @Positive BigDecimal pesoKg,
        @NotNull @Positive BigDecimal anchoCm,
        @NotNull @Positive BigDecimal altoCm,
        @NotNull @Positive BigDecimal largoCm) {
}