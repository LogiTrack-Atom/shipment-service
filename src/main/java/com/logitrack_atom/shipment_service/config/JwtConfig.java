package com.logitrack_atom.shipment_service.config;

import com.logitrack_atom.shipment_service.security.JwtContractValidator;
import com.logitrack_atom.shipment_service.security.PublicKeyLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

/**
 * Configura validación RS256 local sin discovery ni introspección remota.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    /** Crea la configuración criptográfica del servicio. */
    public JwtConfig() { }

    /**
     * Proporciona el reloj de validación.
     * @return reloj UTC del sistema
     */
    @Bean
    public Clock jwtClock() { return Clock.systemUTC(); }

    /**
     * Carga únicamente la clave pública externa de verificación.
     * @param properties ubicación del archivo PEM
     * @param loader cargador de clave pública
     * @return clave RSA válida, sin material privado del emisor
     */
    @Bean
    public RSAPublicKey jwtPublicKey(JwtProperties properties, PublicKeyLoader loader) {
        return loader.cargar(properties.publicKeyLocation());
    }

    /**
     * Valida exclusivamente RS256 con una clave pública y los claims acordados.
     * @param publicKey clave pública local
     * @param properties emisor, audiencia y límites de tiempo
     * @param clock reloj utilizado por los validadores
     * @return decoder que no realiza consultas HTTP
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(properties.clockSkew());
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(properties.issuer()), new JwtContractValidator(properties, clock)));
        return decoder;
    }

    /**
     * Convierte roles explícitos del token a autoridades ROLE_ de Spring Security.
     * @return conversor de roles sin utilizar scopes como permisos de negocio
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
