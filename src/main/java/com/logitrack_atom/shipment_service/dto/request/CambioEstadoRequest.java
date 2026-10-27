package com.logitrack_atom.shipment_service.dto.request;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

import jakarta.validation.constraints.NotNull;

/**
 * Solicita una transición sujeta al rol, pertenencia y reglas del envío.
 *
 * @param estado estado de destino, nunca un valor numérico
 * @author MaxDz
 * @version 0.2.0
 */
public record CambioEstadoRequest(@NotNull EstadoEnvio estado) {}
