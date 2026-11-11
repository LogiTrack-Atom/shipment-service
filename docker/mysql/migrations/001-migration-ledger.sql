-- Ejecutar únicamente en la copia descartable de courier para el ensayo offline.
-- No crea ni altera tablas de dominio; no ejecutar sobre shipment ni datos del equipo.
-- Filas de dominio y registro de inserciones se confirman/revierten juntos en InnoDB.
CREATE TABLE logitrack_migracion_repartidores (
    lote CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    repartidor_id BINARY(16) NOT NULL,
    PRIMARY KEY (lote, repartidor_id),
    UNIQUE KEY uk_migracion_repartidor (repartidor_id)
) ENGINE=InnoDB;
