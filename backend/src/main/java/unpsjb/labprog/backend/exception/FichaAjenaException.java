package unpsjb.labprog.backend.exception;

/**
 * El cuerpo pide crear o apuntar una ficha que no es la de la cuenta autenticada.
 */
public class FichaAjenaException extends RuntimeException {

    public static final String MENSAJE = "Solo podés crear tu propia ficha";

    public FichaAjenaException() {
        super(MENSAJE);
    }

    public FichaAjenaException(String message) {
        super(message == null || message.isBlank() ? MENSAJE : message);
    }
}
