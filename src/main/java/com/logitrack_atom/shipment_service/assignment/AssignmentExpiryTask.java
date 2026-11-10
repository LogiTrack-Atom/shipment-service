package com.logitrack_atom.shipment_service.assignment;

import org.springframework.scheduling.annotation.Scheduled;

/** Materializa vencimientos con locks locales breves; la vigencia también se controla al leer/aplicar. */
public class AssignmentExpiryTask {
    private final AssignmentResultsService results;

    /**
     * Recibe la lógica transaccional independiente del transporte.
     * @param results aplicación y expiración de operaciones
     */
    public AssignmentExpiryTask(AssignmentResultsService results) { this.results = results; }

    /** Procesa como máximo cincuenta pendientes por ciclo sin esperar AMQP. */
    @Scheduled(fixedDelayString = "${logitrack.assignment.expiry-delay-ms:5000}", initialDelayString = "${logitrack.assignment.expiry-delay-ms:5000}")
    public void expirePending() { results.expireDue(); }
}
