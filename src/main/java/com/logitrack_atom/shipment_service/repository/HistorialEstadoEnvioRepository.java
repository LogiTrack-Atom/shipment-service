package com.logitrack_atom.shipment_service.repository;

import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistorialEstadoEnvioRepository extends JpaRepository<HistorialEstadoEnvio, UUID> {

    List<HistorialEstadoEnvio> findByEnvioIdOrderByFechaCambioAsc(UUID envioId);
}