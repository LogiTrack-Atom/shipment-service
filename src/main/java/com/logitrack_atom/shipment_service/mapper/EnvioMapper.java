package com.logitrack_atom.shipment_service.mapper;

import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnvioMapper {

    private final ClienteMapper clienteMapper;
    private final RepartidorMapper repartidorMapper;
    private final DireccionMapper direccionMapper;

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

    public EnvioResponse toResponse(Envio envio) {
        RepartidorResponse repartidor = envio.getRepartidor() == null
                ? null
                : repartidorMapper.toResponse(envio.getRepartidor());

        return new EnvioResponse(
                envio.getId(),
                envio.getCodigoSeguimiento(),
                clienteMapper.toResponse(envio.getCliente()),
                repartidor,
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
}