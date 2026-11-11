package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.request.DireccionRequest;
import com.logitrack_atom.shipment_service.dto.response.DireccionResponse;
import com.logitrack_atom.shipment_service.entity.Direccion;

import org.springframework.stereotype.Component;

/**
 * Convierte el valor embebido de dirección y sus contratos de entrada y salida.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class DireccionMapper {

    /**
     * Convierte datos de entrada sin persistir ni conceder pertenencia.
     *
     * @param request datos de entrada validados
     * @return valor nuevo para el agregado local
     */
    public Direccion toEntity(DireccionRequest request) {
        return new Direccion(
                request.calle(), request.numero(), request.ciudad(), request.codigoPostal());
    }

    /**
     * Expone los campos del contrato de respuesta.
     *
     * @param direccion valor local autorizado
     * @return DTO sin relaciones de persistencia
     */
    public DireccionResponse toResponse(Direccion direccion) {
        return new DireccionResponse(
                direccion.getCalle(),
                direccion.getNumero(),
                direccion.getCiudad(),
                direccion.getCodigoPostal());
    }

    /** Crea el conversor de direcciones. */
    public DireccionMapper() {}
}
