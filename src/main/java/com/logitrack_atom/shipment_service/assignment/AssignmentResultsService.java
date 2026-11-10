package com.logitrack_atom.shipment_service.assignment;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Aplica resultados en un commit local breve, con inbox y guardas compartidas del agregado. */
@Service
public class AssignmentResultsService {
    private final AssignmentStore store;
    private final AssignmentSchema schema;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Construye transacciones propias sin autoridad JWT almacenada ni esperas AMQP.
     * @param store persistencia de operaciones/resultados
     * @param schema presencia del esquema aditivo
     * @param manager gestor local
     * @param clock reloj UTC para vigencia después de adquirir bloqueos
     */
    public AssignmentResultsService(AssignmentStore store, AssignmentSchema schema, PlatformTransactionManager manager, Clock clock) {
        this.store = store;this.schema = schema;this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Confirma inbox y efecto solo para la operación todavía vigente y no vencida.
     * @param result resultado validado del usuario de servicio Courier
     * @param hash SHA-256 del cuerpo inmutable
     */
    public void process(AvailabilityResult result, byte[] hash) {
        if (hash.length != 32) throw invalid();
        try {
            transactions.executeWithoutResult(status -> {
                var seen = store.inboxHash(result.messageId());
                if (seen.isPresent()) { sameHash(seen.get(), hash); return; }
                var initial = store.stored(result.operationId(), false).orElseThrow(this::invalid);
                var guard = store.lock(initial.operation().envioId()).orElseThrow(this::invalid);
                var stored = store.stored(result.operationId(), true).orElseThrow(this::invalid);
                var operation = stored.operation();
                if (!operation.envioId().equals(result.envioId()) || !operation.repartidorId().equals(result.repartidorId())
                        || operation.assignmentVersion() != result.assignmentVersion() || !stored.commandId().equals(result.causationId())
                        || !operation.expiresAt().equals(result.expiresAt())) throw invalid();
                Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                if (operation.status().equals("PENDING")) {
                    if (!now.isBefore(operation.expiresAt())) {
                        store.finish(operation.operationId(), "EXPIRED", "DEADLINE_EXCEEDED", now);
                        store.clearActive(operation.envioId(), operation.operationId());
                    } else if (!guard.state().equals("PENDIENTE") || guard.version() != operation.assignmentVersion()
                            || !Objects.equals(guard.active(), operation.operationId())) {
                        store.finish(operation.operationId(), "SUPERSEDED", guard.state().equals("PENDIENTE") ? "REPLACED" : "STATE_CHANGED", now);
                        store.clearActive(operation.envioId(), operation.operationId());
                    } else if (result.availability().equals("AVAILABLE")) {
                        store.apply(result, now);
                        store.finish(operation.operationId(), "APPLIED", null, now);
                    } else {
                        store.finish(operation.operationId(), "REJECTED", result.availability(), now);
                        store.clearActive(operation.envioId(), operation.operationId());
                    }
                }
                store.recordResult(result, hash, now);
            });
        } catch (DuplicateKeyException race) {
            transactions.executeWithoutResult(status -> sameHash(store.inboxHash(result.messageId()).orElseThrow(() -> race), hash));
        }
    }

    /**
     * Materializa expiración sin broker y con el mismo orden envío → operación.
     * @param id operación consultada o seleccionada por el finalizador
     */
    public void expire(UUID id) {
        if (!schema.installed()) return;
        transactions.executeWithoutResult(status -> {
            var initial = store.stored(id, false);
            if (initial.isEmpty()) return;
            store.lock(initial.get().operation().envioId()).orElseThrow();
            var current = store.stored(id, true).orElseThrow().operation();
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            if (current.status().equals("PENDING") && !now.isBefore(current.expiresAt())) {
                store.finish(id, "EXPIRED", "DEADLINE_EXCEEDED", now);
                store.clearActive(current.envioId(), id);
            }
        });
    }

    /** Procesa un lote acotado de vencimientos, sin mantener locks entre agregados. */
    public void expireDue() {
        if (schema.installed()) for (UUID id : store.due(clock.instant())) expire(id);
    }

    /** Verifica la migración 005 antes de iniciar el consumidor. */
    public void ensureSchemaReady() { store.requireResultSchema(); }

    private void sameHash(byte[] previous, byte[] hash) { if (!MessageDigest.isEqual(previous, hash)) throw invalid(); }
    private InvalidAssignmentResultException invalid() { return new InvalidAssignmentResultException(); }
}
