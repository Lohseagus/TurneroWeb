package unpsjb.labprog.backend.saas;

/**
 * Funciones que dependen del plan. El reparto por plan vive en {@link PlanFeatures}.
 */
public enum FeaturePlan {

    REPORTES("Reportes y métricas"),
    REPORTES_CONSOLIDADOS("Reportes consolidados de la red"),
    AUDITORIA("Panel de auditoría"),
    ENCUESTAS("Encuestas de satisfacción"),
    SOPORTE_PRIORITARIO("Soporte prioritario");

    private final String etiqueta;

    FeaturePlan(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
