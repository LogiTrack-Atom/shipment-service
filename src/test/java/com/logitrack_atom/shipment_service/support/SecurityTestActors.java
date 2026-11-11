package com.logitrack_atom.shipment_service.support;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Prepara el actor administrativo exclusivo de las regresiones de negocio directas. Las pruebas
 * HTTP de seguridad envían JWT firmados al filtro real.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public final class SecurityTestActors {
    /** Identidad sintética del administrador de fixtures. */
    public static final UUID ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private SecurityTestActors() {}

    /** Establece un contexto de prueba para llamadas directas a servicios protegidos. */
    public static void admin() {
        var jwt =
                Jwt.withTokenValue("fixture-directo-no-http")
                        .header("alg", "RS256")
                        .subject(ADMIN.toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900))
                        .claim("roles", List.of("ADMIN"))
                        .build();
        var converter =
                new com.logitrack_atom.shipment_service.config.JwtConfig()
                        .jwtAuthenticationConverter();
        SecurityContextHolder.getContext().setAuthentication(converter.convert(jwt));
    }

    /**
     * @return token ADMIN firmado para las solicitudes MVC de regresión
     */
    public static String adminToken() {
        return TestRsaKeys.token(ADMIN, "ADMIN");
    }

    /** Retira el contexto sintético después de cada prueba. */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
