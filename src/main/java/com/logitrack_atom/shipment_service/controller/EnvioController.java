package com.logitrack_atom.shipment_service.controller;

import com.logitrack_atom.shipment_service.dto.request.AsignarRepartidorRequest;
import com.logitrack_atom.shipment_service.dto.request.CambioEstadoRequest;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/envios")
@RequiredArgsConstructor
public class EnvioController {

    private final ShipmentService shipmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvioResponse registrar(@Valid @RequestBody EnvioRequest request) {
        return shipmentService.registrar(request);
    }

    @GetMapping
    public List<EnvioResponse> listar(@RequestParam(required = false) EstadoEnvio estado) {
        return shipmentService.listarPorEstado(estado);
    }

    @GetMapping("/{id}")
    public EnvioResponse obtenerPorId(@PathVariable UUID id) {
        return shipmentService.obtenerPorId(id);
    }

    @GetMapping("/seguimiento/{codigoSeguimiento}")
    public EnvioResponse obtenerPorCodigoSeguimiento(@PathVariable String codigoSeguimiento) {
        return shipmentService.obtenerPorCodigoSeguimiento(codigoSeguimiento);
    }

    @GetMapping("/{id}/historial")
    public List<HistorialEstadoEnvioResponse> obtenerHistorial(@PathVariable UUID id) {
        return shipmentService.obtenerHistorial(id);
    }

    @PatchMapping("/{id}/estado")
    public EnvioResponse cambiarEstado(@PathVariable UUID id,
                                       @Valid @RequestBody CambioEstadoRequest request) {
        return shipmentService.cambiarEstado(id, request.estado());
    }

    @PatchMapping("/{id}/cancelacion")
    public EnvioResponse cancelar(@PathVariable UUID id) {
        return shipmentService.cancelar(id);
    }

    @PutMapping("/{id}/repartidor")
    public EnvioResponse asignarRepartidor(@PathVariable UUID id,
                                           @Valid @RequestBody AsignarRepartidorRequest request) {
        return shipmentService.asignarRepartidor(id, request.repartidorId());
    }
}