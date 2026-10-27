package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Solicita una asignación administrativa por UUID remoto, sin datos de repartidor.
 *
 * @param repartidorId UUID del recurso que courier debe comprobar
 * @author MaxDz
 * @version 0.2.0
 */
public record AsignarRepartidorRequest(@NotNull UUID repartidorId) {}
