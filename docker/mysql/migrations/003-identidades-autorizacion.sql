-- Ejecutar explícitamente tras respaldo y 002-separar-repartidores.sql.
-- No adjudica clientes por email ni inventa asociaciones de repartidores.
-- DDL MySQL con commit implícito; no ejecutar automáticamente al iniciar.
ALTER TABLE clientes ADD COLUMN usuario_id BINARY(16) NULL,
    ADD CONSTRAINT uk_clientes_usuario UNIQUE (usuario_id);
CREATE TABLE repartidor_usuarios (
    repartidor_id BINARY(16) NOT NULL PRIMARY KEY,
    usuario_id BINARY(16) NOT NULL UNIQUE,
    verificado_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
