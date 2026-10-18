package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import org.springframework.stereotype.Component;

@Component
public class HistorialEstadoEnvioMapper {

    public HistorialEstadoEnvioResponse toResponse(HistorialEstadoEnvio historial) {
        return new HistorialEstadoEnvioResponse(
                historial.getId(),
                historial.getEstadoAnterior(),
                historial.getEstadoNuevo(),
                historial.getFechaCambio());
    }
}