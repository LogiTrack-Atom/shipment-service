package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;

import org.springframework.stereotype.Component;

/**
 * Convierte envíos y DTOs utilizando únicamente datos del agregado local.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class EnvioMapper {

    private final ClienteMapper clienteMapper;
    private final DireccionMapper direccionMapper;

    /**
     * Construye un envío pendiente sin asignación.
     *
     * @param request datos validados
     * @param cliente cliente persistido
     * @param codigoSeguimiento código único generado
     * @return entidad nueva
     */
    public Envio toEntity(EnvioRequest request, Cliente cliente, String codigoSeguimiento) {
        Envio envio = new Envio();
        envio.setCodigoSeguimiento(codigoSeguimiento);
        envio.setCliente(cliente);
        envio.setPesoKg(request.pesoKg());
        envio.setAnchoCm(request.anchoCm());
        envio.setAltoCm(request.altoCm());
        envio.setLargoCm(request.largoCm());
        envio.setOrigen(direccionMapper.toEntity(request.origen()));
        envio.setDestino(direccionMapper.toEntity(request.destino()));
        return envio;
    }

    /**
     * Representa el envío sin realizar consultas remotas.
     *
     * @param envio entidad local
     * @return DTO con UUID remoto nullable
     */
    public EnvioResponse toResponse(Envio envio) {
        return new EnvioResponse(
                envio.getId(),
                envio.getCodigoSeguimiento(),
                clienteMapper.toResponse(envio.getCliente()),
                envio.getRepartidorId(),
                envio.getPesoKg(),
                envio.getAnchoCm(),
                envio.getAltoCm(),
                envio.getLargoCm(),
                envio.getEstado(),
                direccionMapper.toResponse(envio.getOrigen()),
                direccionMapper.toResponse(envio.getDestino()),
                envio.getCreatedAt(),
                envio.getUpdatedAt());
    }

    /**
     * Recibe las dependencias mediante inyección por constructor.
     *
     * @param clienteMapper conversión de contactos de entrada
     * @param direccionMapper conversión de direcciones locales
     */
    public EnvioMapper(ClienteMapper clienteMapper, DireccionMapper direccionMapper) {
        this.clienteMapper = clienteMapper;
        this.direccionMapper = direccionMapper;
    }
}
