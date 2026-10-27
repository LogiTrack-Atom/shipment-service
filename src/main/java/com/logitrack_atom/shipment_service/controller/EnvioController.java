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

/**
 * API del agregado de envíos; la administración de repartidores pertenece a courier.
 * @author MaxDz
 * @version 0.2.0
 */
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@Tag(name = "Envíos")
@RestController
@RequestMapping("/api/v1/envios")
@RequiredArgsConstructor
public class EnvioController {

    private final ShipmentService shipmentService;

    /**
     * Registra un envío y su evento inicial.
     * @param request datos validados del envío
     * @return envío creado
     */
    @Operation(operationId = "registrarEnvio", summary = "Registrar un envío",
            description = "Crea un envío PENDIENTE y su historial inicial en una sola transacción. ADMIN puede reutilizar el cliente por email. CLIENTE usa su asociación local o crea un cliente nuevo vinculado al sub; nunca reclama contactos previos por email. Peso positivo en kg (7 enteros/3 decimales); dimensiones positivas en cm (8 enteros/2 decimales).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Envío registrado con tracking, UUID y auditoría.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/MedidasInvalidas"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoDeDatos"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvioResponse registrar(@Valid @RequestBody EnvioRequest request) {
        return shipmentService.registrar(request);
    }

    /**
     * Consulta envíos sin acceder a courier.
     * @param estado filtro opcional
     * @return envíos locales coincidentes
     */
    @Operation(operationId = "listarEnvios", summary = "Listar envíos por estado",
            description = "El filtro estado es opcional; al omitirlo devuelve los visibles para el usuario. ADMIN ve todos; CLIENTE solo propios y REPARTIDOR solo asignados, con filtro SQL por identidad. Sin coincidencias devuelve una lista vacía. No garantiza un orden específico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de envíos.",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = EnvioResponse.class)))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/EstadoInvalido"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado")
    })
    @GetMapping
    public List<EnvioResponse> listar(@RequestParam(required = false) EstadoEnvio estado) {
        return shipmentService.listarPorEstado(estado);
    }

    /**
     * Consulta un envío por UUID.
     * @param id identificador del envío
     * @return DTO con referencia remota nullable
     */
    @Operation(operationId = "obtenerEnvio", summary = "Consultar un envío por UUID",
            description = "Devuelve datos locales y repartidorId nullable, sin llamadas a courier. Desde Sprint02 no incluye el objeto repartidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envío encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/UuidInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado")
    })
    @GetMapping("/{id}")
    public EnvioResponse obtenerPorId(@PathVariable UUID id) {
        return shipmentService.obtenerPorId(id);
    }

    /**
     * Consulta un envío por tracking.
     * @param codigoSeguimiento código único
     * @return envío local
     */
    @Operation(operationId = "seguirEnvio", summary = "Consultar por código de seguimiento",
            description = "Exige JWT y pertenencia al envío; conocer el tracking no concede acceso. ADMIN recibe 404 si no existe, los demás reciben 403 ante un envío inexistente o ajeno.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envío encontrado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/TrackingNoEncontrado"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado")
    })
    @GetMapping("/seguimiento/{codigoSeguimiento}")
    public EnvioResponse obtenerPorCodigoSeguimiento(@PathVariable String codigoSeguimiento) {
        return shipmentService.obtenerPorCodigoSeguimiento(codigoSeguimiento);
    }

    /**
     * Consulta el historial sin modificar eventos.
     * @param id identificador del envío
     * @return eventos por fecha ascendente
     */
    @Operation(operationId = "obtenerHistorial", summary = "Consultar historial de estados",
            description = "Devuelve el evento inicial y los cambios de estado por fechaCambio ascendente. No garantiza orden entre fechas idénticas. Una asignación de repartidor no agrega eventos de estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos del envío ordenados por fecha.",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = HistorialEstadoEnvioResponse.class)))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/UuidInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado")
    })
    @GetMapping("/{id}/historial")
    public List<HistorialEstadoEnvioResponse> obtenerHistorial(@PathVariable UUID id) {
        return shipmentService.obtenerHistorial(id);
    }

    /**
     * Aplica una transición y registra su historial atómicamente.
     * @param id identificador del envío
     * @param request estado solicitado
     * @return envío actualizado
     */
    @Operation(operationId = "cambiarEstadoEnvio", summary = "Cambiar el estado de un envío",
            description = "Permite PENDIENTE → EN_RUTA/CANCELADO y EN_RUTA → ENTREGADO. EN_RUTA requiere verificar disponibilidad del UUID asignado mediante CourierClient. ADMIN puede aplicar todas las transiciones de negocio. REPARTIDOR solo EN_RUTA y ENTREGADO en sus asignaciones; CLIENTE no puede cambiar estados. El JWT validado se propaga a courier al iniciar ruta. Un fallo remoto no modifica el envío. ENTREGADO y CANCELADO son terminales; repetir el estado se rechaza. Estado e historial se confirman o revierten juntos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado e historial actualizados.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/CuerpoInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/TransicionInvalida"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado"),
            @ApiResponse(responseCode = "502", ref = "#/components/responses/CourierRespuestaInvalida"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/CourierNoDisponible")
    })
    @PatchMapping("/{id}/estado")
    public EnvioResponse cambiarEstado(@PathVariable UUID id,
                                       @Valid @RequestBody CambioEstadoRequest request) {
        return shipmentService.cambiarEstado(id, request.estado());
    }

    /**
     * Cancela un envío pendiente.
     * @param id identificador del envío
     * @return envío cancelado
     */
    @Operation(operationId = "cancelarEnvio", summary = "Cancelar un envío pendiente",
            description = "Exclusivo ADMIN. Solo admite PENDIENTE → CANCELADO. Registra el evento en la misma transacción; repetir una cancelación devuelve 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envío cancelado con su evento.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/UuidInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/EnvioNoEncontrado"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/CancelacionInvalida"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado")
    })
    @PatchMapping("/{id}/cancelacion")
    public EnvioResponse cancelar(@PathVariable UUID id) {
        return shipmentService.cancelar(id);
    }

    /**
     * Asigna el UUID remoto después de verificar disponibilidad.
     * @param id identificador del envío
     * @param request identificador del repartidor
     * @return envío con asignación confirmada
     */
    @Operation(operationId = "asignarRepartidor", summary = "Asignar un repartidor activo",
            description = "Asigna o reemplaza el UUID únicamente en PENDIENTE y tras verificar disponibilidad mediante CourierClient. Exige Bearer JWT ADMIN validado localmente. Inactivo 422, inexistente 404, indisponibilidad 503 y respuesta inválida 502, sin mutación. No cambia el estado ni crea un evento de historial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repartidor asignado.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvioResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/CuerpoInvalido"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/AsignacionNoEncontrada"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/AsignacionInvalida"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoDeDatos"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado"),
            @ApiResponse(responseCode = "502", ref = "#/components/responses/CourierRespuestaInvalida"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/CourierNoDisponible")
    })
    @PutMapping("/{id}/repartidor")
    public EnvioResponse asignarRepartidor(@PathVariable UUID id,
                                           @Valid @RequestBody AsignarRepartidorRequest request) {
        return shipmentService.asignarRepartidor(id, request.repartidorId());
    }
}
