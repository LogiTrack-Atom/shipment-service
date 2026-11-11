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

import lombok.Getter;

import java.time.Instant;

/**
 * Evento persistido e inmutable de una transición, incluido el registro inicial.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Entity
@Table(name = "historial_estados_envio")
@Getter
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

    /** Constructor de hidratación JPA del evento. */
    public HistorialEstadoEnvio() {}

    /**
     * Construye un evento pendiente de persistencia, con fecha compatible con DATETIME(6).
     *
     * @param envio agregado local
     * @param estadoAnterior estado previo, nulo al registrar
     * @param estadoNuevo estado confirmado
     * @param fechaCambio instante del cambio
     */
    public HistorialEstadoEnvio(
            Envio envio, EstadoEnvio estadoAnterior, EstadoEnvio estadoNuevo, Instant fechaCambio) {
        this.envio = envio;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.fechaCambio =
                fechaCambio == null
                        ? null
                        : fechaCambio.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
