package com.logitrack_atom.shipment_service.assignment;

/** Rechazo permanente de contrato con mensaje fijo, sin conservar el cuerpo recibido. */
public final class InvalidAssignmentResultException extends RuntimeException {
    /** Crea un rechazo que no contiene credenciales ni datos del productor. */
    public InvalidAssignmentResultException() { super("Resultado de asignación inválido"); }
}
