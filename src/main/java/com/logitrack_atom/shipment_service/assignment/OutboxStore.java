package com.logitrack_atom.shipment_service.assignment;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Reclama mensajes con lease en transacciones cortas, independientes del envío AMQP. */
@Repository
public class OutboxStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    /**
     * Recibe acceso SQL y transacciones locales.
     * @param jdbc conexión SQL local
     * @param manager gestor de transacciones compartido
     */
    public OutboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    /** Comprueba el esquema requerido antes de activar la tarea publicadora. */
    public void ensureSchemaReady() {
        jdbc.queryForList("SELECT message_id,payload,lease_token,lease_until FROM amqp_outbox LIMIT 0");
        jdbc.queryForList("SELECT assignment_version,active_assignment_operation_id FROM envios LIMIT 0");
        jdbc.queryForList("SELECT id,idempotency_key,command_message_id,status FROM assignment_operations LIMIT 0");
    }

    /**
     * Reclama un pendiente con lease independiente del broker.
     * @return un mensaje reclamado por 30 s, o vacío si no hay pendientes disponibles
     */
    public Optional<Pending> claim() {
        return transactions.execute(status -> {
            Instant now = Instant.now();
            var rows = jdbc.query("""
                    SELECT message_id,operation_id,routing_key,payload,attempts FROM amqp_outbox
                    WHERE published_at IS NULL AND next_attempt_at<=? AND (lease_until IS NULL OR lease_until<=?)
                    ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (r, n) -> new Pending(AssignmentStore.uuid(r.getBytes(1)), AssignmentStore.uuid(r.getBytes(2)),
                    r.getString(3), r.getString(4), UUID.randomUUID(), r.getInt(5) + 1), Timestamp.from(now), Timestamp.from(now));
            if (rows.isEmpty()) return Optional.empty();
            Pending row = rows.getFirst();
            long backoff = Math.min(300, 1L << Math.min(row.attempts(), 8));
            jdbc.update("UPDATE amqp_outbox SET lease_token=?,lease_until=?,attempts=?,next_attempt_at=? WHERE message_id=?",
                    AssignmentStore.bytes(row.leaseToken()), Timestamp.from(now.plusSeconds(30)), row.attempts(),
                    Timestamp.from(now.plusSeconds(backoff)), AssignmentStore.bytes(row.messageId()));
            return Optional.of(row);
        });
    }

    /**
     * Marca una publicación confirmada si conserva su lease.
     * @param row mensaje confirmado; un publicador con lease viejo no confirma el lease nuevo
     */
    public void confirmed(Pending row) {
        jdbc.update("UPDATE amqp_outbox SET published_at=?,lease_token=NULL,lease_until=NULL WHERE message_id=? AND lease_token=? AND published_at IS NULL",
                Timestamp.from(Instant.now()), AssignmentStore.bytes(row.messageId()), AssignmentStore.bytes(row.leaseToken()));
    }

    /**
     * Libera el lease de un intento no confirmado.
     * @param row mensaje no confirmado, conservado para reintentar después del backoff
     */
    public void failed(Pending row) {
        jdbc.update("UPDATE amqp_outbox SET lease_token=NULL,lease_until=NULL WHERE message_id=? AND lease_token=? AND published_at IS NULL",
                AssignmentStore.bytes(row.messageId()), AssignmentStore.bytes(row.leaseToken()));
    }

    /**
     * Mensaje persistido reclamado por un único intento local de publicación.
     * @param messageId identidad estable ante reenvíos
     * @param operationId correlación de negocio
     * @param routingKey ruta del contrato
     * @param payload JSON sin JWT ni datos personales
     * @param leaseToken identidad de esta reclamación
     * @param attempts cantidad de intentos iniciados
     */
    public record Pending(UUID messageId, UUID operationId, String routingKey, String payload, UUID leaseToken, int attempts) { }
}
