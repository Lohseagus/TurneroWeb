package unpsjb.labprog.backend.saas;

/**
 * Regla de negocio de la suscripción (límite de plan o cuenta de solo lectura).
 * Extiende IllegalArgumentException para que los presenters existentes la devuelvan
 * como error de la API sin borrar datos.
 */
public class ReglaSuscripcionException extends IllegalArgumentException {

    public ReglaSuscripcionException(String message) {
        super(message);
    }
}
