package com.logitrack_atom.shipment_service.assignment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Detecta el esquema aditivo sin omitir guardas al deshabilitar MQ o migrar otra instancia. */
@Component
public class AssignmentSchema {
    private final JdbcTemplate jdbc;
    private volatile boolean installed;

    /**
     * Recibe acceso solo a metadatos de la base local.
     * @param jdbc conexión de aplicación
     */
    public AssignmentSchema(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Memoriza únicamente la presencia; un negativo se vuelve a consultar después de una migración.
     * @return verdadero si existen operación y columnas de generación/puntero
     */
    public boolean installed() {
        if (installed) return true;
        boolean present = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='envios' AND column_name IN ('assignment_version','active_assignment_operation_id'))=2
                AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_operations')
                """, Boolean.class);
        if (present) installed = true;
        return present;
    }
}
