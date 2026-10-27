package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.config.CourierClientProperties;
import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.security.CourierBearerProvider;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Comprueba rechazo controlado cuando no existe un proveedor TCP disponible.
 * @author MaxDz
 * @version 0.2.0
 */
class RestCourierClientTest {
    @Test
    void conexionRechazadaSeTraduceA503SinExponerCredenciales() throws Exception {
        int port;
        try (var socket=new ServerSocket(0,0,java.net.InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
        var credentials=mock(CourierBearerProvider.class);
        when(credentials.obtener()).thenReturn("credencial-sintetica-no-publicable");
        var properties=new CourierClientProperties(URI.create("http://127.0.0.1:"+port),Duration.ofSeconds(2),Duration.ofSeconds(3));
        var client=new RestCourierClient(properties,credentials);
        assertThatThrownBy(()->client.verificarDisponible(UUID.randomUUID()))
                .isInstanceOf(CourierNoDisponibleException.class)
                .hasMessageNotContaining("credencial-sintetica-no-publicable").hasCause(null);
    }
}
