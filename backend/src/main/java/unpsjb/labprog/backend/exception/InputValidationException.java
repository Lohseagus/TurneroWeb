package unpsjb.labprog.backend.exception;

/**
 * Datos de entrada inválidos. El presenter la traduce a 400.
 * Los conflictos de unicidad siguen siendo {@link IllegalArgumentException} (409).
 */
public class InputValidationException extends IllegalArgumentException {

    public InputValidationException(String message) {
        super(message);
    }
}
