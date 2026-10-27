package com.logitrack_atom.shipment_service.security;

import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.repository.RepartidorUsuarioRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Evalúa pertenencia únicamente con sub validado y asociaciones persistidas locales.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class ShipmentAccess {
    private final RepartidorUsuarioRepository repartidores;

    /**
     * Recibe las asociaciones de identidad verificadas.
     *
     * @param repartidores asociaciones inmutables verificadas
     */
    public ShipmentAccess(RepartidorUsuarioRepository repartidores) {
        this.repartidores = repartidores;
    }

    private JwtAuthenticationToken actual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated())
            throw new AuthenticationCredentialsNotFoundException("Se requiere autenticación.");
        return jwt;
    }

    /**
     * Obtiene el UUID de cuenta del token autenticado.
     *
     * @return identidad de cuenta del JWT validado
     */
    public UUID usuarioId() {
        return UUID.fromString(actual().getToken().getSubject());
    }

    /**
     * Consulta un rol del token validado.
     *
     * @param role rol de negocio
     * @return verdadero si está concedido
     */
    public boolean tieneRol(String role) {
        return actual().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Comprueba si la cuenta tiene el rol administrativo.
     *
     * @return verdadero únicamente para ADMIN
     */
    public boolean esAdmin() {
        return tieneRol("ADMIN");
    }

    /**
     * Resuelve el UUID de dominio asociado a la cuenta.
     *
     * @return repartidor localmente asociado, o nulo
     */
    public UUID repartidorId() {
        return repartidores.findByUsuarioId(usuarioId()).map(x -> x.getRepartidorId()).orElse(null);
    }

    /**
     * Exige pertenencia a un envío sin invocar servicios remotos.
     *
     * @param envio agregado consultado
     */
    public void permitirLectura(Envio envio) {
        if (esAdmin()) return;
        UUID user = usuarioId();
        if (tieneRol("CLIENTE") && user.equals(envio.getCliente().getUsuarioId())) return;
        if (tieneRol("REPARTIDOR")
                && envio.getRepartidorId() != null
                && envio.getRepartidorId().equals(repartidorId())) return;
        throw new AccessDeniedException("Acceso denegado.");
    }

    /**
     * Autoriza solo administración o transiciones de reparto sobre el envío propio.
     *
     * @param envio agregado que será modificado
     * @param destino estado solicitado
     */
    public void permitirCambio(Envio envio, EstadoEnvio destino) {
        if (esAdmin()) return;
        if (!tieneRol("REPARTIDOR")
                || !(destino == EstadoEnvio.EN_RUTA || destino == EstadoEnvio.ENTREGADO))
            throw new AccessDeniedException("Acceso denegado.");
        if (envio.getRepartidorId() == null || !envio.getRepartidorId().equals(repartidorId()))
            throw new AccessDeniedException("Acceso denegado.");
    }
}
