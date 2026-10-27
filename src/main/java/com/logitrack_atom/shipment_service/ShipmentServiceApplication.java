package com.logitrack_atom.shipment_service;

import com.logitrack_atom.shipment_service.config.ConfigMarkerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Inicia el servicio de envíos con el marcador de configuración enlazado y validado.
 *
 * @author MaxDz
 * @version 0.2.0
 * @see ConfigMarkerProperties
 */
@EnableConfigurationProperties(ConfigMarkerProperties.class)
@SpringBootApplication
public class ShipmentServiceApplication {
    /** Crea la configuración principal del servicio. */
    public ShipmentServiceApplication() { }

    /**
     * Inicia el contexto y aplica el perfil y origen de configuración seleccionados.
     * @param args parámetros externos de arranque, sin valores de secretos versionados
     */
    public static void main(String[] args) {
        SpringApplication.run(ShipmentServiceApplication.class, args);
    }
}
