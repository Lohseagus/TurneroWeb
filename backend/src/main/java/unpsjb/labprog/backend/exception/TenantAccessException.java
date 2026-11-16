package unpsjb.labprog.backend.exception;

/**
 * El usuario autenticado intenta operar un recurso de otro centro.
 * El presenter la traduce a 403.
 */
public class TenantAccessException extends RuntimeException {

    public TenantAccessException(String message) {
        super(message);
    }
}
