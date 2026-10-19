package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.repository.ClienteRepository;
import com.logitrack_atom.shipment_service.repository.EnvioRepository;
import com.logitrack_atom.shipment_service.repository.HistorialEstadoEnvioRepository;
import com.logitrack_atom.shipment_service.repository.RepartidorRepository;
import com.logitrack_atom.shipment_service.service.RepartidorService;
import com.logitrack_atom.shipment_service.service.ShipmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** MySQL compartida durante el fork de Failsafe; Ryuk la elimina al terminar. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class MySqlIntegrationSupport {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("shipment_test")
            .withUsername("shipment_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("serverTimezone", "UTC")
            .withCopyFileToContainer(MountableFile.forHostPath(
                    Path.of("docker/mysql/init/001-schema.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/001-schema.sql");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configurarMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected EntityManager entityManager;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected ShipmentService shipmentService;
    @Autowired protected RepartidorService repartidorService;
    @Autowired protected ClienteRepository clientes;
    @Autowired protected EnvioRepository envios;
    @Autowired protected RepartidorRepository repartidores;
    @Autowired protected HistorialEstadoEnvioRepository historiales;

    @BeforeEach
    void limpiarBaseDePrueba() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("shipment_test");
        jdbc.execute("DROP TRIGGER IF EXISTS fallo_historial_prueba");
        jdbc.update("DELETE FROM historial_estados_envio");
        jdbc.update("DELETE FROM envios");
        jdbc.update("DELETE FROM repartidores");
        jdbc.update("DELETE FROM clientes");
    }

    /** Sin @Transactional en las pruebas: cada llamada al servicio debe hacer su commit real. */
    protected <T> T nuevaTransaccion(Supplier<T> trabajo) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> trabajo.get());
    }

    protected void comprobarBaseVacia() {
        assertThat(jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM clientes) + (SELECT COUNT(*) FROM envios)
                     + (SELECT COUNT(*) FROM repartidores) + (SELECT COUNT(*) FROM historial_estados_envio)
                """, Long.class)).isZero();
    }

    /** DDL de inyección de fallos dentro del contenedor; la aplicación sigue usando su usuario normal. */
    protected void ejecutarDdlComoAdministrador(String ddl) throws IOException, InterruptedException {
        var result = MYSQL.execInContainer("env", "MYSQL_PWD=" + MYSQL.getPassword(),
                "mysql", "-uroot", MYSQL.getDatabaseName(), "-e", ddl);
        assertThat(result.getExitCode()).as("DDL de prueba: %s", result.getStderr()).isZero();
    }

    protected static SQLException causaSql(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql;
        }
        throw new AssertionError("Se esperaba un fallo real de MySQL", error);
    }
}
