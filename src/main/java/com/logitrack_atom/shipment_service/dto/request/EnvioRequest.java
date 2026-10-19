package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record EnvioRequest(
        @NotNull @Valid ClienteRequest cliente,
        @NotNull @Valid DireccionRequest origen,
        @NotNull @Valid DireccionRequest destino,
        @NotNull @Positive
        @Digits(integer = 7, fraction = 3, message = "El peso admite hasta 7 dígitos enteros y 3 decimales")
        BigDecimal pesoKg,
        @NotNull @Positive
        @Digits(integer = 8, fraction = 2, message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
        BigDecimal anchoCm,
        @NotNull @Positive
        @Digits(integer = 8, fraction = 2, message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
        BigDecimal altoCm,
        @NotNull @Positive
        @Digits(integer = 8, fraction = 2, message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
        BigDecimal largoCm) {
}
