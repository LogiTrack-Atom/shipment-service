-- Ampliación MQ: aplicar una vez sobre Shipment con backup/copia comprobada.
-- DDL MySQL implica commits: ante interrupción, inspeccionar el estado antes de reanudar.
-- No usar el ledger de repartidores (pertenece exclusivamente a Courier).
ALTER TABLE envios
    ADD COLUMN assignment_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN active_assignment_operation_id BINARY(16) NULL;

CREATE TABLE assignment_operations (
    id BINARY(16) NOT NULL PRIMARY KEY,
    envio_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    repartidor_id BINARY(16) NOT NULL,
    assignment_version BIGINT NOT NULL,
    command_message_id BINARY(16) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_assignment_actor_key (actor_id, idempotency_key),
    UNIQUE KEY uk_assignment_command (command_message_id),
    KEY idx_assignment_expiry (status, expires_at),
    CONSTRAINT fk_assignment_envio FOREIGN KEY (envio_id) REFERENCES envios(id),
    CONSTRAINT chk_assignment_version CHECK (assignment_version BETWEEN 1 AND 9007199254740991),
    CONSTRAINT chk_assignment_status CHECK (status IN ('PENDING','APPLIED','REJECTED','SUPERSEDED','EXPIRED'))
) ENGINE=InnoDB;

CREATE TABLE amqp_outbox (
    message_id BINARY(16) NOT NULL PRIMARY KEY,
    operation_id BINARY(16) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token BINARY(16) NULL,
    lease_until DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    KEY idx_amqp_outbox_pending (published_at, next_attempt_at, lease_until)
) ENGINE=InnoDB;
