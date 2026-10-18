package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.request.ClienteRequest;
import com.logitrack_atom.shipment_service.dto.response.ClienteResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import org.springframework.stereotype.Component;

@Component
public class ClienteMapper {

    public Cliente toEntity(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNombre(request.nombre());
        cliente.setEmail(request.email());
        cliente.setTelefono(request.telefono());
        return cliente;
    }

    public ClienteResponse toResponse(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getEmail(),
                cliente.getTelefono());
    }
}