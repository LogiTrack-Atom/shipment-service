package com.logitrack_atom.shipment_service.dto.response;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de estado de un envío cuya lectura fue autorizada.
 *
 * @param id UUID del evento
 * @param estadoAnterior estado previo, nulo para el evento inicial
 * @param estadoNuevo estado confirmado
 * @param fechaCambio instante UTC del evento con precisión de microsegundos
 * @author MaxDz
 * @version 0.2.0
 */
public record HistorialEstadoEnvioResponse(
        UUID id, EstadoEnvio estadoAnterior, EstadoEnvio estadoNuevo, Instant fechaCambio) {}
