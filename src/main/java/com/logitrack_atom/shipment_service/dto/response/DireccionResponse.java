package com.logitrack_atom.shipment_service.dto.response;

/**
 * Vista de una dirección del agregado local.
 *
 * @param calle nombre de calle
 * @param numero número descriptivo
 * @param ciudad localidad
 * @param codigoPostal código postal
 * @author MaxDz
 * @version 0.2.0
 */
public record DireccionResponse(String calle, String numero, String ciudad, String codigoPostal) {}
