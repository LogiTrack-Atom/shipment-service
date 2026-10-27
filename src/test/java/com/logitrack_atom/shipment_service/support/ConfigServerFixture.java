package com.logitrack_atom.shipment_service.support;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import java.nio.file.Path;
import java.util.Map;

/**
 * Arranca un Config Server real exclusivamente para pruebas de consumo remoto.
 *
 * <p>Usa un repositorio Git temporal provisto por la prueba; no reemplaza la
 * aplicación configserver del equipo ni se incluye en el JAR de producción.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
@EnableConfigServer
public class ConfigServerFixture {
    /** Crea la configuración del servidor de prueba. */
    public ConfigServerFixture() { }

    /**
     * Expone únicamente en el fixture la API de lectura de configuraciones sin login.
     * @param http constructor de filtros de prueba
     * @return cadena sin cuentas ni contraseñas generadas automáticamente
     * @throws Exception si no puede construirse la cadena
     */
    @Bean
    public SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(access -> access.anyRequest().permitAll()).build();
    }

    /**
     * Inicia un servidor con backend Git local y puerto efímero.
     * @param repository repositorio Git temporal con rama develop
     * @param cloneDirectory directorio temporal para la copia de trabajo de JGit
     * @return contexto que el llamador debe cerrar al terminar
     */
    public static ConfigurableApplicationContext start(Path repository, Path cloneDirectory) {
        return application().properties(Map.of("DB_PASSWORD", "valor-del-servidor-que-no-debe-publicarse"))
                .run("--server.port=0", "--server.address=127.0.0.1",
                        "--spring.cloud.config.server.git.uri=" + repository.toUri(),
                        "--spring.cloud.config.server.git.basedir=" + cloneDirectory,
                        "--spring.cloud.config.server.git.default-label=develop",
                        "--spring.cloud.config.server.git.clone-on-start=true");
    }

    /**
     * Permite ejecutar el fixture en una JVM separada para verificar la imagen Docker.
     * @param args parámetros del servidor temporal, sin secretos reales
     */
    public static void main(String[] args) { application().run(args); }

    /**
     * Aísla la configuración del servidor respecto de application.yaml del cliente.
     * @return constructor con el cliente remoto desactivado únicamente en el fixture
     */
    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(ConfigServerFixture.class).properties(Map.of(
                "spring.config.name", "configserver-fixture",
                "spring.application.name", "configserver-fixture",
                "spring.cloud.config.enabled", false,
                "spring.main.banner-mode", "off",
                "management.endpoints.web.exposure.include", "health",
                "management.endpoint.health.show-details", "never"));
    }
}
