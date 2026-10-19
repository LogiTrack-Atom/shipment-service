package com.logitrack_atom.shipment_service.controller;

import com.logitrack_atom.shipment_service.dto.request.AsignarRepartidorRequest;
import com.logitrack_atom.shipment_service.dto.request.CambioEstadoRequest;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.service.ShipmentService;
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

@Tag(name = "Envíos")
@RestController
@RequestMapping("/api/v1/envios")
@RequiredArgsConstructor
public class EnvioController {

    private final ShipmentService shipmentService;

    @Operation(operationId = "registrarEnvio", summary = "Registrar un envío",
            description = "Crea un envío PENDIENTE y su historial inicial en una sola transacción. Reutiliza el cliente por email sin sobrescribir sus datos. Peso positivo en kg (7 enteros/3 decimales); dimensiones positivas en cm (8 enteros/2 decimales).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Envío registrado con tracking, UUID y auditoría.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/MedidasInvalidas"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoDeDatos")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvioResponse registrar(@Valid @RequestBody EnvioRequest request) {
        return shipmentService.registrar(request);
    }

    @Operation(operationId = "listarEnvios", summary = "Listar envíos por estado",
            description = "El filtro estado es opcional; al omitirlo devuelve todos. Sin coincidencias devuelve una lista vacía. No garantiza un orden específico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de envíos.",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = EnvioResponse.class)))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/EstadoInvalido")
    })
    @GetMapping
    public List<EnvioResponse> listar(@RequestParam(required = false) EstadoEnvio estado) {
        return shipmentService.listarPorEstado(estado);
    }

    @Operation(operationId = "obtenerEnvio", summary = "Consultar un envío por UUID",
            description = "Devuelve los datos persistidos y su estado actual, sin modificarlos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envío encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/UuidInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado")
    })
    @GetMapping("/{id}")
    public EnvioResponse obtenerPorId(@PathVariable UUID id) {
        return shipmentService.obtenerPorId(id);
    }

    @Operation(operationId = "seguirEnvio", summary = "Consultar por código de seguimiento",
            description = "Busca el código único generado durante el registro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envío encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/TrackingNoEncontrado")
    })
    @GetMapping("/seguimiento/{codigoSeguimiento}")
    public EnvioResponse obtenerPorCodigoSeguimiento(@PathVariable String codigoSeguimiento) {
        return shipmentService.obtenerPorCodigoSeguimiento(codigoSeguimiento);
    }

    @Operation(operationId = "obtenerHistorial", summary = "Consultar historial de estados",
            description = "Devuelve el evento inicial y los cambios de estado por fechaCambio ascendente. No garantiza orden entre fechas idénticas. Una asignación de repartidor no agrega eventos de estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos del envío ordenados por fecha.",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = HistorialEstadoEnvioResponse.class)))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/UuidInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado")
    })
    @GetMapping("/{id}/historial")
    public List<HistorialEstadoEnvioResponse> obtenerHistorial(@PathVariable UUID id) {
        return shipmentService.obtenerHistorial(id);
    }

    @Operation(operationId = "cambiarEstadoEnvio", summary = "Cambiar el estado de un envío",
            description = "Permite PENDIENTE → EN_RUTA/CANCELADO y EN_RUTA → ENTREGADO. EN_RUTA requiere un repartidor asignado ACTIVO. ENTREGADO y CANCELADO son terminales; repetir el estado se rechaza. Estado e historial se confirman o revierten juntos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado e historial actualizados.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/CuerpoInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/TransicionInvalida")
    })
    @PatchMapping("/{id}/estado")
    public EnvioResponse cambiarEstado(@PathVariable UUID id,
                                       @Valid @RequestBody CambioEstadoRequest request) {
        return shipmentService.cambiarEstado(id, request.estado());
    }

    @Operation(operationId = "cancelarEnvio", summary = "Cancelar un envío pendiente",
            description = "Solo admite PENDIENTE → CANCELADO. Registra el evento en la misma transacción; repetir una cancelación devuelve 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envío cancelado con su evento.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/UuidInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/CancelacionInvalida")
    })
    @PatchMapping("/{id}/cancelacion")
    public EnvioResponse cancelar(@PathVariable UUID id) {
        return shipmentService.cancelar(id);
    }

    @Operation(operationId = "asignarRepartidor", summary = "Asignar un repartidor activo",
            description = "Asigna o reemplaza el repartidor únicamente si el envío está PENDIENTE y el repartidor está ACTIVO. No cambia el estado ni crea un evento de historial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repartidor asignado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/CuerpoInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/AsignacionNoEncontrada"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/AsignacionInvalida"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoDeDatos")
    })
    @PutMapping("/{id}/repartidor")
    public EnvioResponse asignarRepartidor(@PathVariable UUID id,
                                           @Valid @RequestBody AsignarRepartidorRequest request) {
        return shipmentService.asignarRepartidor(id, request.repartidorId());
    }
}
