package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AsignarRepartidorRequest(@NotNull UUID repartidorId) {
}