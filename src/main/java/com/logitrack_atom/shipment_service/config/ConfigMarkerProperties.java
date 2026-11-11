package com.logitrack_atom.shipment_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identifica la configuración cargada sin exponer secretos ni endpoints de diagnóstico.
 *
 * <p>Los perfiles remotos aportan este marcador desde Git. Las pruebas pueden
 * comprobar su enlace real a un bean y los perfiles local/test declaran su origen.</p>
 *
 * @param marker valor no sensible que identifica aplicación y ambiente
 * @author MaxDz
 * @version 0.2.0
 */
@ConfigurationProperties("logitrack.config")
public record ConfigMarkerProperties(String marker) {
    /**
     * Rechaza una configuración incompleta antes de considerar válido el arranque.
     * @param marker marcador suministrado por el origen de configuración
     * @throws IllegalArgumentException si falta el marcador o está vacío
     */
    public ConfigMarkerProperties {
        if (marker == null || marker.isBlank()) {
            throw new IllegalArgumentException("Falta el marcador de configuración de shipment-service.");
        }
    }
}
