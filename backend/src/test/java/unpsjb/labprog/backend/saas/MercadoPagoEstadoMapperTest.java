package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.saas.pasarela.EventoPasarela;
import unpsjb.labprog.backend.saas.pasarela.MercadoPagoNotificacionParser;
import unpsjb.labprog.backend.saas.pasarela.TipoNotificacionPasarela;

class MercadoPagoEstadoMapperTest {

    @Test
    void mapeaEstadosDePreapproval() {
        assertEquals(EstadoSuscripcion.ACTIVA,
                MercadoPagoEstadoMapper.desdePreapproval("authorized", EstadoSuscripcion.TRIAL));
        assertEquals(EstadoSuscripcion.SUSPENDIDA,
                MercadoPagoEstadoMapper.desdePreapproval("paused", EstadoSuscripcion.ACTIVA));
        assertEquals(EstadoSuscripcion.CANCELADA,
                MercadoPagoEstadoMapper.desdePreapproval("cancelled", EstadoSuscripcion.ACTIVA));
        assertEquals(EstadoSuscripcion.TRIAL,
                MercadoPagoEstadoMapper.desdePreapproval("pending", EstadoSuscripcion.TRIAL));
        assertEquals(EstadoPago.APROBADO, MercadoPagoEstadoMapper.desdePago("approved"));
        assertEquals(EstadoPago.RECHAZADO, MercadoPagoEstadoMapper.desdePago("rejected"));
        assertEquals(EstadoPago.PENDIENTE, MercadoPagoEstadoMapper.desdePago("pending"));
    }

    @Test
    void parserLeePreapprovalYPago() {
        EventoPasarela preapproval = MercadoPagoNotificacionParser.parsear("""
                {"type":"subscription_preapproval","data":{"id":"pre-1"},"status":"authorized"}
                """);
        assertEquals(TipoNotificacionPasarela.PREAPPROVAL, preapproval.tipo());
        assertEquals("pre-1", preapproval.recursoId());
        assertEquals("authorized", preapproval.estadoMp());

        EventoPasarela pago = MercadoPagoNotificacionParser.parsear("""
                {"topic":"payment","data":{"id":"pay-9"},"status":"approved","external_reference":"suscripcion-4"}
                """);
        assertEquals(TipoNotificacionPasarela.PAYMENT, pago.tipo());
        assertEquals("pay-9", pago.recursoId());
        assertEquals("suscripcion-4", pago.externalReference());
    }
}
