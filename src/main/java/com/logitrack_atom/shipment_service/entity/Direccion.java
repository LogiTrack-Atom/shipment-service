package com.logitrack_atom.shipment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

/**
 * Valor embebido de dirección, sin identidad ni repositorio independiente.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Embeddable
@Getter
@Setter
public class Direccion {

    @Column(nullable = false)
    private String calle;

    @Column(nullable = false)
    private String numero;

    @Column(nullable = false)
    private String ciudad;

    @Column(name = "codigo_postal", nullable = false)
    private String codigoPostal;

    /** Constructor de hidratación del valor embebido. */
    public Direccion() {}

    /**
     * Construye una dirección a partir de datos validados por el contrato de entrada.
     *
     * @param calle nombre de calle
     * @param numero número descriptivo
     * @param ciudad localidad
     * @param codigoPostal código postal
     */
    public Direccion(String calle, String numero, String ciudad, String codigoPostal) {
        this.calle = calle;
        this.numero = numero;
        this.ciudad = ciudad;
        this.codigoPostal = codigoPostal;
    }
}
