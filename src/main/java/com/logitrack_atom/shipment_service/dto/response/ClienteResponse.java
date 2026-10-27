package com.logitrack_atom.shipment_service.dto.response;

import java.util.UUID;

/**
 * Datos de contacto visibles en un envío autorizado; no expone la cuenta asociada.
 *
 * @param id UUID del cliente local
 * @param nombre nombre de contacto
 * @param email correo de contacto
 * @param telefono teléfono opcional
 * @author MaxDz
 * @version 0.2.0
 */
public record ClienteResponse(UUID id, String nombre, String email, String telefono) {}
