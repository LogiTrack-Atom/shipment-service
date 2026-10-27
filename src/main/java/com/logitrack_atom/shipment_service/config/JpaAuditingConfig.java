package com.logitrack_atom.shipment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Configura auditoría UTC con la precisión DATETIME(6) del esquema MySQL.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditoriaDateTimeProvider")
public class JpaAuditingConfig {
    /** Crea la configuración de auditoría local. */
    public JpaAuditingConfig() {}

    /**
     * Proporciona instantes representables sin redondeo adicional al persistir.
     *
     * @return proveedor de tiempo UTC con precisión de microsegundos
     */
    @Bean
    public DateTimeProvider auditoriaDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
