package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.PlanSuscripcion;

class PlanFeaturesTest {

    @Test
    void elMapaPorCodigoSigueElCatalogo() {
        assertTrue(PlanFeatures.porCodigo(CodigosPlan.PROFESIONAL).isEmpty());
        Set<FeaturePlan> gestion = EnumSet.of(FeaturePlan.REPORTES, FeaturePlan.AUDITORIA, FeaturePlan.ENCUESTAS);
        assertEquals(gestion, PlanFeatures.porCodigo(CodigosPlan.CONSULTORIO));
        assertEquals(gestion, PlanFeatures.porCodigo(CodigosPlan.CENTRO));
        assertEquals(EnumSet.allOf(FeaturePlan.class), PlanFeatures.porCodigo(CodigosPlan.RED));
    }

    @Test
    void conLasBanderasDelSeedQuedaIgualQueElMapa() {
        assertEquals(PlanFeatures.porCodigo(CodigosPlan.PROFESIONAL),
                PlanFeatures.de(plan(CodigosPlan.PROFESIONAL, false, false, false)));
        assertEquals(PlanFeatures.porCodigo(CodigosPlan.CONSULTORIO),
                PlanFeatures.de(plan(CodigosPlan.CONSULTORIO, true, false, false)));
        assertEquals(PlanFeatures.porCodigo(CodigosPlan.CENTRO),
                PlanFeatures.de(plan(CodigosPlan.CENTRO, true, false, false)));
        assertEquals(PlanFeatures.porCodigo(CodigosPlan.RED),
                PlanFeatures.de(plan(CodigosPlan.RED, true, true, true)));
    }

    @Test
    void lasBanderasQueEditaElSuperadminMandanSobreElMapa() {
        PlanSuscripcion profesionalConReportes = plan(CodigosPlan.PROFESIONAL, true, false, false);
        assertTrue(PlanFeatures.incluye(profesionalConReportes, FeaturePlan.REPORTES));
        assertFalse(PlanFeatures.incluye(profesionalConReportes, FeaturePlan.AUDITORIA));

        PlanSuscripcion centroSinReportes = plan(CodigosPlan.CENTRO, false, false, false);
        assertFalse(PlanFeatures.incluye(centroSinReportes, FeaturePlan.REPORTES));
        assertTrue(PlanFeatures.incluye(centroSinReportes, FeaturePlan.AUDITORIA));

        PlanSuscripcion redSinSoporte = plan(CodigosPlan.RED, true, true, false);
        assertFalse(PlanFeatures.incluye(redSinSoporte, FeaturePlan.SOPORTE_PRIORITARIO));
        // Los consolidados implican reportes, como en assertReporteBasico.
        assertTrue(PlanFeatures.incluye(plan(CodigosPlan.RED, false, true, true), FeaturePlan.REPORTES));
    }

    @Test
    void unPlanFueraDelMapaNoPierdeAuditoriaNiEncuestas() {
        Set<FeaturePlan> features = PlanFeatures.de(plan("A_MEDIDA", false, false, false));
        assertEquals(EnumSet.of(FeaturePlan.AUDITORIA, FeaturePlan.ENCUESTAS), features);
        assertTrue(PlanFeatures.de(null).isEmpty());
    }

    @Test
    void elPlanMinimoEsElMasChicoQueLaTrae() {
        assertEquals(CodigosPlan.CONSULTORIO, PlanFeatures.planMinimo(FeaturePlan.REPORTES));
        assertEquals(CodigosPlan.CONSULTORIO, PlanFeatures.planMinimo(FeaturePlan.AUDITORIA));
        assertEquals(CodigosPlan.CONSULTORIO, PlanFeatures.planMinimo(FeaturePlan.ENCUESTAS));
        assertEquals(CodigosPlan.RED, PlanFeatures.planMinimo(FeaturePlan.REPORTES_CONSOLIDADOS));
        assertEquals(CodigosPlan.RED, PlanFeatures.planMinimo(FeaturePlan.SOPORTE_PRIORITARIO));
        assertEquals("Red", PlanFeatures.nombrePlan(CodigosPlan.RED));
    }

    @Test
    void elMensajeDiceEnQuePlanEstaDisponible() {
        String mensaje = PlanFeatures.mensajeNoIncluida(
                plan(CodigosPlan.PROFESIONAL, false, false, false), FeaturePlan.AUDITORIA);
        assertTrue(mensaje.contains("El plan Profesional no incluye"));
        assertTrue(mensaje.contains("Disponible en plan Consultorio"));
    }

    static PlanSuscripcion plan(String codigo, boolean reportes, boolean consolidados, boolean soporte) {
        PlanSuscripcion plan = new PlanSuscripcion();
        plan.setCodigo(codigo);
        plan.setNombre(PlanFeatures.nombrePlan(codigo));
        plan.setReportesBasicos(reportes);
        plan.setReportesConsolidados(consolidados);
        plan.setSoportePrioritario(soporte);
        return plan;
    }
}
