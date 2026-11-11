package com.logitrack_atom.shipment_service.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Referencia local e inmutable de autorización, verificada administrativamente en courier.
 *
 * <p>No representa al repartidor ni duplica su estado o sus datos descriptivos. Courier no permite
 * sustituir asociaciones; por ello las lecturas locales no requieren consultas remotas ni
 * invalidación de una caché temporal.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Entity
@Table(name = "repartidor_usuarios")
public class RepartidorUsuario {
    @Id
    @Column(name = "repartidor_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID repartidorId;

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "verificado_at", nullable = false, updatable = false)
    private Instant verificadoAt;

    /** Constructor de hidratación JPA. */
    protected RepartidorUsuario() {}

    /**
     * Registra una asociación previamente comprobada por ADMIN.
     *
     * @param repartidorId identidad de dominio
     * @param usuarioId identidad autenticable
     */
    public RepartidorUsuario(UUID repartidorId, UUID usuarioId) {
        this.repartidorId = java.util.Objects.requireNonNull(repartidorId);
        this.usuarioId = java.util.Objects.requireNonNull(usuarioId);
        this.verificadoAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Obtiene el UUID del repartidor propietario de la asociación.
     *
     * @return UUID remoto del repartidor
     */
    public UUID getRepartidorId() {
        return repartidorId;
    }

    /**
     * Obtiene el UUID de la cuenta autenticable.
     *
     * @return UUID de la cuenta asociada
     */
    public UUID getUsuarioId() {
        return usuarioId;
    }

    /**
     * Obtiene el instante en que se verificó la asociación.
     *
     * @return fecha de la comprobación administrativa
     */
    public Instant getVerificadoAt() {
        return verificadoAt;
    }
}
