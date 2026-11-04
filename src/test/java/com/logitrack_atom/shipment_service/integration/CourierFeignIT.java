package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.service.ShipmentService;
import com.logitrack_atom.shipment_service.support.CourierStub;
import com.logitrack_atom.shipment_service.support.TestRsaKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cloud.client.discovery.simple.InstanceProperties;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.URI;
import com.sun.net.httpserver.HttpServer;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.util.stream.Stream;
import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.solicitud;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ejecuta controlador, validación JWT, Feign/LoadBalancer y transacciones MySQL reales.
 *
 * <p>Solo el proveedor remoto es un servidor HTTP controlado; ningún bean de
 * shipment ni repositorio se sustituye por mocks.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CourierFeignIT {
    private static final UUID ADMIN = UUID.randomUUID();
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("shipment_courier_test").withUsername("shipment_app")
            .withPassword(UUID.randomUUID().toString()).withUrlParam("serverTimezone", "UTC")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/mysql/init/001-schema.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/001-schema.sql");
    static { MYSQL.start(); }
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        TestRsaKeys.registrar(registry);
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("clients.courier.base-url",CourierStub::baseUrl);
        registry.add("clients.courier.read-timeout",()->"500ms");
        registry.add("spring.cloud.discovery.client.simple.instances.courier-service[0].uri",CourierStub::baseUrl);
        registry.add("spring.cloud.loadbalancer.cache.enabled",()->"false");
        registry.add("eureka.client.enabled",()->"false");
    }
    @Autowired private MockMvc mvc;
    @Autowired private ShipmentService shipmentService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SimpleDiscoveryProperties discovery;

    @Autowired private org.springframework.web.context.WebApplicationContext webContext;
    @org.junit.jupiter.api.AfterEach
    void limpiarActor() { com.logitrack_atom.shipment_service.support.SecurityTestActors.clear(); }

    @BeforeEach
    void limpiar() {
        com.logitrack_atom.shipment_service.support.SecurityTestActors.admin();
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(webContext.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class))
                .alwaysDo(result->com.logitrack_atom.shipment_service.support.SecurityTestActors.admin()).build();
        jdbc.update("DELETE FROM repartidor_usuarios");
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("shipment_courier_test");
        jdbc.update("DELETE FROM historial_estados_envio");jdbc.update("DELETE FROM envios");jdbc.update("DELETE FROM clientes");
        CourierStub.reset();
    }

    @Test
    void asignaReemplazaYReconsultaAlIniciarRutaSinHistoriaDeAsignacion() throws Exception {
        var envio = nuevo(); UUID primero=UUID.randomUUID(); UUID segundo=UUID.randomUUID();
        String bearer=TestRsaKeys.token(ADMIN,"ADMIN");
        for (UUID repartidor : new UUID[]{primero,segundo}) {
            asignar(envio.id(),repartidor,bearer,200);
            assertThat(CourierStub.authorization()).isEqualTo("Bearer "+bearer);
            assertThat(CourierStub.method()).isEqualTo("GET");
            assertThat(CourierStub.path()).isEqualTo("/api/v1/repartidores/"+repartidor+"/disponibilidad");
            assertThat(shipmentService.obtenerPorId(envio.id()).repartidorId()).isEqualTo(repartidor);
            assertThat(shipmentService.obtenerHistorial(envio.id())).hasSize(1);
        }
        mvc.perform(patch("/api/v1/envios/{id}/estado",envio.id()).header("Authorization","Bearer "+bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_RUTA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_RUTA"));
        assertThat(CourierStub.requests()).isEqualTo(3);
        assertThat(shipmentService.obtenerHistorial(envio.id())).hasSize(2);
        CourierStub.responder(503,"privado","text/plain",0);
        mvc.perform(get("/api/v1/envios/{id}",envio.id()).header("Authorization","Bearer "+bearer)).andExpect(status().isOk());
        assertThat(shipmentService.obtenerHistorial(envio.id())).hasSize(2);
        shipmentService.cambiarEstado(envio.id(),EstadoEnvio.ENTREGADO);
        assertThat(CourierStub.requests()).isEqualTo(3);
    }

    @ParameterizedTest(name="HTTP {0} -> {1}")
    @CsvSource({"404,404","401,502","403,502","429,503","500,503","503,503","201,502","204,502","302,502","307,502","400,502","422,502","600,502"})
    void traduceEstadoRemotoYNoModificaNingunDato(int remote,int expected) throws Exception {
        var antes=conAsignacion(); UUID nuevo=UUID.randomUUID();
        CourierStub.responder(remote,"contenido privado del proveedor","application/json",0);
        asignar(antes.id(),nuevo,TestRsaKeys.token(ADMIN,"ADMIN"),expected);
        comprobarSinMutacion(antes);
        assertThat(CourierStub.requests()).isOne();assertThat(CourierStub.redirects()).isZero();
    }

    @Test
    void inactivoDevuelve422YTampocoPermiteIniciarRuta() throws Exception {
        var antes=conAsignacion();String body="{\"repartidorId\":\""+antes.repartidorId()+"\",\"estado\":\"INACTIVO\",\"disponible\":false}";
        CourierStub.responder(200,body,"application/json",0);
        asignar(antes.id(),antes.repartidorId(),TestRsaKeys.token(ADMIN,"ADMIN"),422);
        mvc.perform(patch("/api/v1/envios/{id}/estado",antes.id()).header("Authorization","Bearer "+TestRsaKeys.token(ADMIN,"ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_RUTA\"}"))
                .andExpect(status().isUnprocessableContent());
        comprobarSinMutacion(antes);assertThat(CourierStub.requests()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("cuerposInvalidos")
    void rechazaContratoJsonInvalidoSinMutaciones(String payload) throws Exception {
        var antes=conAsignacion();
        CourierStub.responder(200,payload.replace("ID",antes.repartidorId().toString()),"application/json",0);
        asignar(antes.id(),antes.repartidorId(),TestRsaKeys.token(ADMIN,"ADMIN"),502);
        comprobarSinMutacion(antes);assertThat(CourierStub.requests()).isOne();
    }

    static Stream<String> cuerposInvalidos() {
        return Stream.of("", "null", "[]", "{}", "{", "x".repeat(65_537),
                "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\"}",
                "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\",\"disponible\":false}",
                "{\"repartidorId\":\"ID\",\"estado\":\"INACTIVO\",\"disponible\":true}",
                "{\"repartidorId\":\"ID\",\"estado\":0,\"disponible\":true}",
                "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\",\"disponible\":\"true\"}",
                "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\",\"disponible\":null}",
                "{\"repartidorId\":\"ID\",\"estado\":\"OTRO\",\"disponible\":true}",
                "{\"repartidorId\":\"00000000-0000-0000-0000-000000000001\",\"estado\":\"ACTIVO\",\"disponible\":true}",
                "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\",\"disponible\":true} {}",
                "{\"repartidorId\":\"ID\",\"estado\":\"ACTIVO\",\"disponible\":false,\"disponible\":true}");
    }

    @ParameterizedTest
    @ValueSource(strings={"text/html","text/plain","application/xml","invalid content type"})
    void contenidoNoJsonDevuelve502(String contentType) throws Exception {
        var antes=conAsignacion();CourierStub.responder(200,"{}",contentType,0);
        asignar(antes.id(),antes.repartidorId(),TestRsaKeys.token(ADMIN,"ADMIN"),502);comprobarSinMutacion(antes);
    }

    @Test
    void timeoutDevuelve503SinEventoNiCambioDeAuditoria() throws Exception {
        var antes=conAsignacion();CourierStub.responder(200,null,"application/json",2000);
        long start=System.nanoTime();
        asignar(antes.id(),UUID.randomUUID(),TestRsaKeys.token(ADMIN,"ADMIN"),503);
        assertThat((System.nanoTime()-start)/1_000_000).isLessThan(4000);
        comprobarSinMutacion(antes);assertThat(CourierStub.requests()).isOne();
    }

    @Test
    void timeoutTambienLimitaLaEsperaDelCuerpoDespuesDeLasCabeceras() throws Exception {
        var antes=conAsignacion();CourierStub.demorarCuerpo(2000);
        long start=System.nanoTime();
        asignar(antes.id(),antes.repartidorId(),TestRsaKeys.token(ADMIN,"ADMIN"),503);
        assertThat((System.nanoTime()-start)/1_000_000).isLessThan(1500);
        comprobarSinMutacion(antes);assertThat(CourierStub.requests()).isOne();
    }

    @ParameterizedTest
    @CsvSource({"503,503", "401,502", "404,404"})
    void falloRemotoAlIniciarRutaNoAgregaEventos(int remote,int expected) throws Exception {
        var antes=conAsignacion();CourierStub.responder(remote,"privado","application/json",0);
        mvc.perform(patch("/api/v1/envios/{id}/estado",antes.id()).header("Authorization","Bearer "+TestRsaKeys.token(ADMIN,"ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_RUTA\"}"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected));
        comprobarSinMutacion(antes);assertThat(CourierStub.requests()).isOne();
    }

    @Test
    void sinContentTypeNoAceptaLaRespuesta() throws Exception {
        var antes=conAsignacion();CourierStub.responder(200,null,null,0);
        asignar(antes.id(),antes.repartidorId(),TestRsaKeys.token(ADMIN,"ADMIN"),502);comprobarSinMutacion(antes);
    }

    @Test
    void sinInstanciasNoEscribeYLaRecuperacionPermiteAsignarEIniciarRuta() throws Exception {
        var original = new HashMap<>(discovery.getInstances());
        var before = conAsignacion();
        String bearer = TestRsaKeys.token(ADMIN, "ADMIN");
        UUID replacement = UUID.randomUUID();
        try {
            var unavailable = new HashMap<>(original);
            unavailable.put("courier-service", List.of());
            discovery.setInstances(unavailable);
            asignar(before.id(), replacement, bearer, 503);
            iniciarRuta(before.id(), bearer, 503);
            comprobarSinMutacion(before);
            assertThat(CourierStub.requests()).isZero();

            discovery.setInstances(original);
            asignar(before.id(), replacement, bearer, 200);
            assertThat(shipmentService.obtenerPorId(before.id()).repartidorId()).isEqualTo(replacement);
            assertThat(shipmentService.obtenerHistorial(before.id())).hasSize(1);
            iniciarRuta(before.id(), bearer, 200);
            assertThat(CourierStub.requests()).isEqualTo(2);
            assertThat(shipmentService.obtenerHistorial(before.id())).extracting("estadoNuevo")
                    .containsExactly(EstadoEnvio.PENDIENTE, EstadoEnvio.EN_RUTA);
        } finally { discovery.setInstances(original); }
    }

    @Test
    void caidaDeProveedorAnunciadoNoEscribeYLaNuevaInstanciaSeRecupera() throws Exception {
        var original = new HashMap<>(discovery.getInstances());
        var firstRequests = new AtomicInteger();
        var recoveredRequests = new AtomicInteger();
        var authorization = new AtomicReference<String>();
        HttpServer first = crearProveedor(firstRequests, authorization);
        HttpServer recovered = null;
        try {
            anunciar(first);
            var created = nuevo();
            String bearer = TestRsaKeys.token(ADMIN, "ADMIN");
            asignar(created.id(), UUID.randomUUID(), bearer, 200);
            var before = shipmentService.obtenerPorId(created.id());
            assertThat(firstRequests.get()).isOne();
            first.stop(0);
            first = null;

            UUID replacement = UUID.randomUUID();
            asignar(before.id(), replacement, bearer, 503);
            iniciarRuta(before.id(), bearer, 503);
            comprobarSinMutacion(before);

            recovered = crearProveedor(recoveredRequests, authorization);
            anunciar(recovered);
            asignar(before.id(), replacement, bearer, 200);
            assertThat(shipmentService.obtenerPorId(before.id()).repartidorId()).isEqualTo(replacement);
            assertThat(shipmentService.obtenerHistorial(before.id())).hasSize(1);
            iniciarRuta(before.id(), bearer, 200);
            assertThat(recoveredRequests.get()).isEqualTo(2);
            assertThat(authorization.get()).isEqualTo("Bearer " + bearer);
            assertThat(shipmentService.obtenerHistorial(before.id())).extracting("estadoNuevo")
                    .containsExactly(EstadoEnvio.PENDIENTE, EstadoEnvio.EN_RUTA);
        } finally {
            discovery.setInstances(original);
            if (first != null) first.stop(0);
            if (recovered != null) recovered.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"sin-token","invalido","CLIENTE","REPARTIDOR","duplicado","query"})
    void noEnviaSolicitudesSinUnaCredencialAdministrativaValidada(String caso) throws Exception {
        var antes=conAsignacion();var request=put("/api/v1/envios/{id}/repartidor",antes.id())
                .contentType(MediaType.APPLICATION_JSON).content("{\"repartidorId\":\""+UUID.randomUUID()+"\"}");
        int expected=401;
        switch(caso) {
            case "invalido" -> request.header("Authorization","Bearer contenido-no-firmado");
            case "duplicado" -> request.header("Authorization","Bearer "+TestRsaKeys.token(ADMIN,"ADMIN"),"Bearer otro");
            case "query" -> request.param("access_token",TestRsaKeys.token(ADMIN,"ADMIN"));
            case "CLIENTE","REPARTIDOR" -> { request.header("Authorization","Bearer "+TestRsaKeys.token(ADMIN,caso));expected=403; }
            default -> { }
        }
        mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected));
        comprobarSinMutacion(antes);assertThat(CourierStub.requests()).isZero();
    }

    @Test
    void reglasLocalesRechazanAntesDeLlamarAlProveedor() throws Exception {
        var envio=nuevo();shipmentService.cancelar(envio.id());
        asignar(envio.id(),UUID.randomUUID(),TestRsaKeys.token(ADMIN,"ADMIN"),422);
        asignar(UUID.randomUUID(),UUID.randomUUID(),TestRsaKeys.token(ADMIN,"ADMIN"),404);
        assertThat(CourierStub.requests()).isZero();
    }

    @Test
    void cancelacionConcurrenteEsperaLaAsignacionYNoPierdeElUuidConfirmado() throws Exception {
        var creado=nuevo();var antes=shipmentService.obtenerPorId(creado.id());
        UUID repartidor=UUID.randomUUID();CourierStub.pausar();
        try (var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var asignacion=executor.submit(()->{asignar(antes.id(),repartidor,TestRsaKeys.token(ADMIN,"ADMIN"),200);return null;});
            assertThat(CourierStub.esperarConsulta()).isTrue();
            var started=new java.util.concurrent.CountDownLatch(1);
            var cancelacion=executor.submit(()->{com.logitrack_atom.shipment_service.support.SecurityTestActors.admin();started.countDown();return shipmentService.cancelar(antes.id());});
            assertThat(started.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                        ()->cancelacion.get(150,java.util.concurrent.TimeUnit.MILLISECONDS));
            } finally { CourierStub.continuar(); }
            asignacion.get(3,java.util.concurrent.TimeUnit.SECONDS);
            var cancelado=cancelacion.get(3,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(cancelado.estado()).isEqualTo(EstadoEnvio.CANCELADO);
            assertThat(cancelado.repartidorId()).isEqualTo(repartidor);
            var persistido=shipmentService.obtenerPorId(antes.id());
            assertThat(persistido.estado()).isEqualTo(EstadoEnvio.CANCELADO);
            assertThat(persistido.repartidorId()).isEqualTo(repartidor);
            assertThat(persistido.createdAt()).isEqualTo(antes.createdAt());
            assertThat(persistido.updatedAt()).isAfter(antes.updatedAt());
            assertThat(shipmentService.obtenerHistorial(antes.id())).extracting("estadoNuevo")
                    .containsExactly(EstadoEnvio.PENDIENTE,EstadoEnvio.CANCELADO);
        }
    }

    private EnvioResponse nuevo() { return shipmentService.registrar(solicitud("rest@example.test")); }
    private EnvioResponse conAsignacion() {
        var envio=nuevo();UUID id=UUID.randomUUID();
        jdbc.update("UPDATE envios SET repartidor_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",id.toString(),envio.id().toString());
        return shipmentService.obtenerPorId(envio.id());
    }
    private void asignar(UUID envio,UUID repartidor,String token,int expected) throws Exception {
        var result = mvc.perform(put("/api/v1/envios/{id}/repartidor",envio).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"repartidorId\":\""+repartidor+"\"}"))
                .andExpect(status().is(expected));
        if (expected != 200) comprobarErrorPublico(result, expected, token);
    }

    private void iniciarRuta(UUID envio, String token, int expected) throws Exception {
        var result = mvc.perform(patch("/api/v1/envios/{id}/estado", envio)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_RUTA\"}")).andExpect(status().is(expected));
        if (expected != 200) comprobarErrorPublico(result, expected, token);
    }

    private void comprobarErrorPublico(org.springframework.test.web.servlet.ResultActions result, int status, String token)
            throws Exception {
        result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.mensaje").isString())
                .andExpect(jsonPath("$.detalles").isArray());
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain(token, "contenido privado", "feign.", "java.net.", "jdbc:", "Authorization");
    }

    private void anunciar(HttpServer server) {
        var instance = new InstanceProperties();
        instance.setServiceId("courier-service");
        instance.setInstanceId("courier-recuperacion");
        instance.setUri(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        var targets = new HashMap<>(discovery.getInstances());
        targets.put("courier-service", List.of(instance));
        discovery.setInstances(targets);
    }

    private HttpServer crearProveedor(AtomicInteger requests, AtomicReference<String> authorization)
            throws java.io.IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/repartidores/", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String id = exchange.getRequestURI().getPath().split("/")[4];
            byte[] body = ("{\"repartidorId\":\"" + id + "\",\"estado\":\"ACTIVO\",\"disponible\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        server.start();
        return server;
    }
    private void comprobarSinMutacion(EnvioResponse antes) {
        assertThat(shipmentService.obtenerPorId(antes.id())).isEqualTo(antes);
        assertThat(shipmentService.obtenerHistorial(antes.id())).hasSize(1);
    }
}
