package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.ShipmentServiceApplication;
import com.logitrack_atom.shipment_service.config.ConfigMarkerProperties;
import com.logitrack_atom.shipment_service.config.JwtProperties;
import com.logitrack_atom.shipment_service.config.SecurityClientProperties;
import com.logitrack_atom.shipment_service.config.CourierClientProperties;
import com.logitrack_atom.shipment_service.support.ConfigServerFixture;
import com.logitrack_atom.shipment_service.support.TestRsaKeys;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba consumo dev/prod con Config Server real, Git temporal y MySQL aislado.
 *
 * <p>Los YAML son copias verificadas de una revisión de configserver-ms. El cliente
 * recibe los secretos únicamente desde su entorno de prueba, sin escribirlos en Git.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
class RemoteConfigurationIT {
    @TempDir
    static Path temporary;
    private static ConfigurableApplicationContext server;
    private static String baseUrl;

    /** Verifica los snapshots, crea un Git descartable y arranca el servidor real. */
    @BeforeAll
    static void startServer() throws Exception {
        Path repository = Files.createDirectory(temporary.resolve("repository"));
        var manifest = JsonMapper.builder().build().readTree(
                new ClassPathResource("config-repository/source.json").getInputStream());
        for (String name : new String[]{"shipment-service.yml", "shipment-service-dev.yml", "shipment-service-prod.yml"}) {
            byte[] bytes;
            try (var input = new ClassPathResource("config-repository/" + name).getInputStream()) {
                bytes = input.readAllBytes();
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(hash).isEqualTo(manifest.path("files").path(name).asString());
            Files.write(repository.resolve(name), bytes);
        }
        try (Git git = Git.init().setDirectory(repository.toFile()).setInitialBranch("develop").call()) {
            git.add().addFilepattern(".").call();
            git.commit().setAuthor("Pruebas LogiTrack", "pruebas@example.com")
                    .setCommitter("Pruebas LogiTrack", "pruebas@example.com")
                    .setMessage("Configuración de prueba dev/prod").call();
        }
        server = ConfigServerFixture.start(repository, temporary.resolve("clone"));
        baseUrl = "http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port");
    }

    /** Cierra el contexto del servidor antes de eliminar sus directorios de prueba. */
    @AfterAll
    static void stopServer() { if (server != null) server.close(); }

