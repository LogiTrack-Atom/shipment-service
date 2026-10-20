package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

/** Contrato de repartidores: escritura transaccional y consultas de solo lectura mediante DTO. */
public interface RepartidorService {

    /**
     * Crea un repartidor ACTIVO. El UUID y la auditoría se generan en persistencia.
     * @param request nombre y tipo de vehículo previamente validados por Bean Validation
     * @return repartidor confirmado; si falla la persistencia, se revierte el alta
     * @throws DataIntegrityViolationException si los datos no caben en el esquema MySQL
     */
    RepartidorResponse registrar(RepartidorRequest request);

    /**
     * Lista los repartidores sin prometer un orden específico.
     * @param estado ACTIVO/INACTIVO, o null para incluir todos
     * @return lista de DTO, vacía si no hay coincidencias
     */
    List<RepartidorResponse> listar(EstadoRepartidor estado);

    /**
     * Consulta un repartidor por su UUID.
     * @param id UUID a buscar
     * @return representación persistida
     * @throws RecursoNoEncontradoException si no existe el UUID
     */
    RepartidorResponse obtenerPorId(UUID id);
}
