-- Migración aditiva: ejecutar después de 001-schema.sql, sin borrar usuarios.
-- En volúmenes existentes aplicar explícitamente antes de arrancar la nueva imagen.
CREATE TABLE IF NOT EXISTS refresh_sessions (
    id BINARY(16) NOT NULL,
    usuario_id BINARY(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    INDEX ix_refresh_expiry (expires_at),
    CONSTRAINT fk_refresh_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS consumed_refresh_tokens (
    token_hash VARCHAR(64) NOT NULL,
    session_id BINARY(16) NOT NULL,
    PRIMARY KEY (token_hash),
    INDEX ix_consumed_session (session_id),
    CONSTRAINT fk_consumed_session FOREIGN KEY (session_id) REFERENCES refresh_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Mantenimiento, con retención decidida por operación: borrar familias expiradas
-- mediante su índice; ON DELETE CASCADE retira su historial de hashes consumidos.
