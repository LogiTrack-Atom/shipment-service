package com.logitrack_atom.shipment_service.controller;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import com.logitrack_atom.shipment_service.service.RepartidorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repartidores")
@RequiredArgsConstructor
public class RepartidorController {

    private final RepartidorService repartidorService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepartidorResponse registrar(@Valid @RequestBody RepartidorRequest request) {
        return repartidorService.registrar(request);
    }

    @GetMapping
    public List<RepartidorResponse> listar(@RequestParam(required = false) EstadoRepartidor estado) {
        return repartidorService.listar(estado);
    }

    @GetMapping("/{id}")
    public RepartidorResponse obtenerPorId(@PathVariable UUID id) {
        return repartidorService.obtenerPorId(id);
    }
}