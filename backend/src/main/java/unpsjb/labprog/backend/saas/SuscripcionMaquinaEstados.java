package unpsjb.labprog.backend.saas;

import java.time.LocalDateTime;

import unpsjb.labprog.backend.model.Suscripcion;

public final class SuscripcionMaquinaEstados {

    private SuscripcionMaquinaEstados() {
    }

    /**
     * Trial vencido pasa a VENCIDA. Período pago vencido pasa a MOROSA.
     * Una suscripción heredada y activa no se vence sola.
     */
    public static EstadoSuscripcion vencerSiCorresponde(Suscripcion suscripcion, LocalDateTime ahora) {
        if (suscripcion == null || suscripcion.getEstado() == null) {
            return null;
        }
        EstadoSuscripcion estado = suscripcion.getEstado();
        if (suscripcion.isHeredada() && estado == EstadoSuscripcion.ACTIVA) {
            return EstadoSuscripcion.ACTIVA;
        }
        if (estado == EstadoSuscripcion.TRIAL
                && suscripcion.getFechaFinTrial() != null
                && ahora.isAfter(suscripcion.getFechaFinTrial())) {
            return EstadoSuscripcion.VENCIDA;
        }
        if (estado == EstadoSuscripcion.ACTIVA
                && suscripcion.getFechaFinPeriodo() != null
                && ahora.isAfter(suscripcion.getFechaFinPeriodo())) {
            return EstadoSuscripcion.MOROSA;
        }
        return estado;
    }

    public static boolean esEscribible(Suscripcion suscripcion, LocalDateTime ahora) {
        EstadoSuscripcion vigente = vencerSiCorresponde(suscripcion, ahora);
        return vigente != null && vigente.permiteEscribir();
    }
}