    /** Ambos perfiles enlazan sus propiedades remotas y arrancan con verificación de compatibilidad. */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void consumesRemoteProperties(String profile) throws Exception {
        try (var client = client(profile, Map.of())) {
            var environment = client.getEnvironment();
            assertThat(client.getBean(ConfigMarkerProperties.class).marker()).isEqualTo("shipment-service-" + profile);
            assertThat(environment.getPropertySources().stream()
                    .anyMatch(source -> source.getName().contains("configserver:"))).isTrue();
            assertThat(environment.getProperty("spring.application.name")).isEqualTo("shipment-service");
            assertThat(environment.getProperty("server.port")).isEqualTo("0");
            assertThat(environment.getProperty("logging.level.com.logitrack_atom"))
                    .isEqualTo(profile.equals("dev") ? "DEBUG" : "INFO");
            assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isEqualTo(profile.equals("dev"));
            assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.jackson.deserialization.fail-on-unknown-properties", Boolean.class)).isTrue();
            assertThat(environment.getProperty("spring.jackson.datatype.enum.fail-on-numbers-for-enums", Boolean.class)).isTrue();
            assertThat(client.getBean(JwtProperties.class).accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(client.getBean(CourierClientProperties.class).connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(client.getBean(CourierClientProperties.class).readTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(client.getBean(SecurityClientProperties.class).connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(client.getBean(SecurityClientProperties.class).readTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(client.getBean("compositeCompatibilityVerifier").getClass().getName())
                    .isEqualTo("org.springframework.cloud.configuration.CompositeCompatibilityVerifier");
            assertThat(environment.getProperty("spring.cloud.compatibility-verifier.enabled", Boolean.class, true)).isTrue();
            try (var connection = client.getBean(DataSource.class).getConnection()) {
                assertThat(connection.getCatalog()).isEqualTo("shipment_test");
                assertThat(connection.getMetaData().getUserName()).startsWith("shipment_test");
            }
            String url = "http://127.0.0.1:" + environment.getProperty("local.server.port");
            assertThat(RestClient.create().get().uri(url + "/actuator/health").retrieve().body(String.class))
                    .contains("\"status\":\"UP\"").doesNotContain("components", "details", "password", "marker");
            org.springframework.http.HttpStatusCode denied = RestClient.create().get().uri(url + "/actuator/env")
                    .exchange((request, response) -> response.getStatusCode());
            assertThat(denied).isEqualTo(HttpStatus.UNAUTHORIZED);
            String token = TestRsaKeys.token(java.util.UUID.randomUUID(), "ADMIN");
            assertThat(RestClient.create().get().uri(url + "/api/v1/envios")
                    .headers(headers -> headers.setBearerAuth(token)).retrieve().body(String.class)).startsWith("[");
        }
    }

    /** Config Server devuelve placeholders aunque su proceso disponga de un valor homónimo. */
    @Test
    void serverDoesNotResolveClientSecrets() {
        String response = RestClient.create().get().uri(baseUrl + "/shipment-service/dev/develop")
                .retrieve().body(String.class);
        assertThat(response).contains("${DB_PASSWORD}", "${JWT_PUBLIC_KEY_LOCATION}")
                .doesNotContain("valor-del-servidor-que-no-debe-publicarse");
    }

    /** La clave pública externa es obligatoria también con configuración remota. */
    @Test
    void missingPublicKeyStopsStartup() {
        assertThatThrownBy(() -> client("dev", Map.of("JWT_PUBLIC_KEY_LOCATION",
                temporary.resolve("missing.pem").toUri().toString())))
                .hasStackTraceContaining("No se pudo cargar la clave pública RSA local");
    }

    /** No existe una contraseña alternativa en los YAML cuando falta la del cliente. */
    @Test
    void missingDatabasePasswordStopsStartup() {
        assertThatThrownBy(() -> client("prod", Map.of("DB_PASSWORD", "")))
                .hasStackTraceContaining("JDBC metadata");
    }

    /** Un servidor inaccesible no habilita valores locales en dev ni prod. */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod", "prod,local"})
    void unavailableServerStopsStartup(String profile) throws Exception {
        int unused;
        try (var socket = new java.net.ServerSocket(0)) { unused = socket.getLocalPort(); }
        assertThatThrownBy(() -> client(profile, Map.of("CONFIG_SERVER_URL", "http://127.0.0.1:" + unused)))
                .hasStackTraceContaining("Could not locate PropertySource");
    }

    /** Las variables externas resuelven placeholders remotos sin alterar el Git de configuración. */
    @Test
    void externalTimeoutOverridesRemoteDefault() throws Exception {
        try (var context = client("dev", Map.of("SECURITY_CONNECT_TIMEOUT", "350ms"))) {
            assertThat(context.getBean(SecurityClientProperties.class).connectTimeout()).isEqualTo(Duration.ofMillis(350));
            assertThat(context.getBean(ConfigMarkerProperties.class).marker()).isEqualTo("shipment-service-dev");
        }
    }

    /** Una etiqueta inexistente no habilita una configuración operativa alternativa. */
    @Test
    void missingLabelStopsStartup() {
        assertThatThrownBy(() -> client("prod", Map.of("CONFIG_LABEL", "etiqueta-inexistente")))
                .hasStackTraceContaining("Could not locate PropertySource");
    }

    private ConfigurableApplicationContext client(String profile, Map<String, Object> overrides) throws Exception {
        Map<String, Object> external = new HashMap<>(MySqlIntegrationSupport.databaseProperties());
        external.put("CONFIG_SERVER_URL", baseUrl);
        external.put("CONFIG_LABEL", "develop");
        external.put("SHIPMENT_SERVER_PORT", "0");
        external.put("JWT_PUBLIC_KEY_LOCATION", TestRsaKeys.publicResource().getURI().toString());
        external.putAll(overrides);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("fixture-client-environment", external));
        return new SpringApplicationBuilder(ShipmentServiceApplication.class).environment(environment)
                .run("--spring.profiles.active=" + profile, "--spring.main.banner-mode=off",
                        "--spring.cloud.config.enabled=true");
    }
}
