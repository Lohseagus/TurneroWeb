package unpsjb.labprog.backend.exception;

/**
 * Un registro de auditoría ya persistido no se edita ni se borra.
 */
public class AuditInmutableException extends RuntimeException {

    public AuditInmutableException(String message) {
        super(message);
    }
}
