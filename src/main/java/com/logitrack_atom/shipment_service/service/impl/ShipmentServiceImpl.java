package com.logitrack_atom.shipment_service.service.impl;

import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import com.logitrack_atom.shipment_service.entity.Repartidor;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.exception.ReglaNegocioException;
import com.logitrack_atom.shipment_service.exception.TransicionEstadoInvalidaException;
import com.logitrack_atom.shipment_service.logging.BusinessLog;
import com.logitrack_atom.shipment_service.mapper.ClienteMapper;
import com.logitrack_atom.shipment_service.mapper.EnvioMapper;
import com.logitrack_atom.shipment_service.mapper.HistorialEstadoEnvioMapper;
import com.logitrack_atom.shipment_service.repository.ClienteRepository;
import com.logitrack_atom.shipment_service.repository.EnvioRepository;
import com.logitrack_atom.shipment_service.repository.HistorialEstadoEnvioRepository;
import com.logitrack_atom.shipment_service.repository.RepartidorRepository;
import com.logitrack_atom.shipment_service.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final EnvioRepository envioRepository;
    private final ClienteRepository clienteRepository;
    private final RepartidorRepository repartidorRepository;
    private final HistorialEstadoEnvioRepository historialRepository;
    private final EnvioMapper envioMapper;
    private final ClienteMapper clienteMapper;
    private final HistorialEstadoEnvioMapper historialMapper;

    @Override
    @Transactional
    public EnvioResponse registrar(EnvioRequest request) {
        Cliente cliente = clienteRepository.findByEmail(request.cliente().email())
                .orElseGet(() -> clienteRepository.save(clienteMapper.toEntity(request.cliente())));

        Envio envio = envioMapper.toEntity(request, cliente, generarCodigoSeguimiento());
        envioRepository.save(envio);
        registrarHistorial(envio, null, EstadoEnvio.PENDIENTE);
        BusinessLog.envioRegistrado(envio.getId(), envio.getEstado());

        return envioMapper.toResponse(envio);
    }

    @Override
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorId(UUID id) {
        return envioMapper.toResponse(buscarEnvio(id));
    }

    @Override
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorCodigoSeguimiento(String codigoSeguimiento) {
        Envio envio = envioRepository.findByCodigoSeguimiento(codigoSeguimiento)
                .orElseThrow(() -> new RecursoNoEncontradoException("Envio", codigoSeguimiento));
        return envioMapper.toResponse(envio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvioResponse> listarPorEstado(EstadoEnvio estado) {
        log.debug("envio.consulta estado={}", estado == null ? "TODOS" : estado);
        List<Envio> envios = estado == null
                ? envioRepository.findAll()
                : envioRepository.findByEstado(estado);
        return envios.stream().map(envioMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public EnvioResponse cancelar(UUID id) {
        return cambiarEstado(id, EstadoEnvio.CANCELADO);
    }

    @Override
    @Transactional
    public EnvioResponse cambiarEstado(UUID id, EstadoEnvio nuevoEstado) {
        Envio envio = buscarEnvio(id);
        EstadoEnvio estadoActual = envio.getEstado();

        if (!estadoActual.puedeTransicionarA(nuevoEstado)) {
            log.warn("envio.transicion_rechazada envioId={} anterior={} solicitado={}", id, estadoActual, nuevoEstado);
            throw new TransicionEstadoInvalidaException(estadoActual, nuevoEstado);
        }
        if (nuevoEstado == EstadoEnvio.EN_RUTA) {
            validarRepartidorAsignado(envio);
        }

        envio.setEstado(nuevoEstado);
        envioRepository.save(envio);
        registrarHistorial(envio, estadoActual, nuevoEstado);
        BusinessLog.estadoCambiado(envio.getId(), estadoActual, nuevoEstado);

        return envioMapper.toResponse(envio);
    }

    @Override
    @Transactional
    public EnvioResponse asignarRepartidor(UUID envioId, UUID repartidorId) {
        Envio envio = buscarEnvio(envioId);
        if (envio.getEstado() != EstadoEnvio.PENDIENTE) {
            log.warn("envio.asignacion_rechazada envioId={} estado={}", envioId, envio.getEstado());
            throw new ReglaNegocioException("Solo se puede asignar repartidor a un envío PENDIENTE");
        }

        Repartidor repartidor = repartidorRepository.findById(repartidorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Repartidor", repartidorId));
        if (repartidor.getEstado() != EstadoRepartidor.ACTIVO) {
            log.warn("envio.asignacion_rechazada envioId={} repartidorId={} estadoRepartidor={}",
                    envioId, repartidorId, repartidor.getEstado());
            throw new ReglaNegocioException("El repartidor no está activo");
        }

        envio.setRepartidor(repartidor);
        envioRepository.save(envio);
        BusinessLog.repartidorAsignado(envio.getId(), repartidor.getId());

        return envioMapper.toResponse(envio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialEstadoEnvioResponse> obtenerHistorial(UUID id) {
        buscarEnvio(id);
        return historialRepository.findByEnvioIdOrderByFechaCambioAsc(id).stream()
                .map(historialMapper::toResponse)
                .toList();
    }

    private Envio buscarEnvio(UUID id) {
        return envioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Envio", id));
    }

    private void validarRepartidorAsignado(Envio envio) {
        Repartidor repartidor = envio.getRepartidor();
        if (repartidor == null) {
            log.warn("envio.en_ruta_rechazado envioId={} motivo=sin_repartidor", envio.getId());
            throw new ReglaNegocioException("El envío no tiene repartidor asignado");
        }
        if (repartidor.getEstado() != EstadoRepartidor.ACTIVO) {
            log.warn("envio.en_ruta_rechazado envioId={} repartidorId={} estadoRepartidor={}",
                    envio.getId(), repartidor.getId(), repartidor.getEstado());
            throw new ReglaNegocioException("El repartidor asignado no está activo");
        }
    }

    private void registrarHistorial(Envio envio, EstadoEnvio anterior, EstadoEnvio nuevo) {
        historialRepository.save(new HistorialEstadoEnvio(envio, anterior, nuevo, Instant.now()));
    }

    private String generarCodigoSeguimiento() {
        String codigo;
        do {
            codigo = "LT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (envioRepository.existsByCodigoSeguimiento(codigo));
        return codigo;
    }
}