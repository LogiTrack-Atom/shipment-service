package com.logitrack_atom.shipment_service.assignment;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Comparte invalidación con PUT/transiciones en la misma transacción y orden de bloqueos. */
@Component
public class AssignmentCoordinator {
    private final AssignmentStore store;
    private final AssignmentSchema schema;
    private final Clock clock;

    /**
     * Recibe persistencia y reloj local sin conectar a AMQP.
     * @param store operaciones bajo bloqueo
     * @param schema detección de migración
     * @param clock reloj UTC
     */
    public AssignmentCoordinator(AssignmentStore store, AssignmentSchema schema, Clock clock) {
        this.store = store;this.schema = schema;this.clock = clock;
    }

    /**
     * Invalida pendientes incluso con el módulo MQ deshabilitado si el esquema ya existe.
     * @param envio envío que el servicio síncrono ya bloqueó y validó
     * @param reason REPLACED o STATE_CHANGED según el comando aceptado
     */
    public void invalidate(UUID envio, String reason) {
        if (!schema.installed()) return;
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La invalidación debe compartir el commit de dominio");
        var guard = store.lock(envio).orElseThrow();
        if (guard.active() != null) {
            var previous = store.stored(guard.active(), true);
            if (previous.isPresent()) store.finish(guard.active(), "SUPERSEDED", reason, clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
        store.invalidateGeneration(envio);
    }
}
