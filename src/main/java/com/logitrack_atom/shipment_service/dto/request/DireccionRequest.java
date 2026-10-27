package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos obligatorios de una dirección de origen o destino.
 *
 * @param calle nombre de la calle
 * @param numero número descriptivo
 * @param ciudad localidad
 * @param codigoPostal código postal
 * @author MaxDz
 * @version 0.2.0
 */
public record DireccionRequest(
        @NotBlank String calle,
        @NotBlank String numero,
        @NotBlank String ciudad,
        @NotBlank String codigoPostal) {}
