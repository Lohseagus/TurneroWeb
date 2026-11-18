package unpsjb.labprog.backend.exception;

import java.util.List;

/**
 * Faltan datos obligatorios para crear la ficha de la cuenta.
 * Los nombres de campo son los del formulario: nombre, apellido, dni, telefono.
 */
public class DatosFichaIncompletosException extends RuntimeException {

    public static final String MENSAJE = "Completá los datos que faltan";

    private final List<String> campos;

    public DatosFichaIncompletosException(List<String> campos) {
        super(MENSAJE);
        this.campos = List.copyOf(campos);
    }

    public List<String> getCampos() {
        return campos;
    }
}
