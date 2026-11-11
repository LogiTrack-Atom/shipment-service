package com.logitrack_atom.shipment_service.dto.response;

import java.util.UUID;

/**
 * Confirmación administrativa de una asociación local inmutable.
 *
 * @param recursoId UUID de cliente o repartidor
 * @param usuarioId cuenta asociada, visible solo para ADMIN
 * @author MaxDz
 * @version 0.2.0
 */
public record AsociacionUsuarioResponse(UUID recursoId, UUID usuarioId) {}
