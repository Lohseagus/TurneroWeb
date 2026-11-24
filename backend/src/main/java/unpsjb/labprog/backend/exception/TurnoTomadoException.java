package unpsjb.labprog.backend.exception;

/**
 * Dos reservas llegaron al mismo horario activo. La respuesta al cliente es 409.
 */
public class TurnoTomadoException extends RuntimeException {

    public static final String MENSAJE = "Ese turno ya fue tomado";

    public TurnoTomadoException() {
        super(MENSAJE);
    }
}
