package com.logitrack_atom.shipment_service.integration;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/** Migra una copia MySQL independiente con datos existentes; no modifica bases del equipo. */
class AssignmentMigrationIT {
    @Test
    void additiveMigrationPreservesDataAndRepeatedApplicationFailsClosed() throws Exception {
        try (var database = new MySQLContainer(org.testcontainers.utility.DockerImageName.parse("mysql:8.4.11@sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364").asCompatibleSubstituteFor("mysql"))
                .withDatabaseName("shipment_migration_copy").withUsername("migration_test")
                .withPassword(UUID.randomUUID().toString()).withUrlParam("serverTimezone", "UTC")
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/mysql/init/001-schema.sql").toAbsolutePath()),
                        "/docker-entrypoint-initdb.d/001-schema.sql")) {
            database.start();
            var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    INSERT INTO clientes(id,created_at,updated_at,nombre,email)
                    VALUES (UNHEX('11111111111141118111111111111111'),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'Fixture','migration@example.test')
                    """);
            jdbc.execute("""
                    INSERT INTO envios(id,created_at,updated_at,codigo_seguimiento,cliente_id,peso_kg,ancho_cm,alto_cm,largo_cm,estado,
                    origen_calle,origen_numero,origen_ciudad,origen_codigo_postal,destino_calle,destino_numero,destino_ciudad,destino_codigo_postal)
                    VALUES (UNHEX('22222222222242228222222222222222'),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'LT-MIGRATION',
                    UNHEX('11111111111141118111111111111111'),1,1,1,1,'PENDIENTE','A','1','C','1','D','2','E','2')
                    """);
            var before = jdbc.queryForMap("SELECT HEX(id),created_at,updated_at,codigo_seguimiento,HEX(cliente_id),peso_kg,estado FROM envios");
            var script = new FileSystemResource("docker/mysql/mq/004-assignment-outbox.sql");
            try (var connection = source.getConnection()) { ScriptUtils.executeSqlScript(connection, script); }
            assertThat(jdbc.queryForMap("SELECT HEX(id),created_at,updated_at,codigo_seguimiento,HEX(cliente_id),peso_kg,estado FROM envios")).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT assignment_version FROM envios", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT active_assignment_operation_id FROM envios", byte[].class)).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assignment_operations", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM amqp_outbox", Integer.class)).isZero();
            try (var connection = source.getConnection()) {
                assertThatThrownBy(() -> ScriptUtils.executeSqlScript(connection, script))
                        .isInstanceOf(org.springframework.jdbc.datasource.init.ScriptStatementFailedException.class);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios", Integer.class)).isEqualTo(1);
        }
    }
}
