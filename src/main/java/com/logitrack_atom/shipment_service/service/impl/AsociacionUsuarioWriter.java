package com.logitrack_atom.shipment_service.service.impl;

import com.logitrack_atom.shipment_service.dto.response.AsociacionUsuarioResponse;
import com.logitrack_atom.shipment_service.entity.RepartidorUsuario;
import com.logitrack_atom.shipment_service.exception.*;
import com.logitrack_atom.shipment_service.repository.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persiste asociaciones ya verificadas sin HTTP dentro de la transacción local.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Service
public class AsociacionUsuarioWriter {
    private final ClienteRepository clientes;
    private final RepartidorUsuarioRepository repartidores;

    /**
     * Recibe persistencias locales con restricciones únicas de identidad.
     *
     * @param clientes clientes de shipment
     * @param repartidores referencias administrativas verificadas
     */
    public AsociacionUsuarioWriter(
            ClienteRepository clientes, RepartidorUsuarioRepository repartidores) {
        this.clientes = clientes;
        this.repartidores = repartidores;
    }

    /**
     * Vincula un cliente existente sin reemplazar otra cuenta.
     *
     * @param id UUID local
     * @param usuarioId identidad previamente comprobada
     * @return asociación confirmada
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AsociacionUsuarioResponse cliente(UUID id, UUID usuarioId) {
        var row =
                clientes.findByIdForUpdate(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
        if (row.getUsuarioId() != null && !row.getUsuarioId().equals(usuarioId))
            throw new AsociacionConflictoException();
        row.setUsuarioId(usuarioId);
        clientes.saveAndFlush(row);
        return new AsociacionUsuarioResponse(id, usuarioId);
    }

    /**
     * Registra una referencia de courier sin permitir sustituciones.
     *
     * @param id UUID remoto
     * @param usuarioId cuenta comprobada en security y courier
     * @return asociación local
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AsociacionUsuarioResponse repartidor(UUID id, UUID usuarioId) {
        var old = repartidores.findById(id);
        if (old.isPresent()) {
            if (!old.get().getUsuarioId().equals(usuarioId))
                throw new AsociacionConflictoException();
        } else repartidores.saveAndFlush(new RepartidorUsuario(id, usuarioId));
        return new AsociacionUsuarioResponse(id, usuarioId);
    }
}
