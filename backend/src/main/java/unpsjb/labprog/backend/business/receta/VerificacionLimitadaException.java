package unpsjb.labprog.backend.business.receta;

public class VerificacionLimitadaException extends RuntimeException {

    public VerificacionLimitadaException() {
        super("Demasiadas verificaciones. Probá de nuevo en un minuto.");
    }
}
