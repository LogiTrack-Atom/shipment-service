package com.logitrack_atom.shipment_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registra al arrancar el marcador no sensible recibido desde la configuración.
 *
 * <p>No publica propiedades, URLs, credenciales ni claves; permite identificar
 * el ambiente cargado sin habilitar endpoints de diagnóstico.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class ConfigMarkerReporter implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigMarkerReporter.class);
    private final ConfigMarkerProperties properties;

    /**
     * Recibe el bean enlazado desde la fuente de configuración efectiva.
     * @param properties marcador de aplicación y ambiente
     */
    public ConfigMarkerReporter(ConfigMarkerProperties properties) {
        this.properties = properties;
    }

    /**
     * Identifica la configuración activa sin inspeccionar datos sensibles.
     * @param args argumentos de ejecución, que no se registran
     */
    @Override
    public void run(ApplicationArguments args) {
        LOG.info("Configuración activa de shipment: {}", properties.marker());
    }
}
