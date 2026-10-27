package com.logitrack_atom.shipment_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Vista de un envío con referencia remota, sin datos duplicados del repartidor.
 *
 * <p>Desde Sprint02, {@code repartidorId} reemplaza el objeto anidado del Sprint01.
 * Su valor es nulo cuando no existe asignación.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 * @param id identificador del envío
 * @param codigoSeguimiento código único de seguimiento
 * @param cliente cliente local
 * @param repartidorId UUID del repartidor remoto, o nulo
 * @param pesoKg peso en kilogramos
 * @param anchoCm ancho en centímetros
 * @param altoCm alto en centímetros
 * @param largoCm largo en centímetros
 * @param estado estado actual
 * @param origen dirección de origen
 * @param destino dirección de destino
 * @param createdAt fecha de creación
 * @param updatedAt fecha de última actualización
 */
public record EnvioResponse(
        UUID id,
        String codigoSeguimiento,
        ClienteResponse cliente,
        @Schema(description = "UUID remoto del repartidor; null sin asignación. Reemplaza repartidor del Sprint01.",
                types = {"string", "null"}, format = "uuid")
        UUID repartidorId,
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
        @Schema(description = "Fecha UTC de creación, con precisión DATETIME(6). Se conserva al actualizar.")
        Instant createdAt,
        @Schema(description = "Fecha UTC persistida al hacer flush dentro de la transacción, con precisión DATETIME(6). Coincide con la lectura posterior de una escritura confirmada.")
        Instant updatedAt) {
}