package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Rechaza las mutaciones que requieren courier hasta integrar el transporte REST.
 *
 * <p>Adaptador transitorio de la extracción del dominio (#23). La integración
 * REST (#24) debe reemplazar esta clase. No consulta la antigua tabla ni asume
 * disponibilidad; las lecturas de envíos siguen siendo independientes.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class CourierPendienteClient implements CourierClient {
    /** {@inheritDoc} */
    @Override
    public void verificarDisponible(UUID repartidorId) {
        throw new CourierNoDisponibleException();
    }
}
