package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.client.CourierClient;
import com.logitrack_atom.shipment_service.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Ensaya el ALTER del Sprint02 sobre datos sintéticos del esquema Sprint01.
 *
 * <p>La base temporal conserva la tabla de respaldo, se valida con JPA y permite
 * comprobar retiro/restauración de la FK sin cambiar UUID ni auditoría.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
class ShipmentSchemaMigrationIT {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("shipment_migrated_test")
            .withUsername("shipment_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("serverTimezone", "UTC")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of(
                    "scripts/migracion/tests/fixtures/shipment-sprint01.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/001-schema.sql")
            .withCopyFileToContainer(MountableFile.forClasspathResource("sql/sprint01-datos-sinteticos.sql"),
                    "/docker-entrypoint-initdb.d/002-seed.sql");
    private static final String[] TABLES = {"clientes", "repartidores", "envios", "historial_estados_envio"};
    private static final Map<String, String> BEFORE = new java.util.LinkedHashMap<>();
    static {
        MYSQL.start();
        try {
            for (String table : TABLES) BEFORE.put(table, sql("SELECT " + (table.equals("clientes") ? "id,created_at,updated_at,nombre,email,telefono" : "*") + " FROM " + table + " ORDER BY id"));
            sql(Files.readString(Path.of("docker/mysql/migrations/002-separar-repartidores.sql")));
            sql(Files.readString(Path.of("docker/mysql/migrations/003-identidades-autorizacion.sql")));
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        com.logitrack_atom.shipment_service.support.TestRsaKeys.registrar(registry);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private ShipmentService shipmentService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private CourierClient courierClient;

    @org.junit.jupiter.api.BeforeEach
    void actor() { com.logitrack_atom.shipment_service.support.SecurityTestActors.admin(); }
    @org.junit.jupiter.api.AfterEach
    void limpiarActor() { com.logitrack_atom.shipment_service.support.SecurityTestActors.clear(); }

    @Test
    void conservaDatosYLeeUuidNulosRepetidosSinConsultarCourierYPermiteRevertirFk() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='fk_envios_repartidor'
                """, Integer.class)).isZero();
        for (int id : new int[]{21, 22, 23, 24, 25}) {
            var envio = shipmentService.obtenerPorId(uuid(id));
            UUID esperado = id == 22 || id == 23 ? uuid(11) : id == 24 ? uuid(12) : null;
            assertThat(envio.repartidorId()).isEqualTo(esperado);
            assertThat(envio.codigoSeguimiento()).isEqualTo("LT-S01-" + id);
            assertThat(envio.createdAt().toString()).isEqualTo("2026-10-19T12:13:14.123456Z");
            assertThat(envio.updatedAt().toString()).isEqualTo("2026-10-20T13:14:15.654321Z");
            assertThat(shipmentService.obtenerPorCodigoSeguimiento(envio.codigoSeguimiento()).id()).isEqualTo(uuid(id));
            assertThat(shipmentService.obtenerHistorial(uuid(id))).hasSize(1);
        }
        assertThat(shipmentService.listarPorEstado(null)).hasSize(5);
        verifyNoInteractions(courierClient);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE usuario_id IS NOT NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repartidor_usuarios", Long.class)).isZero();
        comprobarFilas();
        // Solo se revierte en esta copia sintética; no hay escrituras nuevas ni UUID faltantes.
        sql("ALTER TABLE envios ADD CONSTRAINT fk_envios_repartidor FOREIGN KEY (repartidor_id) REFERENCES repartidores(id)");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='fk_envios_repartidor'
                """, Integer.class)).isOne();
        comprobarFilas();
        sql("ALTER TABLE envios DROP FOREIGN KEY fk_envios_repartidor");
    }

    private static UUID uuid(int value) { return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012x", value)); }
    private static void comprobarFilas() throws Exception {
        for (String table : TABLES) assertThat(sql("SELECT " + (table.equals("clientes") ? "id,created_at,updated_at,nombre,email,telefono" : "*") + " FROM " + table + " ORDER BY id")).as(table).isEqualTo(BEFORE.get(table));
    }
    private static String sql(String query) throws Exception {
        var result = MYSQL.execInContainer("env", "MYSQL_PWD=" + MYSQL.getPassword(), "mysql", "-uroot",
                "--batch", "--raw", "--binary-as-hex", MYSQL.getDatabaseName(), "-e", query);
        if (result.getExitCode() != 0) throw new AssertionError("Falló SQL del ensayo aislado");
        return result.getStdout();
    }
}
