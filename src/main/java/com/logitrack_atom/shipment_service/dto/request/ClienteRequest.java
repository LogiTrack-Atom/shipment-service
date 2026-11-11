package com.logitrack_atom.shipment_service.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import java.util.Set;

/**
 * Datos de contacto, sin identidad de cuenta elegible desde el cuerpo.
 *
 * @param nombre nombre descriptivo
 * @param email correo de contacto normalizado
 * @param telefono teléfono opcional
 * @author MaxDz
 * @version 0.2.0
 */
public record ClienteRequest(
        @NotBlank String nombre, @NotBlank @Email String email, String telefono) {
    /**
     * Normaliza el contacto sin utilizarlo como prueba de identidad.
     *
     * @param nombre nombre recibido
     * @param email correo recibido
     * @param telefono teléfono recibido
     */
    public ClienteRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Rechaza selectores de identidad o campos ajenos al contrato del DTO.
     *
     * @param campo nombre recibido
     * @param valor valor que no se utilizará para autorizar
     */
    @JsonAnySetter
    public void rechazarCampoAdicional(String campo, Object valor) {
        if (Set.of("id", "usuarioId", "clienteId", "repartidorId", "roles", "sub").contains(campo))
            throw new AccessDeniedException("Acceso denegado.");
        throw new IllegalArgumentException("Campo no admitido.");
    }
}
