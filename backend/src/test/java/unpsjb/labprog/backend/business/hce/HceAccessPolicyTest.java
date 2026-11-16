package unpsjb.labprog.backend.business.hce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.hce.EstadoConsulta;

class HceAccessPolicyTest {

    @Test
    void pacienteSoloLeeLoPropio() {
        assertEquals(HceAccessPolicy.Nivel.CLINICO, HceAccessPolicy.nivel(Role.PACIENTE, true));
        assertEquals(HceAccessPolicy.Nivel.DENEGADO, HceAccessPolicy.nivel(Role.PACIENTE, false));
    }

    @Test
    void rolesAdministrativosNoVenContenidoClinico() {
        assertEquals(HceAccessPolicy.Nivel.METADATOS, HceAccessPolicy.nivel(Role.OPERADOR, false));
        assertEquals(HceAccessPolicy.Nivel.METADATOS, HceAccessPolicy.nivel(Role.ADMINISTRADOR, false));
        assertEquals(HceAccessPolicy.Nivel.METADATOS, HceAccessPolicy.nivel(Role.SUPERADMIN, false));
    }

    @Test
    void medicoDeOtroCentroNecesitaConsentimiento() {
        assertTrue(HceAccessPolicy.medicoPuedeLeer(true, false, false));
        assertTrue(HceAccessPolicy.medicoPuedeLeer(false, true, false));
        assertFalse(HceAccessPolicy.medicoPuedeLeer(false, false, false));
        assertTrue(HceAccessPolicy.medicoPuedeLeer(false, false, true));
    }

    @Test
    void crearExigeStaffYVinculo() {
        assertTrue(HceAccessPolicy.medicoPuedeCrear(true, true));
        assertFalse(HceAccessPolicy.medicoPuedeCrear(true, false));
        assertFalse(HceAccessPolicy.medicoPuedeCrear(false, true));
    }

    @Test
    void cerradaEsInmutableYLaEnmiendaExigeCierre() {
        assertThrows(HceImmutableException.class, () -> ConsultaLifecycle.assertBorrador(EstadoConsulta.CERRADA));
        assertThrows(IllegalStateException.class, () -> ConsultaLifecycle.assertCerradaParaEnmienda(EstadoConsulta.BORRADOR));
        ConsultaLifecycle.assertBorrador(EstadoConsulta.BORRADOR);
        ConsultaLifecycle.assertCerradaParaEnmienda(EstadoConsulta.CERRADA);
    }

    @Test
    void imc() {
        assertEquals(24.22, ConsultaLifecycle.calcularImc(70.0, 170.0));
        assertEquals(null, ConsultaLifecycle.calcularImc(null, 170.0));
    }
}
