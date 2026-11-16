package unpsjb.labprog.backend.saas;

/**
 * Estados de la suscripción del tenant.
 * VENCIDA es el trial vencido sin pago. MOROSA es una suscripción paga cuyo período expiró.
 * En ambos casos, y también en SUSPENDIDA y CANCELADA, la cuenta queda de solo lectura.
 */
public enum EstadoSuscripcion {
    TRIAL,
    ACTIVA,
    VENCIDA,
    MOROSA,
    SUSPENDIDA,
    CANCELADA;

    public boolean permiteEscribir() {
        return this == TRIAL || this == ACTIVA;
    }
}
