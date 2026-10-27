package com.logitrack_atom.shipment_service.controller;

import com.logitrack_atom.shipment_service.dto.request.AsociacionUsuarioRequest;
import com.logitrack_atom.shipment_service.dto.response.AsociacionUsuarioResponse;
import com.logitrack_atom.shipment_service.service.AsociacionUsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Expone exclusivamente a ADMIN la vinculación verificada de identidades.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class AsociacionUsuarioController {
    private final AsociacionUsuarioService service;

    /**
     * Recibe el servicio de asociaciones verificadas.
     *
     * @param service coordinador de asociaciones verificadas
     */
    public AsociacionUsuarioController(AsociacionUsuarioService service) {
        this.service = service;
    }

    /**
     * Vincula un cliente con una cuenta compatible existente.
     *
     * @param id UUID de cliente
     * @param request cuenta solicitada
     * @return asociación administrativa confirmada
     */
    @Operation(
            operationId = "asociarCliente",
            summary = "Vincular un cliente existente con una cuenta CLIENTE",
            description =
                    "Exclusivo ADMIN. Comprueba identidad y rol por REST antes de una escritura"
                        + " local. Las asociaciones son únicas, inmutables e idempotentes; las"
                        + " lecturas posteriores no requieren proveedores.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Asociación confirmada.",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(implementation = AsociacionUsuarioResponse.class))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/CuerpoInvalido"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/IdentidadNoEncontrada"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/AsociacionIncompatible"),
        @ApiResponse(responseCode = "422", ref = "#/components/responses/RolIncompatible"),
        @ApiResponse(
                responseCode = "502",
                ref = "#/components/responses/IdentidadRespuestaInvalida"),
        @ApiResponse(responseCode = "503", ref = "#/components/responses/IdentidadNoDisponible")
    })
    @PutMapping("/clientes/{id}/usuario")
    public AsociacionUsuarioResponse cliente(
            @PathVariable UUID id, @Valid @RequestBody AsociacionUsuarioRequest request) {
        return service.cliente(id, request.usuarioId());
    }

    /**
     * Registra una asociación inmutable ya vigente en courier.
     *
     * @param id UUID de repartidor
     * @param request cuenta que debe coincidir con el proveedor
     * @return referencia local confirmada
     */
    @Operation(
            operationId = "asociarRepartidor",
            summary = "Registrar asociación verificada de repartidor",
            description =
                    "Exclusivo ADMIN. Comprueba identidad y rol por REST antes de una escritura"
                        + " local. Las asociaciones son únicas, inmutables e idempotentes; las"
                        + " lecturas posteriores no requieren proveedores.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Asociación confirmada.",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(implementation = AsociacionUsuarioResponse.class))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/CuerpoInvalido"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/AutenticacionRequerida"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/AccesoDenegado"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/IdentidadNoEncontrada"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/AsociacionIncompatible"),
        @ApiResponse(responseCode = "422", ref = "#/components/responses/RolIncompatible"),
        @ApiResponse(
                responseCode = "502",
                ref = "#/components/responses/IdentidadRespuestaInvalida"),
        @ApiResponse(responseCode = "503", ref = "#/components/responses/IdentidadNoDisponible")
    })
    @PutMapping("/asociaciones/repartidores/{id}")
    public AsociacionUsuarioResponse repartidor(
            @PathVariable UUID id, @Valid @RequestBody AsociacionUsuarioRequest request) {
        return service.repartidor(id, request.usuarioId());
    }
}
