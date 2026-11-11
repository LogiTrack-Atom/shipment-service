package com.logitrack_atom.shipment_service.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Datos de registro sin identificadores de cuenta elegibles desde el cuerpo.
 *
 * @param cliente contacto local sujeto a la pertenencia del usuario autenticado
 * @param origen dirección de origen
 * @param destino dirección de destino
 * @param pesoKg peso positivo en kilogramos
 * @param anchoCm ancho positivo en centímetros
 * @param altoCm alto positivo en centímetros
 * @param largoCm largo positivo en centímetros
 * @author MaxDz
 * @version 0.2.0
 */
public record EnvioRequest(
        @NotNull @Valid ClienteRequest cliente,
        @NotNull @Valid DireccionRequest origen,
        @NotNull @Valid DireccionRequest destino,
        @NotNull
                @Positive
                @Digits(
                        integer = 7,
                        fraction = 3,
                        message = "El peso admite hasta 7 dígitos enteros y 3 decimales")
                @Schema(
                        description = "Peso en kg: positivo, hasta 7 enteros y 3 decimales.",
                        example = "1.250",
                        minimum = "0",
                        exclusiveMinimum = true,
                        maximum = "9999999.999",
                        multipleOf = 0.001)
                BigDecimal pesoKg,
        @NotNull
                @Positive
                @Digits(
                        integer = 8,
                        fraction = 2,
                        message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
                @Schema(
                        description = "Ancho en cm: positivo, hasta 8 enteros y 2 decimales.",
                        example = "25.25",
                        minimum = "0",
                        exclusiveMinimum = true,
                        maximum = "99999999.99",
                        multipleOf = 0.01)
                BigDecimal anchoCm,
        @NotNull
                @Positive
                @Digits(
                        integer = 8,
                        fraction = 2,
                        message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
                @Schema(
                        description = "Alto en cm: positivo, hasta 8 enteros y 2 decimales.",
                        example = "15.50",
                        minimum = "0",
                        exclusiveMinimum = true,
                        maximum = "99999999.99",
                        multipleOf = 0.01)
                BigDecimal altoCm,
        @NotNull
                @Positive
                @Digits(
                        integer = 8,
                        fraction = 2,
                        message = "La dimensión admite hasta 8 dígitos enteros y 2 decimales")
                @Schema(
                        description = "Largo en cm: positivo, hasta 8 enteros y 2 decimales.",
                        example = "30.75",
                        minimum = "0",
                        exclusiveMinimum = true,
                        maximum = "99999999.99",
                        multipleOf = 0.01)
                BigDecimal largoCm) {

    /**
     * Rechaza selectores de identidad o campos ajenos al contrato del DTO.
     *
     * @param campo nombre recibido
     * @param valor valor que no se utilizará para autorizar
     */
    @JsonAnySetter
    public void rechazarCampoAdicional(String campo, Object valor) {
        if (Set.of("id", "usuarioId", "clienteId", "repartidorId", "roles", "sub").contains(campo))
            throw new AccessDeniedException("Acceso denegado.");
        throw new IllegalArgumentException("Campo no admitido.");
    }
}
