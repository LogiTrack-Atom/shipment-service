package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.exception.ReglaNegocioException;
import java.util.UUID;

/**
 * Frontera de consulta del servicio responsable de los repartidores.
 *
 * <p>Verifica la disponibilidad observada sin reservar al repartidor ni escribir
 * en courier. Una falla impide la mutación local; nunca implica aprobación.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
public interface CourierClient {
    /**
     * Exige que el repartidor exista y esté disponible en courier.
     * @param repartidorId UUID del repartidor
     * @throws RecursoNoEncontradoException si no existe
     * @throws ReglaNegocioException si no está disponible
     * @throws CourierNoDisponibleException si no se puede comprobar
     */
    void verificarDisponible(UUID repartidorId);
}
