-- Esquema propietario de courier-service para MySQL 8.4.
-- Base vacía; UUID BINARY(16) y auditoría DATETIME(6) compatibles con Sprint01.
-- usuario_id es opcional y único; no hay FK a security ni a shipment.
CREATE TABLE repartidores (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    tipo_vehiculo VARCHAR(255) NOT NULL,
    estado ENUM('ACTIVO', 'INACTIVO') NOT NULL,
    usuario_id BINARY(16),
    PRIMARY KEY (id),
    CONSTRAINT uk_repartidores_usuario UNIQUE (usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
