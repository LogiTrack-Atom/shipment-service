package com.logitrack_atom.shipment_service.assignment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Usa el reloj UTC existente para vencimiento local sin dependencia de conexión al broker. */
@Configuration(proxyBeanMethods = false)
public class AssignmentMaintenanceConfig {
    /** Crea la configuración ligera de mantenimiento. */
    public AssignmentMaintenanceConfig() { }

    /** Activa solo el trabajo local de mantenimiento del módulo habilitado. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "logitrack.assignment", name = "enabled", havingValue = "true")
    public static class Enabled {
        /** Crea la configuración de mantenimiento habilitado. */
        public Enabled() { }

        /**
         * Construye una tarea que no necesita RabbitMQ.
         * @param results guardas y expiración local
         * @return finalizador acotado
         */
        @Bean
        public AssignmentExpiryTask assignmentExpiryTask(AssignmentResultsService results) { return new AssignmentExpiryTask(results); }
    }
}
