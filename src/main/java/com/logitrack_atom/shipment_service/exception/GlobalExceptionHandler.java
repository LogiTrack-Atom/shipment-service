package com.logitrack_atom.shipment_service.exception;

import com.logitrack_atom.shipment_service.dto.response.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/**
 * Traduce errores de entrada, negocio y persistencia al contrato público.
 * @author MaxDz
 * @version 0.2.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Rechaza credenciales ausentes o inválidas sin repetir sus valores.
     * @param ex fallo de autenticación local
     * @return error 401 con desafío Bearer
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> manejarAutenticacion(AuthenticationException ex) {
        var response = construir(HttpStatus.UNAUTHORIZED, "Se requiere una credencial Bearer válida", List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header("WWW-Authenticate", "Bearer").body(response.getBody());
    }

    /**
     * Rechaza operaciones sin el rol o la pertenencia requeridos.
     * @param ex permiso local insuficiente
     * @return error público 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> manejarAcceso(AccessDeniedException ex) {
        return construir(HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta operación", List.of());
    }

    /**
     * Traduce respuestas inesperadas de courier sin exponer su contenido.
     * @param ex contrato remoto inválido
     * @return error público 502
     */
    @ExceptionHandler(CourierRespuestaInvalidaException.class)
    public ResponseEntity<ApiError> manejarCourierInvalido(CourierRespuestaInvalidaException ex) {
        return construir(HttpStatus.BAD_GATEWAY, ex.getMessage(), List.of());
    }

    /**
     * Traduce un recurso inexistente a 404.
     * @param ex error de la operación
     * @return respuesta con ApiError
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    /**
     * Traduce una precondición remota no verificable a 503.
     * @param ex indisponibilidad de courier
     * @return error público coherente con ApiError
     */
    @ExceptionHandler(CourierNoDisponibleException.class)
    public ResponseEntity<ApiError> manejarCourierNoDisponible(CourierNoDisponibleException ex) {
        return construir(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), List.of());
    }

    /**
     * Traduce una precondición incumplida a 422.
     * @param ex error de la operación
     * @return respuesta con ApiError
     */
    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiError> manejarReglaNegocio(ReglaNegocioException ex) {
        return construir(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), List.of());
    }

    /**
     * Expone los campos que incumplen Bean Validation.
     * @param ex error de la operación
     * @return respuesta con ApiError
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> manejarValidacion(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", detalles);
    }

    /**
     * Rechaza JSON, enum o UUID ilegible.
     * @param ex error de la operación
     * @return respuesta con ApiError
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        for(Throwable cause=ex;cause!=null;cause=cause.getCause()) {
            if(cause instanceof AccessDeniedException denied)return manejarAcceso(denied);
        }
        return construir(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud es inválido o contiene un valor no permitido", List.of());
    }

    /**
     * Rechaza un parámetro que no puede convertirse.
     * @param ex error de la operación
     * @return respuesta con ApiError
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> manejarTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return construir(HttpStatus.BAD_REQUEST,
                "Valor inválido para el parámetro '" + ex.getName() + "'", List.of());
    }

    /**
     * Traduce integridad de datos sin exponer SQL.
     * @param ex error de la operación
     * @return respuesta con ApiError
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> manejarIntegridad(DataIntegrityViolationException ex) {
        return construir(HttpStatus.CONFLICT,
                "La operación viola una restricción de datos, por ejemplo un valor duplicado", List.of());
    }

    /**
     * Expone un conflicto de identidad sin revelar su propietario.
     * @param ex conflicto de asociación
     * @return respuesta 409
     */
    @ExceptionHandler(AsociacionConflictoException.class)
    public ResponseEntity<ApiError> manejarAsociacion(AsociacionConflictoException ex) {
        return construir(HttpStatus.CONFLICT,ex.getMessage(),List.of());
    }

    /**
     * Traduce fallas de comprobación de identidades sin divulgar la respuesta remota.
     * @param ex rechazo del proveedor
     * @return ApiError 502 o 503
     */
    @ExceptionHandler(ServicioIdentidadException.class)
    public ResponseEntity<ApiError> manejarIdentidad(ServicioIdentidadException ex) {
        return construir(ex.getStatus(),ex.getMessage(),List.of());
    }

    private ResponseEntity<ApiError> construir(HttpStatus status, String mensaje, List<String> detalles) {
        ApiError cuerpo = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), mensaje, detalles);
        return ResponseEntity.status(status).body(cuerpo);
    }
    /** Crea el traductor de errores públicos. */
    public GlobalExceptionHandler() { }
}