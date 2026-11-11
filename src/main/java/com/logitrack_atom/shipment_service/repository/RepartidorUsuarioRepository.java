package com.logitrack_atom.shipment_service.repository;

import com.logitrack_atom.shipment_service.entity.RepartidorUsuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta referencias locales de autorización sin acceder a la base de courier.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public interface RepartidorUsuarioRepository extends JpaRepository<RepartidorUsuario, UUID> {
    /**
     * Resuelve la cuenta validada a su UUID de dominio comprobado.
     *
     * @param usuarioId cuenta del JWT
     * @return asociación local, o vacío
     */
    Optional<RepartidorUsuario> findByUsuarioId(UUID usuarioId);
}
