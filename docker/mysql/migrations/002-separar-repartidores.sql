-- Transición Sprint01 -> Sprint02, exclusivamente después de verificar copia,
-- UUID y referencias en courier, y disponer de respaldo/restauración ensayados.
-- Ejecución administrativa explícita; NO se monta en init ni corre al arrancar.
-- MySQL confirma DDL implícitamente: no ofrece rollback transaccional del ALTER.
-- No elimina repartidores: conservarla como respaldo sin acceso desde la aplicación.
-- No modifica filas, UUID BINARY(16), índices, auditoría ni las FK locales.
ALTER TABLE envios DROP FOREIGN KEY fk_envios_repartidor;
