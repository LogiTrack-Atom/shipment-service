package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.entity.Repartidor;
import org.springframework.stereotype.Component;

@Component
public class RepartidorMapper {

    public Repartidor toEntity(RepartidorRequest request) {
        Repartidor repartidor = new Repartidor();
        repartidor.setNombre(request.nombre());
        repartidor.setTipoVehiculo(request.tipoVehiculo());
        return repartidor;
    }

    public RepartidorResponse toResponse(Repartidor repartidor) {
        return new RepartidorResponse(
                repartidor.getId(),
                repartidor.getNombre(),
                repartidor.getTipoVehiculo(),
                repartidor.getEstado());
    }
}