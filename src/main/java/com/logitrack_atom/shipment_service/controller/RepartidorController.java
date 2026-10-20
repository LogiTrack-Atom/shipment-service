package com.logitrack_atom.shipment_service.controller;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import com.logitrack_atom.shipment_service.service.RepartidorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Repartidores")
@RestController
@RequestMapping("/api/v1/repartidores")
@RequiredArgsConstructor
public class RepartidorController {

    private final RepartidorService repartidorService;

    @Operation(operationId = "registrarRepartidor", summary = "Registrar un repartidor",
            description = "Crea un repartidor ACTIVO en una transacción. Nombre y tipoVehiculo son obligatorios y no pueden estar en blanco.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Repartidor registrado con UUID.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RepartidorResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/CuerpoInvalido"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoDeDatos")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepartidorResponse registrar(@Valid @RequestBody RepartidorRequest request) {
        return repartidorService.registrar(request);
    }

    @Operation(operationId = "listarRepartidores", summary = "Listar repartidores por estado",
            description = "Filtra opcionalmente por ACTIVO/INACTIVO. Sin filtro devuelve todos; sin coincidencias devuelve una lista vacía. No garantiza orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de repartidores.",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RepartidorResponse.class)))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/EstadoInvalido")
    })
    @GetMapping
    public List<RepartidorResponse> listar(@RequestParam(required = false) EstadoRepartidor estado) {
        return repartidorService.listar(estado);
    }

    @Operation(operationId = "obtenerRepartidor", summary = "Consultar un repartidor por UUID",
            description = "Devuelve el repartidor persistido, sin modificar sus datos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repartidor encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RepartidorResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/UuidInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/RepartidorNoEncontrado")
    })
    @GetMapping("/{id}")
    public RepartidorResponse obtenerPorId(@PathVariable UUID id) {
        return repartidorService.obtenerPorId(id);
    }
}
