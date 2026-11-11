package com.logitrack_atom.shipment_service.repository;

import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persiste eventos y consulta el historial del envío en orden temporal.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public interface HistorialEstadoEnvioRepository extends JpaRepository<HistorialEstadoEnvio, UUID> {

    /**
     * Consulta el historial sin garantizar orden entre eventos de fecha idéntica.
     *
     * @param envioId UUID de envío previamente autorizado
     * @return eventos por fecha ascendente
     */
    List<HistorialEstadoEnvio> findByEnvioIdOrderByFechaCambioAsc(UUID envioId);
}
