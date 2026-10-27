package com.logitrack_atom.shipment_service.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Collections;

/**
 * Obtiene exclusivamente credenciales administrativas verificadas antes de llamar a courier.
 *
 * <p>Esta etapa valida firma y claims localmente al requerir la precondición remota.
 * No reenvía cabeceras arbitrarias ni acepta token por query o formulario. El filtro
 * general y los permisos de CLIENTE/REPARTIDOR por pertenencia se completan en #25.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class CourierBearerProvider {
    private final JwtDecoder decoder;
    private final DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();

    /**
     * Recibe el verificador RS256 local.
     * @param decoder verificador de firma y contrato JWT
     */
    public CourierBearerProvider(JwtDecoder decoder) { this.decoder = decoder; }

    /**
     * Valida la única cabecera Bearer y exige administración antes de cualquier HTTP remoto.
     * @return JWT validado, utilizado únicamente como cabecera del origen configurado
     * @throws org.springframework.security.core.AuthenticationException si falta o es inválido
     * @throws AccessDeniedException si el token válido no tiene ADMIN
     */
    public String obtener() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new AuthenticationCredentialsNotFoundException("Se requiere autenticación Bearer.");
        }
        var request = attributes.getRequest();
        if (Collections.list(request.getHeaders("Authorization")).size() != 1) {
            throw new BadCredentialsException("Credencial Bearer inválida.");
        }
        String bearer = resolver.resolve(request);
        if (bearer == null) throw new AuthenticationCredentialsNotFoundException("Se requiere autenticación Bearer.");
        try {
            var jwt = decoder.decode(bearer);
            if (!jwt.getClaimAsStringList("roles").contains("ADMIN")) {
                throw new AccessDeniedException("Se requiere administración autenticada.");
            }
        } catch (JwtException ex) {
            throw new BadCredentialsException("Credencial Bearer inválida.");
        }
        return bearer;
    }
}
