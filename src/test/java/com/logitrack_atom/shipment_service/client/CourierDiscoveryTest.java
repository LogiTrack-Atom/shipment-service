package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.config.CourierClientProperties;
import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.exception.CourierRespuestaInvalidaException;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.exception.ReglaNegocioException;
import com.logitrack_atom.shipment_service.security.CourierBearerProvider;
import com.logitrack_atom.shipment_service.support.CourierStub;
import com.logitrack_atom.shipment_service.support.SecurityTestActors;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba el proxy Spring Feign y LoadBalancer con descubrimiento HTTP controlado.
 *
 * <p>No usa Docker ni representa un registro Eureka real. Las pruebas con MySQL y JWT
 * firmados permanecen en CourierFeignIT; esta suite aísla transporte y selección de instancia.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@SpringBootTest(classes = CourierDiscoveryTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
            "spring.config.name=courier-discovery-test", "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false", "spring.main.banner-mode=off",
            "clients.courier.base-url=http://127.0.0.1:1",
            "clients.courier.connect-timeout=500ms", "clients.courier.read-timeout=500ms",
            "spring.cloud.openfeign.client.default-to-properties=false",
            "spring.cloud.openfeign.client.config.courierAvailability.follow-redirects=true",
            "spring.cloud.openfeign.client.config.courierAvailability.read-timeout=5000",
            "spring.cloud.loadbalancer.retry.enabled=false", "spring.cloud.loadbalancer.cache.enabled=false"
        })
class CourierDiscoveryTest {
    private static final AtomicReference<ServiceInstance> TARGET = new AtomicReference<>();
    @Autowired private CourierClient courier;

    @BeforeEach
    void prepare() {
        CourierStub.reset();
        select(CourierStub.baseUrl());
        SecurityTestActors.admin();
    }

    @AfterEach
    void clear() { SecurityTestActors.clear(); }

    @Test
    void consultaElNombreDescubiertoYReenviaSoloElBearerDelContexto() {
        UUID id = UUID.randomUUID();
        courier.verificarDisponible(id);
        assertThat(courier).isInstanceOf(FeignCourierClient.class);
        assertThat(CourierStub.requests()).isOne();
        assertThat(CourierStub.method()).isEqualTo("GET");
        assertThat(CourierStub.path()).isEqualTo("/api/v1/repartidores/" + id + "/disponibilidad");
        assertThat(CourierStub.authorization()).isEqualTo("Bearer fixture-directo-no-http");
    }

    @Test
    void cambiaLaInstanciaDescubiertaSinReconstruirElCliente() throws Exception {
        UUID id = UUID.randomUUID();
        courier.verificarDisponible(id);
        var received = new AtomicReference<String>();
        var second = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        second.createContext("/api/v1/repartidores/" + id + "/disponibilidad", exchange -> {
            received.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"repartidorId\":\"" + id + "\",\"estado\":\"ACTIVO\",\"disponible\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        second.start();
        try {
            select("http://127.0.0.1:" + second.getAddress().getPort());
            courier.verificarDisponible(id);
            assertThat(CourierStub.requests()).isOne();
            assertThat(received.get()).isEqualTo("Bearer fixture-directo-no-http");
        } finally { second.stop(0); }
    }

    @Test
    void sinInstanciaRechazaYSeRecuperaCuandoDescubrimientoPublicaUna() {
        TARGET.set(null);
        assertThatThrownBy(() -> courier.verificarDisponible(UUID.randomUUID()))
                .isInstanceOf(CourierNoDisponibleException.class).hasCause(null);
        assertThat(CourierStub.requests()).isZero();
        select(CourierStub.baseUrl());
        courier.verificarDisponible(UUID.randomUUID());
        assertThat(CourierStub.requests()).isOne();
    }

    @Test
    void sinContextoAutenticadoNoEnviaLaConsulta() {
        SecurityTestActors.clear();
        assertThatThrownBy(() -> courier.verificarDisponible(UUID.randomUUID()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(CourierStub.requests()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"404,404", "401,502", "429,503", "500,503", "503,503", "302,502", "204,502"})
    void conservaErroresPublicosSinReintentosNiRedirecciones(int remote, int expected) {
        CourierStub.responder(remote, "contenido-privado-del-proveedor", "application/json", 0);
        Class<? extends RuntimeException> error = switch (expected) {
            case 404 -> RecursoNoEncontradoException.class;
            case 503 -> CourierNoDisponibleException.class;
            default -> CourierRespuestaInvalidaException.class;
        };
        assertThatThrownBy(() -> courier.verificarDisponible(UUID.randomUUID()))
                .isInstanceOf(error).hasCause(null)
                .hasMessageNotContaining("contenido-privado-del-proveedor")
                .hasMessageNotContaining("fixture-directo-no-http");
        assertThat(CourierStub.requests()).isOne();
        assertThat(CourierStub.redirects()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\",\"disponible\":false}",
            "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\",\"disponible\":false,\"disponible\":true}"})
    void validaFormaTiposYCoherenciaDelContrato(String payload) {
        UUID id = UUID.randomUUID();
        CourierStub.responder(200, payload.replace("ID", id.toString()), "application/json", 0);
        assertThatThrownBy(() -> courier.verificarDisponible(id)).isInstanceOf(CourierRespuestaInvalidaException.class);
        assertThat(CourierStub.requests()).isOne();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void limitaLaEsperaDeCabecerasYCuerpo(boolean body) {
        CourierStub.responder(200, null, "application/json", body ? 0 : 2000);
        if (body) CourierStub.demorarCuerpo(2000);
        long started = System.nanoTime();
        assertThatThrownBy(() -> courier.verificarDisponible(UUID.randomUUID()))
                .isInstanceOf(CourierNoDisponibleException.class).hasCause(null);
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1800);
        assertThat(CourierStub.requests()).isOne();
    }

    @Test
    void inactivoConservaElRechazoDeNegocio() {
        UUID id = UUID.randomUUID();
        CourierStub.responder(200, "{\"repartidorId\":\"" + id + "\",\"estado\":\"INACTIVO\",\"disponible\":false}",
                "application/json", 0);
        assertThatThrownBy(() -> courier.verificarDisponible(id)).isInstanceOf(ReglaNegocioException.class);
    }

    private static void select(String url) {
        URI uri = URI.create(url);
        TARGET.set(new DefaultServiceInstance("courier-fixture", "courier-service", uri.getHost(), uri.getPort(), false));
    }

    /** Contexto mínimo sin base de datos, con descubrimiento controlado para Feign real. */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableFeignClients(clients = CourierAvailabilityApi.class)
    @EnableConfigurationProperties(CourierClientProperties.class)
    @Import({FeignCourierClient.class, CourierBearerProvider.class})
    static class TestApplication {
        /** @return proveedor de instancias cuyo destino puede variar durante la ejecución */
        @Bean
        DiscoveryClient discoveryClient() {
            return new DiscoveryClient() {
                @Override public String description() { return "Descubrimiento controlado de prueba"; }
                @Override public List<String> getServices() { return List.of("courier-service"); }
                @Override public List<ServiceInstance> getInstances(String serviceId) {
                    ServiceInstance target = TARGET.get();
                    return "courier-service".equalsIgnoreCase(serviceId) && target != null ? List.of(target) : List.of();
                }
            };
        }
    }
}
