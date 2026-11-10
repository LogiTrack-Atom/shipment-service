package com.logitrack_atom.shipment_service.assignment;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persiste operación y outbox usando la misma conexión/transacción local de Shipment. */
@Repository
public class AssignmentStore {
    private final JdbcTemplate jdbc;

    /**
     * Recibe acceso SQL de la aplicación.
     * @param jdbc acceso SQL unido al gestor de transacciones de la aplicación
     */
    public AssignmentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Consulta un replay por actor/clave; no autoriza ni reclama filas de otro actor.
     * @param actor UUID del sub autenticado
     * @param key clave canónica de idempotencia
     * @return operación existente de ese actor y clave
     */
    public Optional<AssignmentOperation> replay(UUID actor, UUID key) {
        return jdbc.query("SELECT * FROM assignment_operations WHERE actor_id=? AND idempotency_key=?",
                this::map, bytes(actor), bytes(key)).stream().findFirst();
    }

    /**
     * Consulta una operación persistida.
     * @param id operación buscada
     * @return operación local, sin acceso a AMQP
     */
    public Optional<AssignmentOperation> find(UUID id) {
        return jdbc.query("SELECT * FROM assignment_operations WHERE id=?", this::map, bytes(id)).stream().findFirst();
    }

    /**
     * Toma el mismo bloqueo de fila que las rutas síncronas antes de cambiar la generación.
     * @param id envío que se modificará
     * @return guardas actuales o vacío si no existe
     */
    public Optional<Guard> lock(UUID id) {
        return jdbc.query("SELECT estado,assignment_version,active_assignment_operation_id FROM envios WHERE id=? FOR UPDATE",
                (r, n) -> new Guard(r.getString(1), r.getLong(2), uuid(r.getBytes(3))), bytes(id)).stream().findFirst();
    }

    /**
     * Guarda nueva generación, operación y comando atómicamente; exige transacción activa.
     * @param operation operación recién aceptada
     * @param actor identidad autorizada
     * @param key clave de replay
     * @param commandId identidad estable del mensaje
     * @param previous operación anteriormente vigente, si existía
     * @param payload comando JSON ya serializado sin credenciales
     */
    public void insert(AssignmentOperation operation, UUID actor, UUID key, UUID commandId, UUID previous, String payload) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Operación/outbox requieren transacción local");
        Instant now = operation.createdAt();
        if (previous != null)
            jdbc.update("UPDATE assignment_operations SET status='SUPERSEDED',reason='REPLACED',updated_at=? WHERE id=? AND status='PENDING'",
                    Timestamp.from(now), bytes(previous));
        jdbc.update("UPDATE envios SET assignment_version=?,active_assignment_operation_id=? WHERE id=?",
                operation.assignmentVersion(), bytes(operation.operationId()), bytes(operation.envioId()));
        jdbc.update("""
                INSERT INTO assignment_operations
                (id,envio_id,actor_id,idempotency_key,repartidor_id,assignment_version,command_message_id,status,reason,created_at,updated_at,expires_at)
                VALUES (?,?,?,?,?,?,?,'PENDING',NULL,?,?,?)
                """, bytes(operation.operationId()), bytes(operation.envioId()), bytes(actor), bytes(key), bytes(operation.repartidorId()),
                operation.assignmentVersion(), bytes(commandId), Timestamp.from(now), Timestamp.from(now), Timestamp.from(operation.expiresAt()));
        jdbc.update("""
                INSERT INTO amqp_outbox(message_id,operation_id,routing_key,payload,created_at,next_attempt_at)
                VALUES (?,?,'availability.request',?,?,?)
                """, bytes(commandId), bytes(operation.operationId()), payload, Timestamp.from(now), Timestamp.from(now));
    }

    private AssignmentOperation map(ResultSet r, int row) throws SQLException {
        return new AssignmentOperation(uuid(r.getBytes("id")), uuid(r.getBytes("envio_id")), uuid(r.getBytes("repartidor_id")),
                r.getLong("assignment_version"), r.getString("status"), r.getString("reason"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(), r.getTimestamp("expires_at").toInstant());
    }

    /**
     * Convierte la identidad a BINARY(16).
     * @param value UUID local
     * @return representación BINARY(16), o null
     */
    static byte[] bytes(UUID value) {
        return value == null ? null : ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
    }

    /**
     * Reconstruye una identidad binaria nullable.
     * @param value columna binaria nullable
     * @return UUID reconstruido
     */
    static UUID uuid(byte[] value) {
        if (value == null) return null;
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Guardas protegidas por bloqueo de envío durante la aceptación.
     * @param state estado actual del envío
     * @param version generación monotónica local
     * @param active operación pendiente anteriormente vigente
     */
    public record Guard(String state, long version, UUID active) { }
}
