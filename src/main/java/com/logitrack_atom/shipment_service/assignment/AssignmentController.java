package com.logitrack_atom.shipment_service.assignment;

import com.logitrack_atom.shipment_service.dto.response.ApiError;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** API administrativa de aceptación y seguimiento local de asignaciones asíncronas. */
@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Asignación asíncrona")
public class AssignmentController {
    private final AssignmentService service;

    /**
     * Recibe el servicio de operaciones autorizadas.
     * @param service aceptación autorizada y consulta de operaciones
     */
    public AssignmentController(AssignmentService service) { this.service = service; }

    /**
     * Acepta una intención y devuelve su ubicación después del commit.
     * @param id envío objetivo
     * @param key identidad estable del intento del cliente
     * @param request intención mínima validada
     * @param servlet contexto HTTP; solo admite el prefijo conocido del Gateway
     * @return 202 y ubicación de la operación después del commit local
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Solicitar asignación asíncrona",
            description = "ADMIN. Acepta después del commit local, sin esperar a Courier ni al broker. Reutilizar Idempotency-Key para reconciliar la misma intención.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Operación persistida y consultable"))
    @PostMapping("/api/v1/envios/{id}/solicitudes-asignacion")
    public ResponseEntity<AssignmentOperation> accept(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody AssignmentRequest request, HttpServletRequest servlet) {
        var operation = service.accept(id, request.repartidorId(), key);
        String prefix = "/applications/apishipment".equals(servlet.getHeader("X-Forwarded-Prefix"))
                ? "/applications/apishipment" : "";
        return ResponseEntity.accepted().location(URI.create(prefix + "/api/v1/operaciones-asignacion/" + operation.operationId())).body(operation);
    }

    /**
     * Consulta una operación persistida.
     * @param id operación local
     * @return estado autorizado, sin llamadas remotas
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Consultar operación de asignación", description = "ADMIN. Lectura local sin depender de RabbitMQ.")
    @GetMapping("/api/v1/operaciones-asignacion/{id}")
    public AssignmentOperation find(@PathVariable UUID id) { return service.find(id); }

    /**
     * Traduce un rechazo a un error público seguro.
     * @param error rechazo de negocio seguro
     * @return error común sin SQL ni credenciales
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> rejected(ResponseStatusException error) {
        var status = HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), error.getReason(), List.of()));
    }

    /**
     * Traduce un rechazo a un error público seguro.
     * @param error cabecera obligatoria ausente
     * @return error 400 sin ejecutar el servicio
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> missingKey(MissingRequestHeaderException error) {
        return rejected(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta Idempotency-Key"));
    }
}
