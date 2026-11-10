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

    /**
     * Obtiene referencias inmutables de una operación antes de bloquear el envío.
     * @param id operación local
     * @param lock toma bloqueo de operación solo después del bloqueo de envío
     * @return registro con el comando causal original
     */
    public Optional<StoredOperation> stored(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM assignment_operations WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (r, n) -> new StoredOperation(map(r, n), uuid(r.getBytes("command_message_id"))), bytes(id)).stream().findFirst();
    }

    /**
     * Consulta deduplicación de un resultado ya confirmado.
     * @param id identidad de mensaje de resultado
     * @return huella original, si existe
     */
    public Optional<byte[]> inboxHash(UUID id) {
        return jdbc.query("SELECT payload_hash FROM assignment_inbox WHERE message_id=?", (r, n) -> r.getBytes(1), bytes(id)).stream().findFirst();
    }

    /**
     * Guarda la identidad procesada dentro del commit de aplicación del resultado.
     * @param result resultado validado
     * @param hash huella de los bytes recibidos
     * @param now instante UTC del procesamiento
     */
    public void recordResult(AvailabilityResult result, byte[] hash, Instant now) {
        jdbc.update("INSERT INTO assignment_inbox(message_id,operation_id,payload_hash,processed_at) VALUES (?,?,?,?)",
                bytes(result.messageId()), bytes(result.operationId()), hash, Timestamp.from(now));
    }

    /**
     * Cambia solo el estado de la operación protegida por el bloqueo de envío/operación.
     * @param id operación local
     * @param status estado terminal
     * @param reason motivo seguro nullable
     * @param now instante UTC
     */
    public void finish(UUID id, String status, String reason, Instant now) {
        jdbc.update("UPDATE assignment_operations SET status=?,reason=?,updated_at=? WHERE id=? AND status='PENDING'",
                status, reason, Timestamp.from(now), bytes(id));
    }

    /**
     * Limpia el puntero solo si todavía corresponde a la operación procesada.
     * @param envio agregado local
     * @param operation operación propietaria del puntero
     */
    public void clearActive(UUID envio, UUID operation) {
        jdbc.update("UPDATE envios SET active_assignment_operation_id=NULL WHERE id=? AND active_assignment_operation_id=?", bytes(envio), bytes(operation));
    }

    /**
     * Asigna sin transición de estado ni evento de historial, después de validar todas las guardas.
     * @param result resultado AVAILABLE vigente
     * @param now auditoría de la escritura de dominio
     */
    public void apply(AvailabilityResult result, Instant now) {
        int changed = jdbc.update("UPDATE envios SET repartidor_id=?,updated_at=?,active_assignment_operation_id=NULL WHERE id=? AND active_assignment_operation_id=?",
                bytes(result.repartidorId()), Timestamp.from(now), bytes(result.envioId()), bytes(result.operationId()));
        if (changed != 1) throw new IllegalStateException("La operación perdió vigencia durante la transacción");
    }

    /**
     * Incrementa generación para un comando síncrono que ya validó sus precondiciones.
     * @param envio agregado bajo bloqueo local
     */
    public void invalidateGeneration(UUID envio) {
        jdbc.update("UPDATE envios SET assignment_version=assignment_version+1,active_assignment_operation_id=NULL WHERE id=?", bytes(envio));
    }

    /**
     * Selecciona un lote de vencimientos sin bloquear otros agregados mientras se procesa cada uno.
     * @param now instante UTC de selección
     * @return hasta cincuenta operaciones pendientes vencidas
     */
    public java.util.List<UUID> due(Instant now) {
        return jdbc.query("SELECT id FROM assignment_operations WHERE status='PENDING' AND expires_at<=? ORDER BY expires_at LIMIT 50",
                (r, n) -> uuid(r.getBytes(1)), Timestamp.from(now));
    }

    /** Comprueba el inbox de resultados antes de habilitar su consumidor. */
    public void requireResultSchema() {
        jdbc.queryForList("SELECT message_id,operation_id,payload_hash,processed_at FROM assignment_inbox LIMIT 0");
        jdbc.queryForList("SELECT assignment_version,active_assignment_operation_id FROM envios LIMIT 0");
    }

    /**
     * Referencias de operación que nunca se toman como autoridad de autorización del usuario.
     * @param operation estado público persistido
     * @param commandId identidad del comando que Courier debe citar
     */
    public record StoredOperation(AssignmentOperation operation, UUID commandId) { }

}
