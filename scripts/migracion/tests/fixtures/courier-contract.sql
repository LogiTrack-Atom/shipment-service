-- CONTRATO DE ENSAYO: no es el esquema entregado por courier-service#1.
-- Se reemplaza/verifica contra ese proveedor antes del cierre de shipment-service#22.
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
