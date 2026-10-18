package com.logitrack_atom.shipment_service.entity;

import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "repartidores")
@Getter
@Setter
@NoArgsConstructor
public class Repartidor extends BaseEntity {

    @Column(nullable = false)
    private String nombre;

    @Column(name = "tipo_vehiculo", nullable = false)
    private String tipoVehiculo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRepartidor estado = EstadoRepartidor.ACTIVO;
}