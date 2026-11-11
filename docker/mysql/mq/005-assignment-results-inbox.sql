-- Aplicar una vez después de 004 en Shipment; ensayar sobre copia/backup y registrar checksum.
-- Solo agrega inbox local. DDL MySQL implica commits; no reejecutar a ciegas tras interrupción.
CREATE TABLE assignment_inbox (
    message_id BINARY(16) NOT NULL PRIMARY KEY,
    operation_id BINARY(16) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    KEY idx_assignment_inbox_operation (operation_id),
    CONSTRAINT fk_assignment_inbox_operation FOREIGN KEY (operation_id) REFERENCES assignment_operations(id)
) ENGINE=InnoDB;
