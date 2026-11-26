package unpsjb.labprog.backend.model;

import java.util.Locale;

/**
 * Quién originó el registro: un proceso del sistema o una persona.
 */
public enum AuditSource {
    SISTEMA,
    USUARIO;

    /**
     * Regla de relleno y de alta: el actor {@code SISTEMA} (y los alias que ya
     * usan los jobs) queda como sistema. Cualquier otro actor es una persona.
     */
    public static AuditSource deActor(String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            return USUARIO;
        }
        String actor = performedBy.trim().toUpperCase(Locale.ROOT);
        if ("SISTEMA".equals(actor) || "SISTEMA_AUTOMATICO".equals(actor) || "SYSTEM_SEED".equals(actor)) {
            return SISTEMA;
        }
        return USUARIO;
    }
}
