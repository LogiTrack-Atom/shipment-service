package com.logitrack_atom.shipment_service.security;

import com.logitrack_atom.shipment_service.dto.response.ApiError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Rechaza selectores de identidad por query antes de invocar controladores de negocio.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class ShipmentQueryFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;

    /**
     * Recibe el serializador del formato público de errores.
     *
     * @param mapper serializador del contrato público
     */
    public ShipmentQueryFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/v1/")) {
            boolean list = "GET".equals(request.getMethod()) && "/api/v1/envios".equals(path);
            boolean invalid =
                    request.getParameterMap().keySet().stream()
                            .anyMatch(key -> !(list && key.equals("estado")));
            if (invalid
                    && org.springframework.security.core.context.SecurityContextHolder.getContext()
                                    .getAuthentication()
                            instanceof
                            org.springframework.security.oauth2.server.resource.authentication
                                    .JwtAuthenticationToken) {
                response.setStatus(403);
                response.setContentType("application/json");
                mapper.writeValue(
                        response.getOutputStream(),
                        new ApiError(
                                Instant.now(),
                                403,
                                "Forbidden",
                                "No tiene permiso para realizar esta operación",
                                List.of()));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
