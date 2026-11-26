package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.PlanSuscripcion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Suscripcion;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.SaasDtos.FeaturesPlanDTO;

class PlanFeatureGateTest {

    private static final PlanSuscripcion PROFESIONAL =
            PlanFeaturesTest.plan(CodigosPlan.PROFESIONAL, false, false, false);
    private static final PlanSuscripcion CENTRO =
            PlanFeaturesTest.plan(CodigosPlan.CENTRO, true, false, false);

    @Test
    void conLaBanderaApagadaNoBloqueaNiConsultaLaSuscripcion() {
        SuscripcionAccessService access = accessCon(PROFESIONAL);
        PlanFeatureGate gate = new PlanFeatureGate(access, new SaasEnforcement(false));

        for (FeaturePlan feature : FeaturePlan.values()) {
            assertTrue(gate.evaluar(usuario(Role.ADMINISTRADOR, 1), feature).permite(), feature.name());
        }
        verify(access, never()).buscarPorCentro(any());
    }

    @Test
    void conLaBanderaPrendidaElPlanProfesionalNoTieneAuditoria() {
        PlanFeatureGate gate = new PlanFeatureGate(accessCon(PROFESIONAL), new SaasEnforcement(true));

        PlanFeatureGate.Resultado resultado = gate.evaluar(usuario(Role.ADMINISTRADOR, 1), FeaturePlan.AUDITORIA);

        assertFalse(resultado.permite());
        assertEquals(FeaturePlan.AUDITORIA, resultado.feature());
        assertEquals(CodigosPlan.CONSULTORIO, resultado.planRequerido());
        assertTrue(resultado.mensaje().contains("Disponible en plan Consultorio"));
        assertFalse(gate.evaluar(usuario(Role.OPERADOR, 1), FeaturePlan.ENCUESTAS).permite());
    }

    @Test
    void conLaBanderaPrendidaElPlanCentroPasa() {
        PlanFeatureGate gate = new PlanFeatureGate(accessCon(CENTRO), new SaasEnforcement(true));

        assertTrue(gate.evaluar(usuario(Role.ADMINISTRADOR, 1), FeaturePlan.AUDITORIA).permite());
        assertTrue(gate.evaluar(usuario(Role.ADMINISTRADOR, 1), FeaturePlan.REPORTES).permite());
        assertFalse(gate.evaluar(usuario(Role.ADMINISTRADOR, 1), FeaturePlan.REPORTES_CONSOLIDADOS).permite());
    }

    @Test
    void elSuperadminElMedicoYElPacienteNoPasanPorLaCompuerta() {
        SuscripcionAccessService access = accessCon(PROFESIONAL);
        PlanFeatureGate gate = new PlanFeatureGate(access, new SaasEnforcement(true));

        assertTrue(gate.evaluar(usuario(Role.SUPERADMIN, null), FeaturePlan.AUDITORIA).permite());
        assertTrue(gate.evaluar(usuario(Role.SUPERADMIN, 1), FeaturePlan.AUDITORIA).permite());
        assertTrue(gate.evaluar(usuario(Role.MEDICO, 1), FeaturePlan.AUDITORIA).permite());
        assertTrue(gate.evaluar(usuario(Role.PACIENTE, null), FeaturePlan.ENCUESTAS).permite());
        assertTrue(gate.evaluar(null, FeaturePlan.AUDITORIA).permite());
        verify(access, never()).buscarPorCentro(any());
    }

    @Test
    void unCentroSinSuscripcionLoResuelveElModoSoloLectura() {
        SuscripcionAccessService access = mock(SuscripcionAccessService.class);
        when(access.buscarPorCentro(any())).thenReturn(Optional.empty());
        PlanFeatureGate gate = new PlanFeatureGate(access, new SaasEnforcement(true));

        assertTrue(gate.evaluar(usuario(Role.ADMINISTRADOR, 1), FeaturePlan.AUDITORIA).permite());
        assertTrue(gate.evaluar(usuario(Role.ADMINISTRADOR, null), FeaturePlan.AUDITORIA).permite());
    }

    @Test
    void lasFeaturesDelPlanLlegaAlFrontendConElPlanMinimo() {
        PlanFeatureGate gate = new PlanFeatureGate(accessCon(PROFESIONAL), new SaasEnforcement(true));

        FeaturesPlanDTO dto = gate.features(usuario(Role.ADMINISTRADOR, 1));

        assertTrue(dto.enforcementEnabled);
        assertTrue(dto.aplica);
        assertEquals(CodigosPlan.PROFESIONAL, dto.planCodigo);
        assertTrue(dto.features.isEmpty());
        assertEquals(FeaturePlan.values().length, dto.catalogo.size());
        SaasDtos.FeatureDTO auditoria = dto.catalogo.stream()
                .filter(f -> f.codigo.equals("AUDITORIA")).findFirst().orElseThrow();
        assertFalse(auditoria.incluida);
        assertEquals("Consultorio", auditoria.planMinimoNombre);
    }

    @Test
    void conLaBanderaApagadaElFrontendRecibeEnforcementFalse() {
        PlanFeatureGate gate = new PlanFeatureGate(accessCon(PROFESIONAL), new SaasEnforcement(false));

        FeaturesPlanDTO dto = gate.features(usuario(Role.ADMINISTRADOR, 1));

        assertFalse(dto.enforcementEnabled);
        assertEquals(CodigosPlan.PROFESIONAL, dto.planCodigo);
    }

    @Test
    void elSuperadminRecibeTodoIncluidoYSinCompuerta() {
        PlanFeatureGate gate = new PlanFeatureGate(accessCon(PROFESIONAL), new SaasEnforcement(true));

        FeaturesPlanDTO dto = gate.features(usuario(Role.SUPERADMIN, null));

        assertFalse(dto.aplica);
        assertEquals(FeaturePlan.values().length, dto.features.size());
    }

    static SuscripcionAccessService accessCon(PlanSuscripcion plan) {
        SuscripcionAccessService access = mock(SuscripcionAccessService.class);
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setPlan(plan);
        when(access.buscarPorCentro(any())).thenReturn(Optional.of(suscripcion));
        return access;
    }

    static User usuario(Role role, Integer centroId) {
        User user = new User();
        user.setId(1L);
        user.setEmail(role.name().toLowerCase() + "@cheturno.test");
        user.setRole(role);
        if (centroId != null) {
            CentroAtencion centro = new CentroAtencion();
            centro.setId(centroId);
            user.setCentroAtencion(centro);
        }
        return user;
    }
}
