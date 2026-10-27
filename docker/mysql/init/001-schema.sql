-- Esquema inicial de shipment-service para MySQL 8.4.
-- La imagen MySQL selecciona MYSQL_DATABASE antes de ejecutar este archivo.
-- Ejecutar sobre una base vacía: no reemplaza ni modifica tablas existentes.
-- UUID y auditoría los genera la aplicación; no se agregan defaults en la BD.

CREATE TABLE clientes (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    telefono VARCHAR(255),
    usuario_id BINARY(16),
    CONSTRAINT uk_clientes_usuario UNIQUE (usuario_id),
    PRIMARY KEY (id),
    CONSTRAINT uk_clientes_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE envios (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    codigo_seguimiento VARCHAR(255) NOT NULL,
    cliente_id BINARY(16) NOT NULL,
    repartidor_id BINARY(16),
    peso_kg DECIMAL(10,3) NOT NULL,
    ancho_cm DECIMAL(10,2) NOT NULL,
    alto_cm DECIMAL(10,2) NOT NULL,
    largo_cm DECIMAL(10,2) NOT NULL,
    estado ENUM('CANCELADO', 'ENTREGADO', 'EN_RUTA', 'PENDIENTE') NOT NULL,
    origen_calle VARCHAR(255) NOT NULL,
    origen_numero VARCHAR(255) NOT NULL,
    origen_ciudad VARCHAR(255) NOT NULL,
    origen_codigo_postal VARCHAR(255) NOT NULL,
    destino_calle VARCHAR(255) NOT NULL,
    destino_numero VARCHAR(255) NOT NULL,
    destino_ciudad VARCHAR(255) NOT NULL,
    destino_codigo_postal VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_envios_codigo_seguimiento UNIQUE (codigo_seguimiento),
    KEY idx_envios_cliente (cliente_id),
    KEY idx_envios_repartidor (repartidor_id),
    CONSTRAINT fk_envios_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE historial_estados_envio (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    envio_id BINARY(16) NOT NULL,
    estado_anterior ENUM('CANCELADO', 'ENTREGADO', 'EN_RUTA', 'PENDIENTE'),
    estado_nuevo ENUM('CANCELADO', 'ENTREGADO', 'EN_RUTA', 'PENDIENTE') NOT NULL,
    fecha_cambio DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_historial_envio (envio_id),
    CONSTRAINT fk_historial_envio FOREIGN KEY (envio_id) REFERENCES envios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE repartidor_usuarios (
    repartidor_id BINARY(16) NOT NULL PRIMARY KEY,
    usuario_id BINARY(16) NOT NULL UNIQUE,
    verificado_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
