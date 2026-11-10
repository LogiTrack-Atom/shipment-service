package com.logitrack_atom.shipment_service.assignment;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Habilita conexión si al menos un transporte está activo, sin expresiones ni credenciales en modo API local. */
public class AssignmentTransportCondition extends AnyNestedCondition {
    /** Evalúa las opciones de transporte durante la lectura de configuración. */
    public AssignmentTransportCondition() { super(ConfigurationPhase.PARSE_CONFIGURATION); }

    /** Opción de publicación independiente del consumidor. */
    @ConditionalOnProperty(prefix = "logitrack.assignment", name = "publisher-enabled", havingValue = "true", matchIfMissing = true)
    static class Publisher { }

    /** Opción de consumo independiente del publicador. */
    @ConditionalOnProperty(prefix = "logitrack.assignment", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
    static class Consumer { }
}
