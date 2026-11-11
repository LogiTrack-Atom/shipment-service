package com.logitrack_atom.shipment_service.client;

import java.util.UUID;

/**
 * Contrato de disponibilidad de courier, sin entidad ni asociación de persistencia.
 * @param repartidorId identificador consultado
 * @param estado ACTIVO o INACTIVO
 * @param disponible disponibilidad observada, sin reserva ni garantía futura
 * @author MaxDz
 * @version 0.2.0
 */
public record DisponibilidadCourier(UUID repartidorId, String estado, boolean disponible) { }
