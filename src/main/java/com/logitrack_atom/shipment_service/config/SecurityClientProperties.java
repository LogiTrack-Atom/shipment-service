package com.logitrack_atom.shipment_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Configura el origen de consulta administrativa de cuentas y límites de espera finitos.
 *
 * @param baseUrl origen HTTP o HTTPS configurado, sin credenciales ni query
 * @param connectTimeout espera positiva de conexión, hasta diez segundos
 * @param readTimeout espera positiva de lectura, hasta treinta segundos
 * @author MaxDz
 * @version 0.2.0
 */
@ConfigurationProperties("clients.security")
public record SecurityClientProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
    /**
     * Rechaza destinos o tiempos inválidos al arrancar.
     *
     * @param baseUrl dirección del proveedor security
     * @param connectTimeout tiempo de conexión
     * @param readTimeout tiempo de lectura
     * @throws IllegalArgumentException si no se cumple el contrato de configuración
     */
    public SecurityClientProperties {
        if (baseUrl == null
                || !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                || baseUrl.getHost() == null
                || baseUrl.getPort() == 0
                || baseUrl.getPort() > 65535
                || baseUrl.getRawUserInfo() != null
                || baseUrl.getRawQuery() != null
                || baseUrl.getRawFragment() != null
                || !(baseUrl.getPath().isEmpty() || "/".equals(baseUrl.getPath()))) {
            throw new IllegalArgumentException("El origen del servicio de cuentas es inválido.");
        }
        validarTiempo(connectTimeout, Duration.ofSeconds(10));
        validarTiempo(readTimeout, Duration.ofSeconds(30));
    }

    private static void validarTiempo(Duration time, Duration maximum) {
        if (time == null
                || time.compareTo(Duration.ofMillis(1)) < 0
                || time.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    "Los timeouts deben ser positivos y estar dentro de sus límites.");
        }
    }
}
