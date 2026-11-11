package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.request.ClienteRequest;
import com.logitrack_atom.shipment_service.dto.response.ClienteResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;

import org.springframework.stereotype.Component;

/**
 * Convierte contactos y DTOs sin asignar ni exponer identidades de cuenta.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class ClienteMapper {

    /**
     * Convierte datos de entrada sin persistir ni conceder pertenencia.
     *
     * @param request datos de entrada validados
     * @return valor nuevo para el agregado local
     */
    public Cliente toEntity(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNombre(request.nombre());
        cliente.setEmail(request.email());
        cliente.setTelefono(request.telefono());
        return cliente;
    }

    /**
     * Expone los campos del contrato de respuesta.
     *
     * @param cliente valor local autorizado
     * @return DTO sin relaciones de persistencia
     */
    public ClienteResponse toResponse(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(), cliente.getNombre(), cliente.getEmail(), cliente.getTelefono());
    }

    /** Crea el conversor de contactos. */
    public ClienteMapper() {}
}
