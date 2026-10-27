package com.logitrack_atom.shipment_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Contrato público de errores, incluidos los rechazos de los filtros de seguridad.
 *
 * @param timestamp instante UTC del error
 * @param status código HTTP
 * @param error descripción HTTP
 * @param mensaje mensaje seguro de la operación
 * @param detalles detalles de validación o lista vacía; sin SQL ni credenciales
 * @author MaxDz
 * @version 0.2.0
 */
@Schema(description = "Formato común de errores de la API.")
public record ApiError(
        @Schema(
                        description = "Instante UTC del error.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Instant timestamp,
        @Schema(description = "Código HTTP numérico.", requiredMode = Schema.RequiredMode.REQUIRED)
                int status,
        @Schema(
                        description = "Descripción del código HTTP.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String error,
        @Schema(
                        description = "Mensaje general de la operación rechazada.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String mensaje,
        @Schema(
                        description =
                                "Detalles de validación; lista vacía para errores sin detalle de"
                                    + " campos.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<String> detalles) {}
