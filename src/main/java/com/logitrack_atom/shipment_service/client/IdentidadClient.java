package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.config.CourierClientProperties;
import com.logitrack_atom.shipment_service.config.SecurityClientProperties;
import com.logitrack_atom.shipment_service.enums.Rol;
import com.logitrack_atom.shipment_service.exception.*;
import com.logitrack_atom.shipment_service.security.CourierBearerProvider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * Verifica asociaciones administrativas mediante consultas REST de solo lectura.
 *
 * <p>Se usa exclusivamente al registrar una asociación. No interviene en login, validación del JWT
 * ni lectura de envíos. Cada origen está configurado y las redirecciones están desactivadas.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
@EnableConfigurationProperties({SecurityClientProperties.class, CourierClientProperties.class})
public class IdentidadClient {
    private final RestClient security;
    private final RestClient courier;
    private final CourierBearerProvider credentials;
    private final JsonMapper mapper =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    /**
     * Configura transportes con límites positivos y sin redirecciones.
     *
     * @param accounts origen de security
     * @param repartidores origen de courier
     * @param credentials acceso al JWT ADMIN validado
     */
    public IdentidadClient(
            SecurityClientProperties accounts,
            CourierClientProperties repartidores,
            CourierBearerProvider credentials) {
        this.credentials = credentials;
        security = crear(accounts.baseUrl(), accounts.connectTimeout(), accounts.readTimeout());
        courier =
                crear(
                        repartidores.baseUrl(),
                        repartidores.connectTimeout(),
                        repartidores.readTimeout());
    }

    private RestClient crear(URI origin, Duration connect, Duration read) {
        var http =
                HttpClient.newBuilder()
                        .connectTimeout(connect)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(read);
        return RestClient.builder().baseUrl(origin.toString()).requestFactory(factory).build();
    }

    /**
     * Comprueba existencia e identidad exacta de una cuenta con rol compatible.
     *
     * @param usuarioId cuenta solicitada
     * @param role rol requerido para el dominio
     */
    public void verificarCuenta(UUID usuarioId, Rol role) {
        var node = consultar(security, "/api/v1/usuarios/{id}", usuarioId, "Usuario");
        if (!coincide(node.path("id"), usuarioId)
                || !node.path("roles").isArray()
                || node.path("roles").isEmpty()) throw invalid();
        boolean found = false;
        for (var value : node.path("roles")) {
            if (!value.isString()) throw invalid();
            try {
                Rol.valueOf(value.asString());
            } catch (IllegalArgumentException ex) {
                throw invalid();
            }
            found |= value.asString().equals(role.name());
        }
        if (!found)
            throw new ReglaNegocioException(
                    "La cuenta no tiene el rol requerido para esta asociación");
    }

    /**
     * Comprueba la asociación inmutable vigente en el proveedor propietario.
     *
     * @param repartidorId UUID de dominio
     * @param usuarioId cuenta que debe estar asociada
     */
    public void verificarRepartidor(UUID repartidorId, UUID usuarioId) {
        var node =
                consultar(courier, "/api/v1/repartidores/{id}/usuario", repartidorId, "Repartidor");
        if (!coincide(node.path("repartidorId"), repartidorId) || !node.has("usuarioId"))
            throw invalid();
        if (node.path("usuarioId").isNull()) throw new AsociacionConflictoException();
        if (!node.path("usuarioId").isString()) throw invalid();
        try {
            if (!UUID.fromString(node.path("usuarioId").asString())
                    .toString()
                    .equalsIgnoreCase(node.path("usuarioId").asString())) throw invalid();
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
        if (!coincide(node.path("usuarioId"), usuarioId)) throw new AsociacionConflictoException();
    }

    private boolean coincide(JsonNode node, UUID id) {
        return node.isString() && id.toString().equalsIgnoreCase(node.asString());
    }

    private ServicioIdentidadException invalid() {
        return new ServicioIdentidadException(HttpStatus.BAD_GATEWAY);
    }

    private JsonNode consultar(RestClient client, String path, UUID id, String recurso) {
        String bearer = credentials.obtenerAdministrativo();
        try {
            return client.get()
                    .uri(path, id)
                    .headers(h -> h.setBearerAuth(bearer))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange(
                            (request, response) -> {
                                int code = response.getStatusCode().value();
                                if (code == 404)
                                    throw new RecursoNoEncontradoException(recurso, id);
                                if (code == 429 || code >= 500 && code < 600)
                                    throw new ServicioIdentidadException(
                                            HttpStatus.SERVICE_UNAVAILABLE);
                                if (code != 200) throw invalid();
                                try {
                                    if (response.getHeaders().getContentType() == null
                                            || !MediaType.APPLICATION_JSON.isCompatibleWith(
                                                    response.getHeaders().getContentType()))
                                        throw invalid();
                                    var body = response.getBody().readNBytes(65_537);
                                    if (body.length > 65_536) throw invalid();
                                    var node = mapper.readTree(body);
                                    if (node == null || !node.isObject()) throw invalid();
                                    return node;
                                } catch (tools.jackson.core.JacksonException
                                        | IllegalArgumentException ex) {
                                    throw invalid();
                                }
                            });
        } catch (RestClientException ex) {
            throw new ServicioIdentidadException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
