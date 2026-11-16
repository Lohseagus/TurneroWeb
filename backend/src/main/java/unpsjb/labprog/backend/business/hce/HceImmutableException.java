package unpsjb.labprog.backend.business.hce;

/**
 * La evolución ya fue firmada (cerrada). Ley 26.529: no se reescribe;
 * la corrección entra como enmienda.
 */
public class HceImmutableException extends RuntimeException {

    public HceImmutableException(String message) {
        super(message);
    }
}
