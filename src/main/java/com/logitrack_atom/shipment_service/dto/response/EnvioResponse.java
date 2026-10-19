package com.logitrack_atom.shipment_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnvioResponse(
        UUID id,
        String codigoSeguimiento,
        ClienteResponse cliente,
        RepartidorResponse repartidor,
        @Schema(description = "Peso en kg: positivo, hasta 7 enteros y 3 decimales.", example = "1.250",
                minimum = "0", exclusiveMinimum = true, maximum = "9999999.999", multipleOf = 0.001)
        BigDecimal pesoKg,
        @Schema(description = "Ancho en cm: positivo, hasta 8 enteros y 2 decimales.", example = "25.25",
                minimum = "0", exclusiveMinimum = true, maximum = "99999999.99", multipleOf = 0.01)
        BigDecimal anchoCm,
        @Schema(description = "Alto en cm: positivo, hasta 8 enteros y 2 decimales.", example = "15.50",
                minimum = "0", exclusiveMinimum = true, maximum = "99999999.99", multipleOf = 0.01)
        BigDecimal altoCm,
        @Schema(description = "Largo en cm: positivo, hasta 8 enteros y 2 decimales.", example = "30.75",
                minimum = "0", exclusiveMinimum = true, maximum = "99999999.99", multipleOf = 0.01)
        BigDecimal largoCm,
        EstadoEnvio estado,
        DireccionResponse origen,
        DireccionResponse destino,
        Instant createdAt,
        Instant updatedAt) {
}