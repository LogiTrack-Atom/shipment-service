package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;

import java.util.List;
import java.util.UUID;

public interface ShipmentService {

    EnvioResponse registrar(EnvioRequest request);

    EnvioResponse obtenerPorId(UUID id);

    EnvioResponse obtenerPorCodigoSeguimiento(String codigoSeguimiento);

    List<EnvioResponse> listarPorEstado(EstadoEnvio estado);

    EnvioResponse cancelar(UUID id);

    EnvioResponse cambiarEstado(UUID id, EstadoEnvio nuevoEstado);

    EnvioResponse asignarRepartidor(UUID envioId, UUID repartidorId);

    List<HistorialEstadoEnvioResponse> obtenerHistorial(UUID id);
}