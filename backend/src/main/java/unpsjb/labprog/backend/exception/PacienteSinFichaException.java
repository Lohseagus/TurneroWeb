package unpsjb.labprog.backend.exception;

/**
 * La cuenta autenticada no tiene una fila Paciente con su email.
 */
public class PacienteSinFichaException extends RuntimeException {

    public static final String MENSAJE =
            "Tu usuario puede ver el panel de paciente, pero no hay una ficha de paciente vinculada. "
                    + "Pedile a un administrador del centro que te asocie.";

    public PacienteSinFichaException() {
        super(MENSAJE);
    }

    public PacienteSinFichaException(String message) {
        super(message == null || message.isBlank() ? MENSAJE : message);
    }
}
