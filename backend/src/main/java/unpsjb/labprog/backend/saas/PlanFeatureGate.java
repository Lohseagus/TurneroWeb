package unpsjb.labprog.backend.saas;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import unpsjb.labprog.backend.model.PlanSuscripcion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Suscripcion;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.SaasDtos.FeatureDTO;
import unpsjb.labprog.backend.saas.SaasDtos.FeaturesPlanDTO;

/**
 * Compuerta de funciones por plan. Con {@code saas.enforcement.enabled} apagado no bloquea nada.
 *
 * <p>Aplica al personal de gestión de un centro (ADMINISTRADOR y OPERADOR). El SUPERADMIN
 * no entra. El médico y el paciente tampoco: lo que usan (agenda, historia clínica, sus
 * estadísticas) no depende del plan. Un centro sin suscripción no se corta acá: eso lo
 * resuelve el modo solo lectura.</p>
 */
@Service
public class PlanFeatureGate {

    public static final String CODIGO_FEATURE_NO_INCLUIDA = "PLAN_SIN_FEATURE";

    private final SuscripcionAccessService access;
    private final SaasEnforcement enforcement;

    @Autowired
    public PlanFeatureGate(SuscripcionAccessService access, SaasEnforcement enforcement) {
        this.access = access;
        this.enforcement = enforcement;
    }

    /** Resultado de la compuerta. {@code mensaje} es null si la función está permitida. */
    public record Resultado(boolean permite, String mensaje, FeaturePlan feature, String planRequerido) {

        static Resultado permitir() {
            return new Resultado(true, null, null, null);
        }
    }

    public boolean habilitada() {
        return enforcement != null && enforcement.habilitado();
    }

    public Resultado evaluar(FeaturePlan feature) {
        return evaluar(usuarioActual(), feature);
    }

    public Resultado evaluar(User user, FeaturePlan feature) {
        if (feature == null || !habilitada() || !alcanzado(user)) {
            return Resultado.permitir();
        }
        PlanSuscripcion plan = planDe(user);
        if (plan == null || PlanFeatures.incluye(plan, feature)) {
            return Resultado.permitir();
        }
        return new Resultado(false, PlanFeatures.mensajeNoIncluida(plan, feature), feature,
                PlanFeatures.planMinimo(feature));
    }

    /** Lo que ve el frontend para armar el menú. */
    public FeaturesPlanDTO features() {
        return features(usuarioActual());
    }

    public FeaturesPlanDTO features(User user) {
        FeaturesPlanDTO dto = new FeaturesPlanDTO();
        dto.enforcementEnabled = habilitada();
        dto.aplica = alcanzado(user);
        PlanSuscripcion plan = dto.aplica ? planDe(user) : null;
        if (plan == null) {
            // Sin plan que mirar (o sin compuerta para este rol): no se oculta nada.
            dto.aplica = false;
        } else {
            dto.planCodigo = plan.getCodigo();
            dto.planNombre = plan.getNombre();
        }
        Set<FeaturePlan> incluidas = plan == null ? Set.of(FeaturePlan.values()) : PlanFeatures.de(plan);
        List<FeatureDTO> catalogo = new ArrayList<>();
        List<String> codigos = new ArrayList<>();
        for (FeaturePlan feature : FeaturePlan.values()) {
            FeatureDTO item = new FeatureDTO();
            item.codigo = feature.name();
            item.etiqueta = feature.etiqueta();
            item.incluida = incluidas.contains(feature);
            item.planMinimoCodigo = PlanFeatures.planMinimo(feature);
            item.planMinimoNombre = PlanFeatures.nombrePlan(item.planMinimoCodigo);
            catalogo.add(item);
            if (item.incluida) {
                codigos.add(feature.name());
            }
        }
        dto.features = codigos;
        dto.catalogo = catalogo;
        return dto;
    }

    private PlanSuscripcion planDe(User user) {
        if (user == null || user.getCentroAtencion() == null || user.getCentroAtencion().getId() == null) {
            return null;
        }
        Suscripcion suscripcion = access.buscarPorCentro(user.getCentroAtencion().getId()).orElse(null);
        return suscripcion == null ? null : suscripcion.getPlan();
    }

    static boolean alcanzado(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        Role role = user.getRole();
        return role == Role.ADMINISTRADOR || role == Role.OPERADOR;
    }

    static User usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof User user ? user : null;
    }
}
