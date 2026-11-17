package unpsjb.labprog.backend.exception;

/**
 * El usuario autenticado tiene rol MEDICO y no existe una fila Medico con su email.
 */
public class MedicoSinFichaException extends RuntimeException {

    public static final String MENSAJE =
            "Tu usuario tiene rol de médico, pero no hay una ficha profesional vinculada. "
                    + "Pedile a un administrador del centro que te asocie.";

    public MedicoSinFichaException() {
        super(MENSAJE);
    }
}
