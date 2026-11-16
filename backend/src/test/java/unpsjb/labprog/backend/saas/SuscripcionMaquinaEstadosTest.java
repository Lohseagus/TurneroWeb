package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.Suscripcion;

class SuscripcionMaquinaEstadosTest {

    private final LocalDateTime ahora = LocalDateTime.of(2026, 6, 15, 10, 0);

    @Test
    void trialVigentePermiteEscribirYVencidoPasaASoloLectura() {
        Suscripcion trial = suscripcion(EstadoSuscripcion.TRIAL);
        trial.setFechaFinTrial(ahora.plusDays(30));
        assertEquals(EstadoSuscripcion.TRIAL, SuscripcionMaquinaEstados.vencerSiCorresponde(trial, ahora));
        assertTrue(SuscripcionMaquinaEstados.esEscribible(trial, ahora));

        trial.setFechaFinTrial(ahora.minusMinutes(1));
        assertEquals(EstadoSuscripcion.VENCIDA, SuscripcionMaquinaEstados.vencerSiCorresponde(trial, ahora));
        assertFalse(SuscripcionMaquinaEstados.esEscribible(trial, ahora));
    }

    @Test
    void periodoPagoVencidoPasaAMorosaYLaHeredadaNo() {
        Suscripcion paga = suscripcion(EstadoSuscripcion.ACTIVA);
        paga.setFechaFinPeriodo(ahora.minusDays(1));
        assertEquals(EstadoSuscripcion.MOROSA, SuscripcionMaquinaEstados.vencerSiCorresponde(paga, ahora));

        Suscripcion heredada = suscripcion(EstadoSuscripcion.ACTIVA);
        heredada.setHeredada(true);
        heredada.setFechaFinPeriodo(ahora.minusYears(1));
        assertEquals(EstadoSuscripcion.ACTIVA, SuscripcionMaquinaEstados.vencerSiCorresponde(heredada, ahora));
        assertTrue(SuscripcionMaquinaEstados.esEscribible(heredada, ahora));
    }

    @Test
    void suspendidaYCanceladaNoEscriben() {
        assertFalse(SuscripcionMaquinaEstados.esEscribible(suscripcion(EstadoSuscripcion.SUSPENDIDA), ahora));
        assertFalse(SuscripcionMaquinaEstados.esEscribible(suscripcion(EstadoSuscripcion.CANCELADA), ahora));
        assertFalse(SuscripcionMaquinaEstados.esEscribible(suscripcion(EstadoSuscripcion.MOROSA), ahora));
    }

    private Suscripcion suscripcion(EstadoSuscripcion estado) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setEstado(estado);
        return suscripcion;
    }
}
