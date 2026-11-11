package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.courier_feign.CourierFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.UUID;

/**
 * Contrato declarativo de disponibilidad, resuelto por nombre mediante LoadBalancer.
 *
 * <p>No define URL ni recibe entidades del proveedor. El adaptador entrega únicamente
 * el Bearer obtenido del contexto autenticado después de las comprobaciones locales.</p>
 *
 * @author MaxDz
 * @version 0.3.0
 */
@FeignClient(name = "courier-service", contextId = "courierAvailability",
        configuration = CourierFeignConfiguration.class)
public interface CourierAvailabilityApi {
    /**
     * Consulta el estado observado sin reservar ni modificar al repartidor.
     *
     * @param repartidorId identificador remoto a consultar
     * @param authorization Bearer validado por el servicio de envíos
     * @return DTO mínimo decodificado, sujeto a validación de coherencia en el adaptador
     */
    @GetMapping(value = "/api/v1/repartidores/{id}/disponibilidad", produces = "application/json")
    DisponibilidadCourier consultar(@PathVariable("id") UUID repartidorId,
                                   @RequestHeader("Authorization") String authorization);
}
