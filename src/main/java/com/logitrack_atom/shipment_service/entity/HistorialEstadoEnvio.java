package com.logitrack_atom.shipment_service.entity;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "historial_estados_envio")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEstadoEnvio extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "envio_id", nullable = false, updatable = false)
    private Envio envio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", updatable = false)
    private EstadoEnvio estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, updatable = false)
    private EstadoEnvio estadoNuevo;

    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    private Instant fechaCambio;
}