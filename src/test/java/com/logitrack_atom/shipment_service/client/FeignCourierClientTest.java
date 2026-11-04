package com.logitrack_atom.shipment_service.client;

import com.logitrack_atom.shipment_service.exception.CourierNoDisponibleException;
import com.logitrack_atom.shipment_service.security.CourierBearerProvider;
import com.logitrack_atom.courier_feign.CourierFeignConfiguration;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.Retryer;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Comprueba rechazo controlado cuando no existe un proveedor TCP disponible.
 * @author MaxDz
 * @version 0.3.0
 */
class FeignCourierClientTest {
    @Test
    void elDecodificadorDeErroresNoLeeNiCopiaElCuerpoPrivado() throws Exception {
        var request=Request.create(Request.HttpMethod.GET,"http://courier-service/consulta",Map.of(),null,
                java.nio.charset.StandardCharsets.UTF_8,null);
        var body=new InputStream() {
            @Override public int read() { throw new AssertionError("No debe leer un cuerpo HTTP de error."); }
        };
        try (var response=Response.builder().request(request).status(500).headers(Map.of()).body(body,null).build()) {
            var exception=new CourierFeignConfiguration().courierErrorDecoder().decode("consultar",response);
            assertThat(exception).isInstanceOf(FeignException.class);
            assertThat(((FeignException)exception).status()).isEqualTo(500);
        }
    }

    @Test
    void conexionRechazadaSeTraduceA503SinExponerCredenciales() throws Exception {
        int port;
        try (var socket=new ServerSocket(0,0,java.net.InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
        var credentials=mock(CourierBearerProvider.class);
        when(credentials.obtener()).thenReturn("credencial-sintetica-no-publicable");
        var api=Feign.builder().contract(new SpringMvcContract()).decoder(new CourierAvailabilityDecoder())
                .options(new Request.Options(2,TimeUnit.SECONDS,3,TimeUnit.SECONDS,false))
                .retryer(Retryer.NEVER_RETRY)
                .target(CourierAvailabilityApi.class,"http://127.0.0.1:"+port);
        var client=new FeignCourierClient(api,credentials);
        assertThatThrownBy(()->client.verificarDisponible(UUID.randomUUID()))
                .isInstanceOf(CourierNoDisponibleException.class)
                .hasMessageNotContaining("credencial-sintetica-no-publicable").hasCause(null);
    }
}
