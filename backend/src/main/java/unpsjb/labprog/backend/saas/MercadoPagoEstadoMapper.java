package unpsjb.labprog.backend.saas;

/**
 * Traduce los estados de Mercado Pago Subscriptions a los de CheTurno.
 * Preapproval: authorized, paused, cancelled, pending.
 */
public final class MercadoPagoEstadoMapper {

    private MercadoPagoEstadoMapper() {
    }

    public static EstadoSuscripcion desdePreapproval(String estadoMp, EstadoSuscripcion actual) {
        if (estadoMp == null || estadoMp.isBlank()) {
            return actual;
        }
        return switch (estadoMp.trim().toLowerCase()) {
            case "authorized" -> EstadoSuscripcion.ACTIVA;
            case "paused" -> EstadoSuscripcion.SUSPENDIDA;
            case "cancelled", "canceled" -> EstadoSuscripcion.CANCELADA;
            case "pending" -> actual == null ? EstadoSuscripcion.TRIAL : actual;
            default -> actual;
        };
    }

    public static EstadoPago desdePago(String estadoMp) {
        if (estadoMp == null || estadoMp.isBlank()) {
            return EstadoPago.PENDIENTE;
        }
        return switch (estadoMp.trim().toLowerCase()) {
            case "approved", "authorized" -> EstadoPago.APROBADO;
            case "rejected", "cancelled", "canceled" -> EstadoPago.RECHAZADO;
            case "refunded", "charged_back" -> EstadoPago.REEMBOLSADO;
            default -> EstadoPago.PENDIENTE;
        };
    }
}
