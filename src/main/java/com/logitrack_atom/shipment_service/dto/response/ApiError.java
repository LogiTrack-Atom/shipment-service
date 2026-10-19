package com.logitrack_atom.shipment_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Formato común de errores de la API.")
public record ApiError(
        @Schema(description = "Instante UTC del error.", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp,
        @Schema(description = "Código HTTP numérico.", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(description = "Descripción del código HTTP.", requiredMode = Schema.RequiredMode.REQUIRED)
        String error,
        @Schema(description = "Mensaje general de la operación rechazada.", requiredMode = Schema.RequiredMode.REQUIRED)
        String mensaje,
        @Schema(description = "Detalles de validación; lista vacía para errores sin detalle de campos.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> detalles) {
}