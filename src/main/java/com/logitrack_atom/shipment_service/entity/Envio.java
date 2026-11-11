package com.logitrack_atom.shipment_service.entity;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Envío con cliente local y referencia al repartidor administrado por courier.
 *
 * <p>La referencia remota conserva su UUID sin relación JPA ni clave foránea.
 * Las consultas de este agregado no requieren disponibilidad de courier.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 * @see Cliente
 */
@Entity
@Table(name = "envios")
@Getter
@Setter
public class Envio extends BaseEntity {

    @Column(name = "codigo_seguimiento", nullable = false, unique = true, updatable = false)
    private String codigoSeguimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "repartidor_id", columnDefinition = "BINARY(16)")
    private UUID repartidorId;

    @Column(name = "peso_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal pesoKg;

    @Column(name = "ancho_cm", nullable = false, precision = 10, scale = 2)
    private BigDecimal anchoCm;

    @Column(name = "alto_cm", nullable = false, precision = 10, scale = 2)
    private BigDecimal altoCm;

    @Column(name = "largo_cm", nullable = false, precision = 10, scale = 2)
    private BigDecimal largoCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEnvio estado = EstadoEnvio.PENDIENTE;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "calle", column = @Column(name = "origen_calle", nullable = false)),
            @AttributeOverride(name = "numero", column = @Column(name = "origen_numero", nullable = false)),
            @AttributeOverride(name = "ciudad", column = @Column(name = "origen_ciudad", nullable = false)),
            @AttributeOverride(name = "codigoPostal", column = @Column(name = "origen_codigo_postal", nullable = false))
    })
    private Direccion origen;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "calle", column = @Column(name = "destino_calle", nullable = false)),
            @AttributeOverride(name = "numero", column = @Column(name = "destino_numero", nullable = false)),
            @AttributeOverride(name = "ciudad", column = @Column(name = "destino_ciudad", nullable = false)),
            @AttributeOverride(name = "codigoPostal", column = @Column(name = "destino_codigo_postal", nullable = false))
    })
    private Direccion destino;
    /** Crea un envío PENDIENTE sin UUID de repartidor. */
    public Envio() { }
}