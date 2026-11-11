package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.client.IdentidadClient;
import com.logitrack_atom.shipment_service.dto.response.AsociacionUsuarioResponse;
import com.logitrack_atom.shipment_service.enums.Rol;
import com.logitrack_atom.shipment_service.exception.*;
import com.logitrack_atom.shipment_service.repository.*;
import com.logitrack_atom.shipment_service.service.impl.AsociacionUsuarioWriter;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Coordina comprobaciones administrativas de identidad antes de la escritura local.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Service
public class AsociacionUsuarioService {
    private final ClienteRepository clientes;
    private final RepartidorUsuarioRepository repartidores;
    private final IdentidadClient client;
    private final AsociacionUsuarioWriter writer;

    /**
     * Recibe repositorios, transporte de comprobación y escritor transaccional.
     *
     * @param clientes clientes locales
     * @param repartidores referencias locales de courier
     * @param client proveedor de identidades
     * @param writer escritura de asociaciones verificadas
     */
    public AsociacionUsuarioService(
            ClienteRepository clientes,
            RepartidorUsuarioRepository repartidores,
            IdentidadClient client,
            AsociacionUsuarioWriter writer) {
        this.clientes = clientes;
        this.repartidores = repartidores;
        this.client = client;
        this.writer = writer;
    }

    /**
     * Verifica y vincula una cuenta CLIENTE sin reclamar el registro por email.
     *
     * @param id UUID del cliente
     * @param usuarioId cuenta existente
     * @return asociación confirmada o la misma asociación idempotente
     */
    @PreAuthorize("hasRole('ADMIN')")
    public AsociacionUsuarioResponse cliente(UUID id, UUID usuarioId) {
        var row =
                clientes.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
        if (row.getUsuarioId() != null) {
            if (!row.getUsuarioId().equals(usuarioId)) throw new AsociacionConflictoException();
            return new AsociacionUsuarioResponse(id, usuarioId);
        }
        client.verificarCuenta(usuarioId, Rol.CLIENTE);
        return writer.cliente(id, usuarioId);
    }

    /**
     * Verifica cuenta REPARTIDOR y asociación propietaria antes de registrar la referencia.
     *
     * @param id UUID del repartidor existente en courier
     * @param usuarioId cuenta asociada inmutable
     * @return asociación administrativa local
     */
    @PreAuthorize("hasRole('ADMIN')")
    public AsociacionUsuarioResponse repartidor(UUID id, UUID usuarioId) {
        var old = repartidores.findById(id);
        if (old.isPresent()) {
            if (!old.get().getUsuarioId().equals(usuarioId))
                throw new AsociacionConflictoException();
            return new AsociacionUsuarioResponse(id, usuarioId);
        }
        client.verificarCuenta(usuarioId, Rol.REPARTIDOR);
        client.verificarRepartidor(id, usuarioId);
        return writer.repartidor(id, usuarioId);
    }
}
