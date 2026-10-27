package com.logitrack_atom.shipment_service.service.impl;

import com.logitrack_atom.shipment_service.client.CourierClient;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.exception.AsociacionConflictoException;
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
import com.logitrack_atom.shipment_service.security.ShipmentAccess;
import com.logitrack_atom.shipment_service.service.ShipmentService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestiona el agregado de envíos y su historial dentro de transacciones locales.
 *
 * <p>La asignación y el inicio de ruta exigen disponibilidad mediante {@link CourierClient}. Las
 * lecturas no consultan servicios remotos.
 *
 * @author MaxDz
 * @version 0.2.0
 * @see ShipmentService
 */
@Slf4j
@Service
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentAccess access;
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
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE')")
    public EnvioResponse registrar(EnvioRequest request) {
        Cliente cliente;
        if (access.esAdmin()) {
            cliente =
                    clienteRepository
                            .findByEmail(request.cliente().email())
                            .orElseGet(
                                    () ->
                                            clienteRepository.save(
                                                    clienteMapper.toEntity(request.cliente())));
        } else {
            var propio = clienteRepository.findByUsuarioId(access.usuarioId());
            if (propio.isPresent()) {
                cliente = propio.get();
                if (!cliente.getEmail().equalsIgnoreCase(request.cliente().email()))
                    throw new AccessDeniedException("Acceso denegado.");
            } else {
                if (clienteRepository.findByEmail(request.cliente().email()).isPresent())
                    throw new AsociacionConflictoException();
                cliente = clienteMapper.toEntity(request.cliente());
                cliente.setUsuarioId(access.usuarioId());
                cliente = clienteRepository.save(cliente);
            }
        }

        Envio envio = envioMapper.toEntity(request, cliente, generarCodigoSeguimiento());
        envioRepository.save(envio);
        registrarHistorial(envio, null, EstadoEnvio.PENDIENTE);
        envioRepository.flush();
        BusinessLog.envioRegistrado(envio.getId(), envio.getEstado());

        return envioMapper.toResponse(envio);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE','REPARTIDOR')")
    public EnvioResponse obtenerPorId(UUID id) {
        var envio = buscarEnvio(id);
        access.permitirLectura(envio);
        return envioMapper.toResponse(envio);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE','REPARTIDOR')")
    public EnvioResponse obtenerPorCodigoSeguimiento(String codigoSeguimiento) {
        Envio envio =
                envioRepository
                        .findByCodigoSeguimiento(codigoSeguimiento)
                        .orElseThrow(() -> noEncontrado(codigoSeguimiento));
        access.permitirLectura(envio);
        return envioMapper.toResponse(envio);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE','REPARTIDOR')")
    public List<EnvioResponse> listarPorEstado(EstadoEnvio estado) {
        log.debug("envio.consulta estado={}", estado == null ? "TODOS" : estado);
        List<Envio> envios;
        if (access.esAdmin())
            envios =
                    estado == null
                            ? envioRepository.findAll()
                            : envioRepository.findByEstado(estado);
        else
            envios =
                    envioRepository.findVisibles(
                            access.usuarioId(),
                            access.repartidorId(),
                            access.tieneRol("CLIENTE"),
                            access.tieneRol("REPARTIDOR"),
                            estado);
        return envios.stream().map(envioMapper::toResponse).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EnvioResponse cancelar(UUID id) {
        return cambiarEstado(id, EstadoEnvio.CANCELADO);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','REPARTIDOR')")
    public EnvioResponse cambiarEstado(UUID id, EstadoEnvio nuevoEstado) {
        Envio envio = buscarEnvioParaModificar(id);
        access.permitirCambio(envio, nuevoEstado);
        EstadoEnvio estadoActual = envio.getEstado();

        if (!estadoActual.puedeTransicionarA(nuevoEstado)) {
            log.warn(
                    "envio.transicion_rechazada envioId={} anterior={} solicitado={}",
                    id,
                    estadoActual,
                    nuevoEstado);
            throw new TransicionEstadoInvalidaException(estadoActual, nuevoEstado);
        }
        if (nuevoEstado == EstadoEnvio.EN_RUTA) {
            validarRepartidorAsignado(envio);
        }

        envio.setEstado(nuevoEstado);
        envioRepository.save(envio);
        registrarHistorial(envio, estadoActual, nuevoEstado);
        envioRepository.flush();
        BusinessLog.estadoCambiado(envio.getId(), estadoActual, nuevoEstado);

        return envioMapper.toResponse(envio);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EnvioResponse asignarRepartidor(UUID envioId, UUID repartidorId) {
        Envio envio = buscarEnvioParaModificar(envioId);
        if (envio.getEstado() != EstadoEnvio.PENDIENTE) {
            log.warn("envio.asignacion_rechazada envioId={} estado={}", envioId, envio.getEstado());
            throw new ReglaNegocioException(
                    "Solo se puede asignar repartidor a un envío PENDIENTE");
        }

        courierClient.verificarDisponible(repartidorId);

        envio.setRepartidorId(repartidorId);
        envioRepository.save(envio);
        envioRepository.flush();
        BusinessLog.repartidorAsignado(envio.getId(), repartidorId);

        return envioMapper.toResponse(envio);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','CLIENTE','REPARTIDOR')")
    public List<HistorialEstadoEnvioResponse> obtenerHistorial(UUID id) {
        access.permitirLectura(buscarEnvio(id));
        return historialRepository.findByEnvioIdOrderByFechaCambioAsc(id).stream()
                .map(historialMapper::toResponse)
                .toList();
    }

    private Envio buscarEnvio(UUID id) {
        return envioRepository.findById(id).orElseThrow(() -> noEncontrado(id));
    }

    private Envio buscarEnvioParaModificar(UUID id) {
        return envioRepository.findByIdForUpdate(id).orElseThrow(() -> noEncontrado(id));
    }

    private RuntimeException noEncontrado(Object id) {
        return access.esAdmin()
                ? new RecursoNoEncontradoException("Envio", id)
                : new AccessDeniedException("Acceso denegado.");
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
            codigo =
                    "LT-"
                            + UUID.randomUUID()
                                    .toString()
                                    .replace("-", "")
                                    .substring(0, 10)
                                    .toUpperCase();
        } while (envioRepository.existsByCodigoSeguimiento(codigo));
        return codigo;
    }

    /**
     * Recibe las dependencias mediante inyección por constructor.
     *
     * @param access evaluador de roles y pertenencia del usuario autenticado
     * @param envioRepository persistencia y bloqueos de envíos
     * @param clienteRepository persistencia y búsqueda de clientes asociados
     * @param courierClient consulta de disponibilidad de repartidores
     * @param historialRepository persistencia del historial en la transacción local
     * @param envioMapper conversión del agregado y sus respuestas
     * @param clienteMapper conversión de contactos de entrada
     * @param historialMapper conversión de eventos autorizados
     */
    public ShipmentServiceImpl(
            ShipmentAccess access,
            EnvioRepository envioRepository,
            ClienteRepository clienteRepository,
            CourierClient courierClient,
            HistorialEstadoEnvioRepository historialRepository,
            EnvioMapper envioMapper,
            ClienteMapper clienteMapper,
            HistorialEstadoEnvioMapper historialMapper) {
        this.access = access;
        this.envioRepository = envioRepository;
        this.clienteRepository = clienteRepository;
        this.courierClient = courierClient;
        this.historialRepository = historialRepository;
        this.envioMapper = envioMapper;
        this.clienteMapper = clienteMapper;
        this.historialMapper = historialMapper;
    }
}
