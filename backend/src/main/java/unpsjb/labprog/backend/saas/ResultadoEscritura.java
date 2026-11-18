package unpsjb.labprog.backend.saas;

/**
 * Decisión de la regla de solo lectura para un centro.
 * {@code sinSuscripcion} distingue "no hay fila" de "la fila está vencida, morosa, suspendida o cancelada".
 */
public record ResultadoEscritura(boolean permite, String mensaje, boolean sinSuscripcion) {

    public static ResultadoEscritura permitir() {
        return new ResultadoEscritura(true, null, false);
    }

    public static ResultadoEscritura bloquear(String mensaje, boolean sinSuscripcion) {
        return new ResultadoEscritura(false, mensaje, sinSuscripcion);
    }
}
