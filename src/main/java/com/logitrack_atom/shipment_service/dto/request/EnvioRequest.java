package com.logitrack_atom.shipment_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "Peso en kg: positivo, hasta 7 enteros y 3 decimales.", example = "1.250",
                minimum = "0", exclusiveMinimum = true, maximum = "9999999.999", multipleOf = 0.001)
        BigDecimal pesoKg,
        @NotNull @Positive
        @Digits(integer = 8, fraction = 2, message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
        @Schema(description = "Ancho en cm: positivo, hasta 8 enteros y 2 decimales.", example = "25.25",
                minimum = "0", exclusiveMinimum = true, maximum = "99999999.99", multipleOf = 0.01)
        BigDecimal anchoCm,
        @NotNull @Positive
        @Digits(integer = 8, fraction = 2, message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
        @Schema(description = "Alto en cm: positivo, hasta 8 enteros y 2 decimales.", example = "15.50",
                minimum = "0", exclusiveMinimum = true, maximum = "99999999.99", multipleOf = 0.01)
        BigDecimal altoCm,
        @NotNull @Positive
        @Digits(integer = 8, fraction = 2, message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
        @Schema(description = "Largo en cm: positivo, hasta 8 enteros y 2 decimales.", example = "30.75",
                minimum = "0", exclusiveMinimum = true, maximum = "99999999.99", multipleOf = 0.01)
        BigDecimal largoCm) {
}
