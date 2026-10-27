package com.logitrack_atom.shipment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Cliente local con vínculo opcional y único a una cuenta de security.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Entity
@Table(name = "clientes")
@Getter
@Setter
public class Cliente extends BaseEntity {

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    private String telefono;

    @Column(name = "usuario_id", unique = true, columnDefinition = "BINARY(16)")
    private java.util.UUID usuarioId;

    /** Crea un contacto sin identidad de cuenta asignada. */
    public Cliente() {}
}
