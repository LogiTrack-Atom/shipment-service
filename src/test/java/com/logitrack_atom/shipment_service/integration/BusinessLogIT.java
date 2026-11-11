package com.logitrack_atom.shipment_service.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.logging.BusinessLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import static com.logitrack_atom.shipment_service.integration.IntegrationFixtures.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba logs únicamente después de confirmar transacciones locales.
 * @author MaxDz
 * @version 0.2.0
 */
class BusinessLogIT extends MySqlIntegrationSupport {
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capturarLogsDeNegocio() {
        logger = (Logger) LoggerFactory.getLogger(BusinessLog.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restaurarLogger() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void noPublicaExitosAntesDelCommitNiTrasRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            shipmentService.registrar(solicitud("rollback-log@example.test"));
            entityManager.flush();
            assertThat(appender.list).isEmpty();
            status.setRollbackOnly();
        });
        comprobarBaseVacia();
        assertThat(appender.list).isEmpty();

        var confirmado = shipmentService.registrar(solicitud("commit-log@example.test"));
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("envio.registrado", confirmado.id().toString(), "PENDIENTE")
                .doesNotContain("commit-log@example.test", "Ana Pérez", "Belgrano", "Alvear");
    }

    @Test
    void registraCambiosConfirmadosConUuidYEstadosSinVolcarLosDatos() {
        var envio = shipmentService.registrar(solicitud("privado@example.test"));
        UUID repartidor = UUID.randomUUID();
        shipmentService.asignarRepartidor(envio.id(), repartidor);
        shipmentService.cambiarEstado(envio.id(), EstadoEnvio.EN_RUTA);
        shipmentService.cambiarEstado(envio.id(), EstadoEnvio.ENTREGADO);
        var cancelado = shipmentService.registrar(solicitud("privado@example.test"));
        shipmentService.cancelar(cancelado.id());

        assertThat(appender.list).hasSize(6).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).doesNotContain("privado@example.test", "Ana Pérez",
                    "Belgrano", "Alvear", "Repartidor privado", "VEHICULO_PRIVADO");
        });
        var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("envio.repartidor_asignado", envio.id().toString(), repartidor.toString()));
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("envio.estado_cambiado", envio.id().toString(), "anterior=EN_RUTA", "nuevo=ENTREGADO"));
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains(cancelado.id().toString(), "anterior=PENDIENTE", "nuevo=CANCELADO"));
    }
}
