package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;

import org.springframework.stereotype.Component;

/**
 * Representa eventos de estado sin exponer entidades ni relaciones JPA.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class HistorialEstadoEnvioMapper {

    /**
     * Representa un evento ya autorizado por el servicio.
     *
     * @param historial evento persistido
     * @return datos del cambio de estado
     */
    public HistorialEstadoEnvioResponse toResponse(HistorialEstadoEnvio historial) {
        return new HistorialEstadoEnvioResponse(
                historial.getId(),
                historial.getEstadoAnterior(),
                historial.getEstadoNuevo(),
                historial.getFechaCambio());
    }

    /** Crea el conversor de eventos. */
    public HistorialEstadoEnvioMapper() {}
}
