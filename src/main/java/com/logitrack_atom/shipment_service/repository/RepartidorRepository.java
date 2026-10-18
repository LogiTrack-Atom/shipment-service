package com.logitrack_atom.shipment_service.repository;

import com.logitrack_atom.shipment_service.entity.Repartidor;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepartidorRepository extends JpaRepository<Repartidor, UUID> {

    List<Repartidor> findByEstado(EstadoRepartidor estado);
}