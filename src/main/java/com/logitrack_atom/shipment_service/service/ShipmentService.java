package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.exception.ReglaNegocioException;
import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.exception.TransicionEstadoInvalidaException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

/**
 * Contrato del ciclo de vida de un envío. Recibe solicitudes previamente validadas por Bean Validation.
 * Las escrituras son transaccionales; si falla un evento de historial se revierte toda la operación.
 * Las consultas usan transacciones de solo lectura y devuelven DTO, nunca entidades JPA.
 * La disponibilidad del repartidor se verifica en courier únicamente al mutar.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public interface ShipmentService {

    /**
     * Registra un envío PENDIENTE, con tracking generado y un evento inicial sin estado anterior.
     * Reutiliza el cliente encontrado por email sin sobrescribir sus datos; crea uno si no existe.
     * Cliente nuevo, envío e historial se confirman o revierten juntos.
     *
     * @param request datos válidos; kg con hasta 7 enteros/3 decimales y cm con 8 enteros/2 decimales
     * @return envío persistido con UUID, tracking y auditoría
     * @throws DataIntegrityViolationException si una restricción de MySQL impide guardar los datos
     */
    EnvioResponse registrar(EnvioRequest request);

    /**
     * Consulta por UUID sin modificar el envío.
     * @param id UUID del envío
     * @return representación persistida
     * @throws RecursoNoEncontradoException si no existe el UUID
     */
    EnvioResponse obtenerPorId(UUID id);

    /**
     * Consulta por el código de seguimiento único.
     * @param codigoSeguimiento código generado al registrar el envío
     * @return representación persistida
     * @throws RecursoNoEncontradoException si no existe el código
     */
    EnvioResponse obtenerPorCodigoSeguimiento(String codigoSeguimiento);

    /**
     * Lista envíos sin prometer un orden específico.
     * @param estado estado a filtrar; null devuelve todos
     * @return lista de DTO, vacía si no hay coincidencias
     */
    List<EnvioResponse> listarPorEstado(EstadoEnvio estado);

    /**
     * Cancela únicamente un envío PENDIENTE y agrega el evento CANCELADO en la misma transacción.
     * @param id UUID del envío
     * @return envío cancelado
     * @throws RecursoNoEncontradoException si no existe el envío
     * @throws TransicionEstadoInvalidaException si ya no está PENDIENTE, incluso si ya está CANCELADO
     */
    EnvioResponse cancelar(UUID id);

    /**
     * Aplica PENDIENTE → EN_RUTA/CANCELADO o EN_RUTA → ENTREGADO y registra el evento atómicamente.
     * EN_RUTA exige repartidor asignado y ACTIVO; ENTREGADO y CANCELADO son terminales.
     * Repetir el estado actual también se rechaza. Un fallo de persistencia revierte estado e historial.
     * @param id UUID del envío
     * @param nuevoEstado estado destino no nulo
     * @return envío actualizado
     * @throws RecursoNoEncontradoException si no existe el envío
     * @throws TransicionEstadoInvalidaException si el salto no está permitido
     * @throws ReglaNegocioException si EN_RUTA no tiene repartidor asignado y activo
     * @throws CourierNoDisponibleException si no se puede comprobar disponibilidad
     */
    EnvioResponse cambiarEstado(UUID id, EstadoEnvio nuevoEstado);

    /**
     * Asigna o reemplaza un repartidor ACTIVO solo mientras el envío está PENDIENTE.
     * No cambia el estado ni agrega un evento de historial de estados.
     * @param envioId UUID del envío
     * @param repartidorId UUID del repartidor
     * @return envío con la asignación confirmada
     * @throws RecursoNoEncontradoException si no existe alguno de los dos recursos
     * @throws ReglaNegocioException si el envío no está PENDIENTE o el repartidor no está ACTIVO
     * @throws CourierNoDisponibleException si no se puede comprobar disponibilidad
     */
    EnvioResponse asignarRepartidor(UUID envioId, UUID repartidorId);

    /**
     * Consulta los eventos por fecha de cambio ascendente; no modifica el historial.
     * @param id UUID del envío
     * @return eventos, incluido el inicial; no se promete orden entre fechas idénticas
     * @throws RecursoNoEncontradoException si no existe el envío
     */
    List<HistorialEstadoEnvioResponse> obtenerHistorial(UUID id);
}
