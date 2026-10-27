package com.logitrack_atom.shipment_service.repository;

import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persiste el agregado local y serializa sus modificaciones por envío.
 * @author MaxDz
 * @version 0.2.0
 */
public interface EnvioRepository extends JpaRepository<Envio, UUID> {

    /**
     * Bloquea la fila hasta confirmar o revertir la transacción de negocio.
     * @param id UUID del envío que se modificará
     * @return agregado actual, o vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Envio e where e.id = :id")
    Optional<Envio> findByIdForUpdate(UUID id);

    /**
     * Consulta sin bloqueo de escritura por tracking.
     * @param codigoSeguimiento código único
     * @return envío encontrado, o vacío
     */
    Optional<Envio> findByCodigoSeguimiento(String codigoSeguimiento);

    /**
     * Consulta envíos con el estado indicado.
     * @param estado filtro del estado local
     * @return coincidencias sin orden garantizado
     */
    List<Envio> findByEstado(EstadoEnvio estado);

    /**
     * Comprueba si el código generado ya está ocupado.
     * @param codigoSeguimiento código candidato
     * @return verdadero si existe
     */
    boolean existsByCodigoSeguimiento(String codigoSeguimiento);
    /**
     * Filtra en SQL por las asociaciones y roles del usuario autenticado.
     * @param usuarioId identidad del JWT
     * @param repartidorId UUID de dominio comprobado, o nulo
     * @param cliente habilita la pertenencia de cliente
     * @param repartidor habilita la pertenencia de repartidor
     * @param estado filtro opcional
     * @return exclusivamente envíos visibles
     */
    @Query("select e from Envio e where (:estado is null or e.estado=:estado) and "
            + "((:cliente=true and e.cliente.usuarioId=:usuarioId) or (:repartidor=true and e.repartidorId=:repartidorId))")
    List<Envio> findVisibles(UUID usuarioId,UUID repartidorId,boolean cliente,boolean repartidor,EstadoEnvio estado);
}