package com.logitrack_atom.shipment_service.support;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proveedor HTTP loopback controlado, sin dobles del cliente ni de sus credenciales.
 * @author MaxDz
 * @version 0.2.0
 */
public final class CourierStub {
    private static final HttpServer SERVER;
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final AtomicInteger REDIRECTS = new AtomicInteger();
    private static volatile int status = 200;
    private static volatile String body;
    private static volatile String contentType = "application/json";
    private static volatile int delay;
    private static volatile boolean delayBody;
    private static volatile java.util.concurrent.CountDownLatch entered;
    private static volatile java.util.concurrent.CountDownLatch release;
    private static volatile String authorization;
    private static volatile String method;
    private static volatile String path;
    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            SERVER.setExecutor(executor);
            SERVER.createContext("/api/v1/repartidores/", exchange -> {
                REQUESTS.incrementAndGet();
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                method = exchange.getRequestMethod(); path = exchange.getRequestURI().getPath();
                int code = status; int wait = delay; boolean afterHeaders = delayBody;
                var entryGate=entered; var releaseGate=release; String type = contentType; String data = body;
                if (data == null) {
                    String id = path.split("/")[4];
                    data = "{\"repartidorId\":\""+id+"\",\"estado\":\"ACTIVO\",\"disponible\":true}";
                }
                try {
                    if (entryGate != null) { entryGate.countDown(); releaseGate.await(3,java.util.concurrent.TimeUnit.SECONDS); }
                    if (wait > 0 && !afterHeaders) Thread.sleep(wait);
                    if (type != null) exchange.getResponseHeaders().set("Content-Type", type);
                    exchange.getResponseHeaders().set("Location", baseUrl()+"/destino-no-autorizado");
                    byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(code, code == 204 ? -1 : bytes.length);
                    if (wait > 0 && afterHeaders) Thread.sleep(wait);
                    if (code != 204) exchange.getResponseBody().write(bytes);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.io.IOException ignored) {
                    // El cliente cierra el socket al vencer su timeout, como espera el ensayo.
                } finally { exchange.close(); }
            });
            SERVER.createContext("/destino-no-autorizado", exchange -> {
                REDIRECTS.incrementAndGet(); exchange.sendResponseHeaders(200,-1); exchange.close();
            });
            SERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { SERVER.stop(0); executor.close(); }, "limpieza-courier-fixture"));
        } catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
    private CourierStub() { }
    /** @return origen efímero del servidor controlado */
    public static String baseUrl() { return "http://127.0.0.1:"+SERVER.getAddress().getPort(); }
    /** Restablece respuestas y contadores entre escenarios secuenciales. */
    public static void reset() { status=200;body=null;contentType="application/json";delay=0;delayBody=false;entered=null;release=null;authorization=null;method=null;path=null;REQUESTS.set(0);REDIRECTS.set(0); }
    /**
     * Configura una respuesta inesperada o un rechazo remoto.
     * @param code estado HTTP
     * @param payload cuerpo, o nulo para el contrato ACTIVO del UUID solicitado
     * @param type Content-Type, o nulo para omitirlo
     * @param milliseconds demora antes de enviar cabeceras
     */
    public static void responder(int code,String payload,String type,int milliseconds) { status=code;body=payload;contentType=type;delay=milliseconds; }
    /**
     * Demora el cuerpo después de enviar cabeceras para comprobar el timeout de lectura.
     * @param milliseconds demora de cuerpo en milisegundos
     */
    public static void demorarCuerpo(int milliseconds) { delay=milliseconds;delayBody=true; }
    /** Detiene una respuesta hasta que el ensayo libere la operación concurrente. */
    public static void pausar() { entered=new java.util.concurrent.CountDownLatch(1);release=new java.util.concurrent.CountDownLatch(1); }
    /**
     * Espera una consulta en curso sin depender de sleeps de preparación.
     * @return verdadero si la consulta alcanzó la barrera
     * @throws InterruptedException si se interrumpe la espera del ensayo
     */
    public static boolean esperarConsulta() throws InterruptedException { return entered.await(3,java.util.concurrent.TimeUnit.SECONDS); }
    /** Libera la respuesta del proveedor controlado. */
    public static void continuar() { release.countDown(); }
    /** @return número de consultas recibidas */
    public static int requests() { return REQUESTS.get(); }
    /** @return solicitudes recibidas por el destino de una redirección */
    public static int redirects() { return REDIRECTS.get(); }
    /** @return cabecera recibida, disponible solo dentro del proceso de prueba */
    public static String authorization() { return authorization; }
    /** @return método utilizado por el cliente real */
    public static String method() { return method; }
    /** @return ruta recibida por el proveedor controlado */
    public static String path() { return path; }
}
