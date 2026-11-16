package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.PlanSuscripcion;

class LimitesPlanTest {

    @Test
    void profesionalRechazaElSegundoMedicoOperadorYCentro() {
        PlanSuscripcion plan = new PlanSuscripcion();
        plan.setNombre("Profesional");
        plan.setMaxMedicos(1);
        plan.setMaxOperadores(1);
        plan.setMaxCentros(1);
        plan.setMaxConsultorios(1);

        UsoOrganizacion lleno = new UsoOrganizacion(1, 1, 1, 1, 0);
        assertThrows(ReglaSuscripcionException.class,
                () -> LimitesPlan.validarNuevo(plan, lleno, RecursoSaas.MEDICO));
        assertThrows(ReglaSuscripcionException.class,
                () -> LimitesPlan.validarNuevo(plan, lleno, RecursoSaas.OPERADOR));
        assertThrows(ReglaSuscripcionException.class,
                () -> LimitesPlan.validarNuevo(plan, lleno, RecursoSaas.CENTRO));
        assertDoesNotThrow(() -> LimitesPlan.validarNuevo(plan, new UsoOrganizacion(0, 0, 0, 0, 0), RecursoSaas.MEDICO));
    }

    @Test
    void consultorioNoBajaDeDosNiPasaDeDiezAlCambiarDePlan() {
        PlanSuscripcion plan = new PlanSuscripcion();
        plan.setNombre("Consultorio");
        plan.setPrecioPorProfesional(new BigDecimal("18000"));
        plan.setMinProfesionales(2);
        plan.setMaxProfesionales(10);
        plan.setMaxMedicos(10);
        plan.setMaxCentros(1);

        ReglaSuscripcionException error = assertThrows(ReglaSuscripcionException.class,
                () -> LimitesPlan.validarCabeEnPlan(plan, new UsoOrganizacion(12, 0, 0, 1, 0), 12));
        assertTrue(error.getMessage().contains("máximo"));

        assertDoesNotThrow(() -> LimitesPlan.validarCabeEnPlan(plan, new UsoOrganizacion(4, 0, 0, 1, 0), 4));
    }

    @Test
    void centroNoTieneTopeDeMedicosYRedPermiteVariasSedes() {
        PlanSuscripcion centro = new PlanSuscripcion();
        centro.setNombre("Centro");
        centro.setMaxCentros(1);
        assertDoesNotThrow(() -> LimitesPlan.validarNuevo(centro, new UsoOrganizacion(40, 10, 8, 0, 6), RecursoSaas.MEDICO));
        assertThrows(ReglaSuscripcionException.class,
                () -> LimitesPlan.validarNuevo(centro, new UsoOrganizacion(40, 10, 8, 1, 6), RecursoSaas.CENTRO));

        PlanSuscripcion red = new PlanSuscripcion();
        red.setNombre("Red");
        red.setSedesIncluidas(3);
        assertDoesNotThrow(() -> LimitesPlan.validarNuevo(red, new UsoOrganizacion(1, 1, 1, 3, 1), RecursoSaas.CENTRO));
    }
}
