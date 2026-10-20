package com.logitrack_atom.shipment_service.logging;

import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Registra cambios confirmados usando identificadores y estados, sin datos personales ni cuerpos. */
public final class BusinessLog {
    private static final Logger LOG = LoggerFactory.getLogger(BusinessLog.class);

    private BusinessLog() { }

    public static void envioRegistrado(UUID envioId, EstadoEnvio estado) {
        despuesDeCommit(() -> LOG.info("envio.registrado envioId={} estado={}", envioId, estado));
    }

    public static void repartidorRegistrado(UUID repartidorId, EstadoRepartidor estado) {
        despuesDeCommit(() -> LOG.info("repartidor.registrado repartidorId={} estado={}", repartidorId, estado));
    }

    public static void repartidorAsignado(UUID envioId, UUID repartidorId) {
        despuesDeCommit(() -> LOG.info("envio.repartidor_asignado envioId={} repartidorId={}", envioId, repartidorId));
    }

    public static void estadoCambiado(UUID envioId, EstadoEnvio anterior, EstadoEnvio nuevo) {
        despuesDeCommit(() -> LOG.info("envio.estado_cambiado envioId={} anterior={} nuevo={}", envioId, anterior, nuevo));
    }

    private static void despuesDeCommit(Runnable evento) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            LOG.debug("Log de éxito omitido: no hay una transacción activa");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evento.run();
            }
        });
    }
}
