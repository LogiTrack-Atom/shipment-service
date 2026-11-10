package com.logitrack_atom.shipment_service.assignment;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa transporte solo con opt-in; no declara colas ni agrega salud AMQP al núcleo. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "logitrack.assignment", name = {"enabled", "publisher-enabled"}, havingValue = "true")
public class AssignmentMessagingConfig {
    /** Crea la configuración opcional sin abrir conexiones durante su construcción. */
    public AssignmentMessagingConfig() { }

    /**
     * Configura una conexión con el usuario restringido de Shipment.
     * @param host DNS privado del broker
     * @param port puerto AMQP privado
     * @param vhost vhost de la ampliación
     * @param user usuario restringido de Shipment
     * @param password credencial externa obligatoria al activar publicación
     * @return fábrica sin RabbitAdmin ni declaración automática de topología
     */
    @Bean(destroyMethod = "destroy")
    public CachingConnectionFactory assignmentConnectionFactory(
            @Value("${MQ_HOST:rabbitmq}") String host, @Value("${MQ_PORT:5672}") int port,
            @Value("${MQ_VHOST:logitrack-s04}") String vhost,
            @Value("${MQ_SHIPMENT_USER:lt_shipment}") String user,
            @Value("${MQ_SHIPMENT_PASSWORD:}") String password) {
        if (password.isBlank()) throw new IllegalArgumentException("Configurar credencial externa de Shipment para MQ");
        var factory = new CachingConnectionFactory(host, port);
        factory.setVirtualHost(vhost);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setConnectionTimeout(3000);
        factory.getRabbitConnectionFactory().setRequestedHeartbeat(15);
        factory.getRabbitConnectionFactory().setChannelRpcTimeout(5000);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    /**
     * Configura publicación confirmada y devolución de mensajes sin ruta.
     * @param assignmentConnectionFactory fábrica privada
     * @return publicador con mandatory activo
     */
    @Bean
    public RabbitTemplate assignmentRabbitTemplate(CachingConnectionFactory assignmentConnectionFactory) {
        var template = new RabbitTemplate(assignmentConnectionFactory);
        template.setMandatory(true);
        return template;
    }

    /**
     * Recibe acceso SQL y transacciones locales.
     * @param outbox mensajes locales
     * @param assignmentRabbitTemplate transporte confirmado
     * @return tarea desacoplada
     */
    @Bean
    public AssignmentPublisher assignmentPublisher(OutboxStore outbox, RabbitTemplate assignmentRabbitTemplate) {
        outbox.ensureSchemaReady();
        return new AssignmentPublisher(outbox, assignmentRabbitTemplate);
    }
}
