package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RutasEscrituraSaasTest {

    @Test
    void lasLecturasNoSonEscrituras() {
        assertTrue(RutasEscrituraSaas.esLectura("GET"));
        assertTrue(RutasEscrituraSaas.esLectura("head"));
        assertTrue(RutasEscrituraSaas.esLectura("OPTIONS"));
        assertFalse(RutasEscrituraSaas.esLectura("POST"));
        assertFalse(RutasEscrituraSaas.esLectura("PUT"));
        assertFalse(RutasEscrituraSaas.esLectura("PATCH"));
        assertFalse(RutasEscrituraSaas.esLectura("DELETE"));
    }

    @Test
    void laListaBlancaCubreSesionCobroYConsultas() {
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/auth/login"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/auth/refresh"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/auth/logout"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/auth/change-password"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/saas/registro"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/saas/cotizar"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/saas/webhooks/mercadopago"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/saas/mi-suscripcion/pagar"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/saas/mi-suscripcion/cambiar-plan"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/saas/admin/suscripciones/4/suspender"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/recetas/verificar/abc"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/ordenes-estudio/verificar/abc"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/turno/search"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/turno/filters/simple"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/turno/historial/12/buscar"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/turno/export/pdf"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/export/turnos/csv"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/deep-links/validate"));
        assertTrue(RutasEscrituraSaas.estaPermitida("/api/esquema-turno/validar-conflictos/"));
        assertTrue(RutasEscrituraSaas.estaPermitida(
                "/api/staff-medico/centrosAtencion/3/medicos/porcentajes/validar"));
    }

    @Test
    void unaEscrituraDelCentroNoEstaEnLaListaBlanca() {
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/turno"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/turno/asignar"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/turno/9/cancelar"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/turno/9/confirmar"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/turno/9/completar"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/turno/9/reagendar"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/pacientes"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/hce/consultas"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/recetas"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/ordenes-estudio"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/lista-espera"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/disponibilidades-medico"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/esquema-turno"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/centrosAtencion"));
        assertFalse(RutasEscrituraSaas.estaPermitida("/api/saas/otra-cosa"));
    }
}
