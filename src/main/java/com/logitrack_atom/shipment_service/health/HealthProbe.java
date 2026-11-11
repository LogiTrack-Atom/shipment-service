package com.logitrack_atom.shipment_service.health;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Comprueba la salud del contenedor utilizando únicamente la JRE disponible.
 *
 * <p>Consulta la API local sin credenciales, limita conexión y lectura y no
 * sigue redirecciones. No necesita instalar herramientas de red en el runtime.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
public final class HealthProbe {
    /** Impide instanciar la utilidad de comprobación. */
    private HealthProbe() { }

    /**
     * Devuelve al motor de contenedores un código cero únicamente ante salud HTTP 200.
     * @param args argumentos no utilizados; la API interna acordada usa el puerto 8080
     */
    public static void main(String[] args) { System.exit(healthy() ? 0 : 1); }

    /**
     * Realiza una consulta acotada a loopback sin imprimir respuestas o configuración.
     * @return true si el endpoint de salud está disponible y responde 200
     */
    private static boolean healthy() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:8080/actuator/health").toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException ex) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
