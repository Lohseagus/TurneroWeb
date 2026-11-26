package unpsjb.labprog.backend.model;

import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

import unpsjb.labprog.backend.exception.AuditInmutableException;

/**
 * Hibernate no emite UPDATE ni DELETE de un {@link AuditLog}.
 * El disparador de la base cubre el SQL que no pasa por JPA.
 */
public class AuditLogGuard {

    public static final String MENSAJE = "Los registros de auditoría no se modifican ni se eliminan";

    @PreUpdate
    @PreRemove
    public void rechazar(Object entidad) {
        throw new AuditInmutableException(MENSAJE);
    }
}
