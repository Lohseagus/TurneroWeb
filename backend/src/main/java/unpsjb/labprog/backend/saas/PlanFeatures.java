package unpsjb.labprog.backend.saas;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import unpsjb.labprog.backend.model.PlanSuscripcion;

/**
 * Mapa único plan → funciones.
 *
 * <p>La base sale del catálogo sembrado en {@code SaasService.asegurarDatosIniciales}:
 * Profesional no tiene reportes; Consultorio y Centro tienen reportes básicos;
 * Red suma reportes consolidados y soporte prioritario. Las tres banderas del catálogo
 * ({@code reportesBasicos}, {@code reportesConsolidados}, {@code soportePrioritario})
 * las edita el SUPERADMIN, así que para esas tres manda la fila del plan.
 * Auditoría y encuestas no están en el catálogo: se reparten acá.</p>
 *
 * <p>Este mapa no aplica nada por sí solo. Quien bloquea es {@link PlanFeatureGate},
 * y solo con {@code saas.enforcement.enabled} prendido.</p>
 */
public final class PlanFeatures {

    /** Orden comercial, del plan más chico al más grande. */
    static final List<String> ORDEN = List.of(
            CodigosPlan.PROFESIONAL, CodigosPlan.CONSULTORIO, CodigosPlan.CENTRO, CodigosPlan.RED);

    private static final Map<String, String> NOMBRES = Map.of(
            CodigosPlan.PROFESIONAL, "Profesional",
            CodigosPlan.CONSULTORIO, "Consultorio",
            CodigosPlan.CENTRO, "Centro",
            CodigosPlan.RED, "Red");

    private static final Map<String, Set<FeaturePlan>> MAPA;

    static {
        Map<String, Set<FeaturePlan>> mapa = new LinkedHashMap<>();
        mapa.put(CodigosPlan.PROFESIONAL, Collections.unmodifiableSet(EnumSet.noneOf(FeaturePlan.class)));
        Set<FeaturePlan> consultorioYCentro = Collections.unmodifiableSet(
                EnumSet.of(FeaturePlan.REPORTES, FeaturePlan.AUDITORIA, FeaturePlan.ENCUESTAS));
        mapa.put(CodigosPlan.CONSULTORIO, consultorioYCentro);
        mapa.put(CodigosPlan.CENTRO, consultorioYCentro);
        mapa.put(CodigosPlan.RED, Collections.unmodifiableSet(EnumSet.allOf(FeaturePlan.class)));
        MAPA = Collections.unmodifiableMap(mapa);
    }

    private PlanFeatures() {
    }

    /** Funciones por defecto de un código de plan, sin mirar las banderas del catálogo. */
    public static Set<FeaturePlan> porCodigo(String codigo) {
        Set<FeaturePlan> base = codigo == null ? null : MAPA.get(codigo.trim().toUpperCase());
        if (base != null) {
            return base;
        }
        // Un plan que no está en el mapa (creado a mano) no pierde auditoría ni encuestas.
        return Collections.unmodifiableSet(EnumSet.of(FeaturePlan.AUDITORIA, FeaturePlan.ENCUESTAS));
    }

    /** Funciones efectivas del plan: el mapa por código más las banderas del catálogo. */
    public static Set<FeaturePlan> de(PlanSuscripcion plan) {
        if (plan == null) {
            return Collections.unmodifiableSet(EnumSet.noneOf(FeaturePlan.class));
        }
        EnumSet<FeaturePlan> features = EnumSet.noneOf(FeaturePlan.class);
        features.addAll(porCodigo(plan.getCodigo()));
        ajustar(features, FeaturePlan.REPORTES, plan.isReportesBasicos() || plan.isReportesConsolidados());
        ajustar(features, FeaturePlan.REPORTES_CONSOLIDADOS, plan.isReportesConsolidados());
        ajustar(features, FeaturePlan.SOPORTE_PRIORITARIO, plan.isSoportePrioritario());
        return Collections.unmodifiableSet(features);
    }

    public static boolean incluye(PlanSuscripcion plan, FeaturePlan feature) {
        return feature != null && de(plan).contains(feature);
    }

    /** Código del plan más chico que trae la función según el mapa. */
    public static String planMinimo(FeaturePlan feature) {
        for (String codigo : ORDEN) {
            if (MAPA.get(codigo).contains(feature)) {
                return codigo;
            }
        }
        return CodigosPlan.RED;
    }

    public static String nombrePlan(String codigo) {
        if (codigo == null) {
            return null;
        }
        return NOMBRES.getOrDefault(codigo.trim().toUpperCase(), codigo);
    }

    public static String mensajeNoIncluida(PlanSuscripcion plan, FeaturePlan feature) {
        String actual = plan == null ? "actual" : plan.getNombre();
        return "El plan " + actual + " no incluye " + feature.etiqueta().toLowerCase()
                + ". Disponible en plan " + nombrePlan(planMinimo(feature))
                + ". Podés cambiar de plan desde Mi plan.";
    }

    private static void ajustar(Set<FeaturePlan> features, FeaturePlan feature, boolean incluida) {
        if (incluida) {
            features.add(feature);
        } else {
            features.remove(feature);
        }
    }
}
