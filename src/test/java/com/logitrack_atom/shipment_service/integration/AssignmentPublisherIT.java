package com.logitrack_atom.shipment_service.integration;

import static org.assertj.core.api.Assertions.*;

import com.logitrack_atom.shipment_service.assignment.AssignmentMessagingConfig;
import com.logitrack_atom.shipment_service.assignment.AssignmentPublisher;
import com.logitrack_atom.shipment_service.assignment.AssignmentService;
import com.logitrack_atom.shipment_service.assignment.OutboxStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;

/** Publicador Java real con MySQL y RabbitMQ: confirm, mandatory return y recuperación sin reiniciar la app. */
@SpringBootTest(properties = {"logitrack.assignment.enabled=true", "logitrack.assignment.publisher-enabled=false", "logitrack.assignment.consumer-enabled=false"})
class AssignmentPublisherIT extends MySqlIntegrationSupport {
    @Autowired AssignmentService assignments;
    @Autowired OutboxStore outbox;

    @Test
    void confirmsReturnsAndRecoversPersistedOutboxAfterBrokerRestart() throws Exception {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_operations'", Integer.class) == 0)
            for (String sql : Files.readString(Path.of("docker/mysql/mq/004-assignment-outbox.sql")).split(";"))
                if (!sql.isBlank()) jdbc.execute(sql);
        String password = UUID.randomUUID().toString();
        int stablePort;
        try (var socket = new java.net.ServerSocket(0)) { stablePort = socket.getLocalPort(); }
        try (var broker = new GenericContainer<>("rabbitmq:4.4.0-management@sha256:af6f50d38098d8bd52fef7f3fa85015dd8b2bf6def58e995bf584ae9ba0f6407")
                .withEnv("RABBITMQ_DEFAULT_USER", "assignment_test").withEnv("RABBITMQ_DEFAULT_PASS", password)
                .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "+S 2:2").withExposedPorts(5672)
                .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                        new com.github.dockerjava.api.model.PortBinding(
                                com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1", stablePort),
                                com.github.dockerjava.api.model.ExposedPort.tcp(5672))))) {
            broker.start();
            var factory = new AssignmentMessagingConfig().assignmentConnectionFactory(broker.getHost(), broker.getMappedPort(5672), "/", "assignment_test", password);
            try {
                var template = new AssignmentMessagingConfig().assignmentRabbitTemplate(factory);
                var publisher = new AssignmentPublisher(outbox, template);
                String queue = "lt.courier.assignment.v1";
                try (var connection = factory.createConnection(); var channel = connection.createChannel(false)) {
                    channel.exchangeDeclare("lt.assignment.commands.v1", "direct", true);
                    channel.queueDeclare(queue, true, false, false, Map.of("x-queue-type", "quorum"));
                    channel.queueBind(queue, "lt.assignment.commands.v1", "availability.request");
                }
                UUID envio = shipmentService.registrar(IntegrationFixtures.solicitud("publisher@example.test")).id();
                var first = assignments.accept(envio, UUID.randomUUID(), UUID.randomUUID().toString());
                publisher.publishPending();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM amqp_outbox WHERE published_at IS NOT NULL", Integer.class)).isEqualTo(1);
                try (var connection = factory.createConnection(); var channel = connection.createChannel(false)) {
                    var received = channel.basicGet(queue, true);
                    assertThat(received).isNotNull();
                    assertThat(received.getProps().getCorrelationId()).isEqualTo(first.operationId().toString());
                    assertThat(new String(received.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                            .contains("assignment.availability.requested.v1").doesNotContain("Bearer", "password", "email");
                    channel.queueUnbind(queue, "lt.assignment.commands.v1", "availability.request");
                }
                assignments.accept(envio, UUID.randomUUID(), UUID.randomUUID().toString());
                publisher.publishPending();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM amqp_outbox WHERE published_at IS NULL", Integer.class)).isEqualTo(1);
                try (var connection = factory.createConnection(); var channel = connection.createChannel(false)) {
                    channel.queueBind(queue, "lt.assignment.commands.v1", "availability.request");
                }
                jdbc.update("UPDATE amqp_outbox SET next_attempt_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE published_at IS NULL");
                publisher.publishPending();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM amqp_outbox WHERE published_at IS NULL", Integer.class)).isZero();
                broker.getDockerClient().stopContainerCmd(broker.getContainerId()).withTimeout(2).exec();
                var pending = assignments.accept(envio, UUID.randomUUID(), UUID.randomUUID().toString());
                publisher.publishPending();
                assertThat(assignments.find(pending.operationId()).status()).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM amqp_outbox WHERE published_at IS NULL", Integer.class)).isEqualTo(1);
                broker.getDockerClient().startContainerCmd(broker.getContainerId()).exec();
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
                boolean ready = false;
                while (System.nanoTime() < deadline) {
                    try (var connection = factory.createConnection()) { ready = true; break; }
                    catch (org.springframework.amqp.AmqpException unavailable) { Thread.sleep(1000); }
                }
                assertThat(ready).isTrue();
                jdbc.update("UPDATE amqp_outbox SET next_attempt_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE published_at IS NULL");
                publisher.publishPending();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM amqp_outbox WHERE published_at IS NULL", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT MAX(attempts) FROM amqp_outbox", Integer.class)).isGreaterThanOrEqualTo(2);
            } finally { factory.destroy(); }
        }
    }
}
