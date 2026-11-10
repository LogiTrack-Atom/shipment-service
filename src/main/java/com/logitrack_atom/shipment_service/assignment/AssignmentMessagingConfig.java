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
@ConditionalOnProperty(prefix = "logitrack.assignment", name = "enabled", havingValue = "true")
@org.springframework.context.annotation.Conditional(AssignmentTransportCondition.class)
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
    @ConditionalOnProperty(prefix = "logitrack.assignment", name = "publisher-enabled", havingValue = "true")
    public AssignmentPublisher assignmentPublisher(OutboxStore outbox, RabbitTemplate assignmentRabbitTemplate) {
        outbox.ensureSchemaReady();
        return new AssignmentPublisher(outbox, assignmentRabbitTemplate);
    }

    /**
     * Construye el receptor de resultados con ACK posterior al commit local.
     * @param results guardas y deduplicación de resultados
     * @param mapper parser JSON
     * @param assignmentRabbitTemplate retry confirmado
     * @return límite de transporte de resultados
     */
    @Bean
    public AssignmentResultListener assignmentResultListener(AssignmentResultsService results,
            tools.jackson.databind.ObjectMapper mapper, RabbitTemplate assignmentRabbitTemplate) {
        return new AssignmentResultListener(results, mapper, assignmentRabbitTemplate);
    }

    /**
     * Activa consumo manual sin declarar colas ni incluir el broker en salud del portal.
     * @param assignmentConnectionFactory usuario de servicio Shipment
     * @param assignmentResultListener procesamiento protegido
     * @param results comprobación de la migración de inbox
     * @return consumidor recuperable del resultado v1
     */
    @Bean
    @ConditionalOnProperty(prefix = "logitrack.assignment", name = "consumer-enabled", havingValue = "true")
    public org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer assignmentResultContainer(
            CachingConnectionFactory assignmentConnectionFactory, AssignmentResultListener assignmentResultListener, AssignmentResultsService results) {
        results.ensureSchemaReady();
        var container = new org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer(assignmentConnectionFactory);
        container.setQueueNames("lt.shipment.assignment.v1");
        container.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        container.setPrefetchCount(10);container.setConcurrentConsumers(1);
        container.setAutoDeclare(false);container.setMissingQueuesFatal(false);
        container.setDefaultRequeueRejected(false);container.setRecoveryInterval(5000);
        container.setMessageListener(assignmentResultListener);
        return container;
    }

}
