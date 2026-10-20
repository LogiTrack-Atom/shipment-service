package com.logitrack_atom.shipment_service.config;

import com.logitrack_atom.shipment_service.dto.response.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shipmentServiceOpenApi() {
        Components components = new Components();
        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
        components.addResponses("MedidasInvalidas", error(400,
                "JSON o datos de entrada inválidos: cliente, direcciones o medidas no representables.",
                "Datos de entrada inválidos", "pesoKg: El peso admite hasta 7 dígitos enteros y 3 decimales"));
        components.addResponses("CuerpoInvalido", error(400,
                "JSON, UUID o enum inválido, o incumplimiento de validaciones del cuerpo.",
                "El cuerpo de la solicitud es inválido o contiene un valor no permitido"));
        components.addResponses("UuidInvalido", error(400,
                "El identificador de la ruta no tiene formato UUID.", "Valor inválido para el parámetro 'id'"));
        components.addResponses("EstadoInvalido", error(400,
                "El filtro estado no pertenece al enum correspondiente.", "Valor inválido para el parámetro 'estado'"));
        components.addResponses("EnvioNoEncontrado", error(404, "No existe el envío indicado.",
                "Envio no encontrado: 00000000-0000-0000-0000-000000000001"));
        components.addResponses("TrackingNoEncontrado", error(404, "No existe el código de seguimiento indicado.",
                "Envio no encontrado: LT-NO-EXISTE"));
        components.addResponses("RepartidorNoEncontrado", error(404, "No existe el repartidor indicado.",
                "Repartidor no encontrado: 00000000-0000-0000-0000-000000000001"));
        components.addResponses("AsignacionNoEncontrada", error(404, "No existe el envío o el repartidor indicado.",
                "Repartidor no encontrado: 00000000-0000-0000-0000-000000000001"));
        components.addResponses("TransicionInvalida", error(422,
                "Transición no permitida, o EN_RUTA sin repartidor asignado y activo.",
                "No se puede pasar de PENDIENTE a ENTREGADO"));
        components.addResponses("CancelacionInvalida", error(422, "Solo se puede cancelar un envío PENDIENTE.",
                "No se puede pasar de ENTREGADO a CANCELADO"));
        components.addResponses("AsignacionInvalida", error(422,
                "El envío no está PENDIENTE o el repartidor no está ACTIVO.",
                "Solo se puede asignar repartidor a un envío PENDIENTE"));
        components.addResponses("ConflictoDeDatos", error(409,
                "Una restricción de integridad, longitud o unicidad impide persistir la operación.",
                "La operación viola una restricción de datos, por ejemplo un valor duplicado"));

        return new OpenAPI().components(components).info(new Info()
                .title("shipment-service API").version("v1")
                .description("""
                        Gestión de envíos y repartidores de LogiTrack Atom — Sprint 01.
                        Cada envío se registra PENDIENTE con historial inicial. PENDIENTE permite
                        EN_RUTA (con repartidor activo) o CANCELADO; EN_RUTA permite ENTREGADO.
                        ENTREGADO y CANCELADO son terminales. Las escrituras y su historial son atómicos.
                        Peso en kg (7 enteros/3 decimales) y dimensiones en cm (8 enteros/2 decimales),
                        estrictamente positivos. Los errores usan ApiError.
                        """));
    }

    private static ApiResponse error(int status, String description, String message, String... details) {
        Map<String, Object> example = Map.of("timestamp", "2026-10-19T12:00:00Z", "status", status,
                "error", HttpStatus.valueOf(status).getReasonPhrase(), "mensaje", message,
                "detalles", List.of(details));
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))
                        .addExamples("ejemplo", new Example().value(example))));
    }
}
