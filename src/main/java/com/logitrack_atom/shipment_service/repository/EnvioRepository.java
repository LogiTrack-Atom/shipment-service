package com.logitrack_atom.shipment_service.repository;

import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvioRepository extends JpaRepository<Envio, UUID> {

    Optional<Envio> findByCodigoSeguimiento(String codigoSeguimiento);

    List<Envio> findByEstado(EstadoEnvio estado);

    boolean existsByCodigoSeguimiento(String codigoSeguimiento);
}