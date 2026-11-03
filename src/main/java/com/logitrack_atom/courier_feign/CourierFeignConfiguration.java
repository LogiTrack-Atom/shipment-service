package com.logitrack_atom.courier_feign;

import com.logitrack_atom.shipment_service.client.CourierAvailabilityDecoder;
import com.logitrack_atom.shipment_service.config.CourierClientProperties;
import feign.Logger;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

/**
 * Configuración exclusiva del contexto Feign de disponibilidad.
 *
 * <p>Está fuera del escaneo de shipment-service para no modificar otros transportes.
 * Los timeouts provienen de las propiedades ya validadas, sin usar su URL administrativa.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@Configuration(proxyBeanMethods = false)
public class CourierFeignConfiguration {
    /** Crea la configuración del cliente declarativo. */
    public CourierFeignConfiguration() { }

    /**
     * Fija límites positivos y evita reenviar el Bearer mediante redirecciones.
     * @param properties límites externos validados
     * @return opciones HTTP del cliente, con redirecciones desactivadas
     */
    @Bean
    public Request.Options courierOptions(CourierClientProperties properties) {
        return new Request.Options(properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS,
                properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS, false);
    }

    /**
     * Desactiva reintentos del cliente Feign.
     * @return política explícita de un solo intento por consulta
     */
    @Bean
    public Retryer courierRetryer() { return Retryer.NEVER_RETRY; }

    /**
     * Desactiva el registro HTTP del cliente.
     * @return nivel que evita registrar cabeceras, cuerpos o credenciales
     */
    @Bean
    public Logger.Level courierLogLevel() { return Logger.Level.NONE; }

    /**
     * Restringe las respuestas al contrato mínimo de disponibilidad.
     * @return decodificador JSON estricto del contrato de disponibilidad
     */
    @Bean
    public Decoder courierDecoder() { return new CourierAvailabilityDecoder(); }

    /**
     * Clasifica el estado HTTP sin leer su cuerpo privado.
     * @return errores HTTP sin copiar cuerpos privados o de tamaño no controlado
     */
    @Bean
    public ErrorDecoder courierErrorDecoder() {
        return (methodKey, response) -> FeignException.errorStatus(methodKey,
                response.toBuilder().body((Response.Body) null).build());
    }
}
