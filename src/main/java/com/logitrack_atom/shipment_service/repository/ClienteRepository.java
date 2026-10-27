package com.logitrack_atom.shipment_service.repository;

import com.logitrack_atom.shipment_service.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta clientes por contacto o por su asociación local de identidad.
 * @author MaxDz
 * @version 0.2.0
 */
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    /**
     * Busca el contacto sin otorgarle pertenencia por esa coincidencia.
     * @param email correo normalizado
     * @return cliente existente
     */
    Optional<Cliente> findByEmail(String email);
    /**
     * Busca el cliente propio de una identidad validada.
     * @param usuarioId cuenta de security
     * @return cliente asociado
     */
    Optional<Cliente> findByUsuarioId(UUID usuarioId);

    /**
     * Bloquea el cliente durante una vinculación administrativa.
     * @param id UUID local
     * @return cliente bloqueado
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cliente c where c.id=:id")
    Optional<Cliente> findByIdForUpdate(UUID id);
}