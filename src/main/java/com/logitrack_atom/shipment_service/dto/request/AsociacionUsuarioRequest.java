package com.logitrack_atom.shipment_service.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Solicitud exclusivamente administrativa de asociación de una cuenta existente.
 *
 * @param usuarioId UUID de cuenta que será verificado en su proveedor
 * @author MaxDz
 * @version 0.2.0
 */
public record AsociacionUsuarioRequest(@NotNull UUID usuarioId) {}
