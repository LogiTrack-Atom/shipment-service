package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;

import java.util.List;
import java.util.UUID;

public interface RepartidorService {

    RepartidorResponse registrar(RepartidorRequest request);

    List<RepartidorResponse> listar(EstadoRepartidor estado);

    RepartidorResponse obtenerPorId(UUID id);
}