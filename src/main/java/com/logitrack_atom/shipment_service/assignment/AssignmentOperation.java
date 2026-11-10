package com.logitrack_atom.shipment_service.assignment;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado público consultable de una solicitud de asignación, sin credenciales del actor.
 *
 * @param operationId identidad estable para reconciliar una respuesta HTTP perdida
 * @param envioId agregado local solicitado
 * @param repartidorId UUID remoto solicitado, sin reservar capacidad
 * @param assignmentVersion generación local que invalida resultados viejos
 * @param status estado de la operación, independiente del estado del envío
 * @param reason motivo seguro de un resultado terminal o null
 * @param createdAt aceptación local UTC
 * @param updatedAt última modificación UTC
 * @param expiresAt vencimiento de negocio UTC
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record AssignmentOperation(UUID operationId, UUID envioId, UUID repartidorId,
        long assignmentVersion, String status, String reason,
        Instant createdAt, Instant updatedAt, Instant expiresAt) { }
