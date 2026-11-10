package com.logitrack_atom.shipment_service.assignment;

import com.logitrack_atom.shipment_service.security.ShipmentAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Acepta intenciones autorizadas sin efectuar HTTP a Courier ni esperar al broker. */
@Service
public class AssignmentService {
    private final AssignmentStore store;
    private final ShipmentAccess access;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final AssignmentResultsService results;

    /**
     * Recibe persistencia, identidad y transacciones de aceptación.
     * @param store persistencia local
     * @param access identidad del JWT validado
     * @param mapper serializador JSON de la aplicación
     * @param manager gestor compartido por JPA y JDBC
     * @param results vencimiento local antes de exponer el estado
     * @param enabled habilitación explícita de la API de ampliación
     */
    public AssignmentService(AssignmentStore store, ShipmentAccess access, ObjectMapper mapper,
            PlatformTransactionManager manager, AssignmentResultsService results, @Value("${logitrack.assignment.enabled:false}") boolean enabled) {
        this.store = store;
        this.access = access;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.enabled = enabled;
        this.results = results;
    }

    /**
     * Confirma operación/outbox antes de devolver el 202; una carrera de clave se reconcilia tras rollback.
     * @param envio UUID de envío
     * @param repartidor UUID remoto solicitado
     * @param rawKey Idempotency-Key canónica
     * @return operación persistida o replay de la misma intención
     */
    @PreAuthorize("hasRole('ADMIN')")
    public AssignmentOperation accept(UUID envio, UUID repartidor, String rawKey) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Asignación asíncrona deshabilitada");
        UUID key;
        try {
            key = UUID.fromString(rawKey);
            if (!key.toString().equals(rawKey)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key debe ser un UUID canónico en minúsculas");
        }
        UUID actor = access.usuarioId();
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                var replay = store.replay(actor, key);
                if (replay.isPresent()) return sameIntention(replay.get(), envio, repartidor);
                var guard = store.lock(envio).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Envío no encontrado"));
                replay = store.replay(actor, key);
                if (replay.isPresent()) return sameIntention(replay.get(), envio, repartidor);
                if (!"PENDIENTE".equals(guard.state()))
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "El envío debe estar PENDIENTE");
                if (guard.version() >= 9007199254740991L)
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Generación de asignación agotada");
                Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
                var operation = new AssignmentOperation(UUID.randomUUID(), envio, repartidor, guard.version() + 1,
                        "PENDING", null, now, now, now.plusSeconds(300));
                UUID commandId = UUID.randomUUID();
                var body = new LinkedHashMap<String, Object>();
                body.put("schemaVersion", 1);
                body.put("type", "assignment.availability.requested.v1");
                body.put("messageId", commandId);
                body.put("operationId", operation.operationId());
                body.put("envioId", envio);
                body.put("repartidorId", repartidor);
                body.put("assignmentVersion", operation.assignmentVersion());
                body.put("occurredAt", now.toString());
                body.put("expiresAt", operation.expiresAt().toString());
                store.insert(operation, actor, key, commandId, guard.active(), mapper.writeValueAsString(body));
                return operation;
            }));
        } catch (DuplicateKeyException race) {
            // La transacción fallida ya se revirtió; no consultar dentro de rollback-only.
            return Objects.requireNonNull(transactions.execute(status -> store.replay(actor, key)
                    .map(operation -> sameIntention(operation, envio, repartidor)).orElseThrow(() -> race)));
        }
    }

    private AssignmentOperation sameIntention(AssignmentOperation operation, UUID envio, UUID repartidor) {
        if (!operation.envioId().equals(envio) || !operation.repartidorId().equals(repartidor))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key ya usada para otra intención");
        return operation;
    }

    /**
     * Consulta una operación persistida.
     * @param id operación consultada
     * @return estado persistido sin consultar el broker
     */
    @PreAuthorize("hasRole('ADMIN')")
    public AssignmentOperation find(UUID id) {
        results.expire(id);
        return store.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operación no encontrada"));
    }
}
