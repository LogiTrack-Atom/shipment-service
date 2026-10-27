package com.logitrack_atom.shipment_service.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Reutiliza exclusivamente el JWT validado por el filtro stateless de la API.
 *
 * <p>El servicio de envíos comprueba la pertenencia antes de invocar courier. Las asociaciones
 * administrativas requieren además ADMIN explícito.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class CourierBearerProvider {
    /** Crea el acceso a credenciales del contexto autenticado. */
    public CourierBearerProvider() {}

    /**
     * Obtiene la credencial para una operación de reparto ya autorizada localmente.
     *
     * @return JWT validado, sin volver a leer cabeceras ni llamar al emisor
     */
    public String obtener() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated())
            throw new AuthenticationCredentialsNotFoundException("Se requiere autenticación.");
        if (jwt.getAuthorities().stream()
                .noneMatch(
                        a ->
                                a.getAuthority().equals("ROLE_ADMIN")
                                        || a.getAuthority().equals("ROLE_REPARTIDOR")))
            throw new AccessDeniedException("Acceso denegado.");
        return jwt.getToken().getTokenValue();
    }

    /**
     * Obtiene la credencial de una operación administrativa ya autenticada.
     *
     * @return JWT administrativo validado para verificar una asociación
     */
    public String obtenerAdministrativo() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
            throw new AccessDeniedException("Acceso denegado.");
        return obtener();
    }
}
