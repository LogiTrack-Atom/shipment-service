package com.logitrack_atom.shipment_service.service.impl;

import com.logitrack_atom.shipment_service.client.CourierClient;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
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
import com.logitrack_atom.shipment_service.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestiona el agregado de envíos y su historial dentro de transacciones locales.
 *
 * <p>La asignación y el inicio de ruta exigen disponibilidad mediante
 * {@link CourierClient}. Las lecturas no consultan servicios remotos.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 * @see ShipmentService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final EnvioRepository envioRepository;
    private final ClienteRepository clienteRepository;
    private final CourierClient courierClient;
    private final HistorialEstadoEnvioRepository historialRepository;
    private final EnvioMapper envioMapper;
    private final ClienteMapper clienteMapper;
    private final HistorialEstadoEnvioMapper historialMapper;

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorId(UUID id) {
        return envioMapper.toResponse(buscarEnvio(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorCodigoSeguimiento(String codigoSeguimiento) {
        Envio envio = envioRepository.findByCodigoSeguimiento(codigoSeguimiento)
                .orElseThrow(() -> new RecursoNoEncontradoException("Envio", codigoSeguimiento));
        return envioMapper.toResponse(envio);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<EnvioResponse> listarPorEstado(EstadoEnvio estado) {
        log.debug("envio.consulta estado={}", estado == null ? "TODOS" : estado);
        List<Envio> envios = estado == null
                ? envioRepository.findAll()
                : envioRepository.findByEstado(estado);
        return envios.stream().map(envioMapper::toResponse).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public EnvioResponse cancelar(UUID id) {
        return cambiarEstado(id, EstadoEnvio.CANCELADO);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public EnvioResponse cambiarEstado(UUID id, EstadoEnvio nuevoEstado) {
        Envio envio = buscarEnvioParaModificar(id);
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

    /** {@inheritDoc} */
    @Override
    @Transactional
    public EnvioResponse asignarRepartidor(UUID envioId, UUID repartidorId) {
        Envio envio = buscarEnvioParaModificar(envioId);
        if (envio.getEstado() != EstadoEnvio.PENDIENTE) {
            log.warn("envio.asignacion_rechazada envioId={} estado={}", envioId, envio.getEstado());
            throw new ReglaNegocioException("Solo se puede asignar repartidor a un envío PENDIENTE");
        }

        courierClient.verificarDisponible(repartidorId);

        envio.setRepartidorId(repartidorId);
        envioRepository.save(envio);
        BusinessLog.repartidorAsignado(envio.getId(), repartidorId);

        return envioMapper.toResponse(envio);
    }

    /** {@inheritDoc} */
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

    private Envio buscarEnvioParaModificar(UUID id) {
        return envioRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Envio", id));
    }

    private void validarRepartidorAsignado(Envio envio) {
        UUID repartidorId = envio.getRepartidorId();
        if (repartidorId == null) {
            log.warn("envio.en_ruta_rechazado envioId={} motivo=sin_repartidor", envio.getId());
            throw new ReglaNegocioException("El envío no tiene repartidor asignado");
        }
        courierClient.verificarDisponible(repartidorId);
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