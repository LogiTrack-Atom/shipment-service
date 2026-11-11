package com.logitrack_atom.shipment_service.integration;

import com.logitrack_atom.shipment_service.dto.request.ClienteRequest;
import com.logitrack_atom.shipment_service.dto.request.DireccionRequest;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Direccion;
import com.logitrack_atom.shipment_service.entity.Envio;

import java.math.BigDecimal;

final class IntegrationFixtures {
    private IntegrationFixtures() { }

    static EnvioRequest solicitud(String email) {
        return new EnvioRequest(new ClienteRequest("Ana Pérez", email, null),
                new DireccionRequest("Belgrano", "100", "Jujuy", "Y4600"),
                new DireccionRequest("Alvear", "200", "Salta", "A4400"),
                new BigDecimal("1.250"), new BigDecimal("25.25"),
                new BigDecimal("15.50"), new BigDecimal("30.75"));
    }

    static Cliente cliente(String email) {
        Cliente cliente = new Cliente();
        cliente.setNombre("Ana Pérez");
        cliente.setEmail(email);
        return cliente;
    }

    static Envio envio(Cliente cliente, String tracking) {
        Envio envio = new Envio();
        envio.setCliente(cliente);
        envio.setCodigoSeguimiento(tracking);
        envio.setOrigen(new Direccion("Belgrano", "100", "Jujuy", "Y4600"));
        envio.setDestino(new Direccion("Alvear", "200", "Salta", "A4400"));
        envio.setPesoKg(new BigDecimal("1.250"));
        envio.setAnchoCm(new BigDecimal("25.25"));
        envio.setAltoCm(new BigDecimal("15.50"));
        envio.setLargoCm(new BigDecimal("30.75"));
        return envio;
    }

    static String envioJson(String peso, String ancho, String alto, String largo) {
        return """
                {
                  "cliente": {"nombre": "Ana Pérez", "email": "ana@example.test"},
                  "origen": {"calle": "Belgrano", "numero": "100", "ciudad": "Jujuy", "codigoPostal": "Y4600"},
                  "destino": {"calle": "Alvear", "numero": "200", "ciudad": "Salta", "codigoPostal": "A4400"},
                  "pesoKg": %s, "anchoCm": %s, "altoCm": %s, "largoCm": %s
                }
                """.formatted(peso, ancho, alto, largo);
    }

    static String envioJson() {
        return envioJson("1.250", "25.25", "15.50", "30.75");
    }
}
