package com.logitrack_atom.shipment_service.service.impl;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.entity.Repartidor;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.logging.BusinessLog;
import com.logitrack_atom.shipment_service.mapper.RepartidorMapper;
import com.logitrack_atom.shipment_service.repository.RepartidorRepository;
import com.logitrack_atom.shipment_service.service.RepartidorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepartidorServiceImpl implements RepartidorService {

    private final RepartidorRepository repartidorRepository;
    private final RepartidorMapper repartidorMapper;

    @Override
    @Transactional
    public RepartidorResponse registrar(RepartidorRequest request) {
        Repartidor repartidor = repartidorRepository.save(repartidorMapper.toEntity(request));
        BusinessLog.repartidorRegistrado(repartidor.getId(), repartidor.getEstado());
        return repartidorMapper.toResponse(repartidor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepartidorResponse> listar(EstadoRepartidor estado) {
        log.debug("repartidor.consulta estado={}", estado == null ? "TODOS" : estado);
        List<Repartidor> repartidores = estado == null
                ? repartidorRepository.findAll()
                : repartidorRepository.findByEstado(estado);
        return repartidores.stream().map(repartidorMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RepartidorResponse obtenerPorId(UUID id) {
        Repartidor repartidor = repartidorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Repartidor", id));
        return repartidorMapper.toResponse(repartidor);
    }
}