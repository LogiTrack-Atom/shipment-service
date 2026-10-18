package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.request.DireccionRequest;
import com.logitrack_atom.shipment_service.dto.response.DireccionResponse;
import com.logitrack_atom.shipment_service.entity.Direccion;
import org.springframework.stereotype.Component;

@Component
public class DireccionMapper {

    public Direccion toEntity(DireccionRequest request) {
        return new Direccion(request.calle(), request.numero(), request.ciudad(), request.codigoPostal());
    }

    public DireccionResponse toResponse(Direccion direccion) {
        return new DireccionResponse(
                direccion.getCalle(),
                direccion.getNumero(),
                direccion.getCiudad(),
                direccion.getCodigoPostal());
    }
}