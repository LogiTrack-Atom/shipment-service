package com.logitrack_atom.shipment_service.config;

import com.logitrack_atom.shipment_service.dto.response.ApiError;
import com.logitrack_atom.shipment_service.security.ShipmentQueryFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Define la frontera stateless de autorización de envíos e identidades locales.
 *
 * <p>La API no usa sesiones, formularios ni autenticación por cookies. Las rutas administrativas
 * exigen ADMIN y las rutas no declaradas se deniegan.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    /** Crea la configuración de seguridad HTTP. */
    public SecurityConfig() {}

    /**
     * Aplica acceso público a salud y roles explícitos a las rutas declaradas.
     *
     * @param http constructor de seguridad web
     * @param mapper serializador JSON de la aplicación
     * @param decoder validador local de JWT
     * @param converter conversor de roles de negocio
     * @return cadena de filtros stateless
     * @throws Exception si no puede construirse la configuración de seguridad
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper mapper,
            JwtDecoder decoder,
            JwtAuthenticationConverter converter)
            throws Exception {
        AuthenticationEntryPoint unauthorized =
                (request, response, ex) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    escribirError(
                            response,
                            mapper,
                            HttpStatus.UNAUTHORIZED,
                            "Se requiere una credencial Bearer válida");
                };
        AccessDeniedHandler forbidden =
                (request, response, ex) ->
                        escribirError(
                                response,
                                mapper,
                                HttpStatus.FORBIDDEN,
                                "No tiene permiso para realizar esta operación");
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/envios",
                                                "/api/v1/envios/{id}",
                                                "/api/v1/envios/seguimiento/{codigoSeguimiento}",
                                                "/api/v1/envios/{id}/historial")
                                        .hasAnyRole("ADMIN", "CLIENTE", "REPARTIDOR")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/envios")
                                        .hasAnyRole("ADMIN", "CLIENTE")
                                        .requestMatchers(
                                                HttpMethod.PATCH, "/api/v1/envios/{id}/estado")
                                        .hasAnyRole("ADMIN", "REPARTIDOR")
                                        .requestMatchers(
                                                HttpMethod.PATCH, "/api/v1/envios/{id}/cancelacion")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.PUT,
                                                "/api/v1/envios/{id}/repartidor",
                                                "/api/v1/clientes/{id}/usuario",
                                                "/api/v1/asociaciones/repartidores/{id}")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(unauthorized)
                                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(
                        resource ->
                                resource.authenticationEntryPoint(unauthorized)
                                        .accessDeniedHandler(forbidden)
                                        .bearerTokenResolver(
                                                request -> {
                                                    var headers =
                                                            java.util.Collections.list(
                                                                    request.getHeaders(
                                                                            HttpHeaders
                                                                                    .AUTHORIZATION));
                                                    if (headers.size() > 1)
                                                        throw new org.springframework.security
                                                                .oauth2.core
                                                                .OAuth2AuthenticationException(
                                                                org.springframework.security.oauth2
                                                                        .server.resource
                                                                        .BearerTokenErrors
                                                                        .invalidRequest(
                                                                                "Cabecera"
                                                                                    + " Authorization"
                                                                                    + " ambigua"));
                                                    return new org.springframework.security.oauth2
                                                                    .server.resource.web
                                                                    .DefaultBearerTokenResolver()
                                                            .resolve(request);
                                                })
                                        .jwt(
                                                jwt ->
                                                        jwt.decoder(decoder)
                                                                .jwtAuthenticationConverter(
                                                                        converter)))
                .addFilterAfter(
                        new ShipmentQueryFilter(mapper), BearerTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * Serializa una denegación sin detalles de las credenciales recibidas.
     *
     * @param response respuesta HTTP
     * @param mapper serializador JSON
     * @param status estado de la denegación
     * @param mensaje descripción segura
     * @throws IOException si no se puede escribir la respuesta
     */
    private void escribirError(
            HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String mensaje)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(
                response.getOutputStream(),
                new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        mensaje,
                        List.of()));
    }
}
