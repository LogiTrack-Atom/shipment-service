package com.logitrack_atom.shipment_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shipmentServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("shipment-service API")
                .version("v1")
                .description("Microservicio de envíos y repartidores de LogiTrack"));
    }
}