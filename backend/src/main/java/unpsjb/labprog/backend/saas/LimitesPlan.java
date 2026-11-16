package unpsjb.labprog.backend.saas;

import unpsjb.labprog.backend.model.PlanSuscripcion;

public final class LimitesPlan {

    private LimitesPlan() {
    }

    public static void validarNuevo(PlanSuscripcion plan, UsoOrganizacion uso, RecursoSaas recurso) {
        if (plan == null || uso == null || recurso == null) {
            return;
        }
        switch (recurso) {
            case MEDICO -> validarTope(plan.getMaxMedicos(), uso.medicos(), "médicos");
            case OPERADOR -> validarTope(plan.getMaxOperadores(), uso.operadores(), "operadores");
            case CONSULTORIO -> validarTope(plan.getMaxConsultorios(), uso.consultorios(), "consultorios");
            case CENTRO -> validarTope(plan.getMaxCentros(), uso.centros(), "centros de atención");
            case ESPECIALIDAD -> validarTope(plan.getMaxEspecialidades(), uso.especialidades(), "especialidades");
            default -> {
                // Turnos, listas de espera y esquemas solo respetan el modo solo lectura.
            }
        }
    }

    public static void validarCabeEnPlan(PlanSuscripcion plan, UsoOrganizacion uso, int profesionalesContratados) {
        if (plan.getMaxMedicos() != null && uso.medicos() > plan.getMaxMedicos()) {
            throw new ReglaSuscripcionException(
                    "No se puede usar el plan " + plan.getNombre() + ": hay " + uso.medicos()
                            + " médicos y el máximo es " + plan.getMaxMedicos() + ".");
        }
        if (plan.getMaxOperadores() != null && uso.operadores() > plan.getMaxOperadores()) {
            throw new ReglaSuscripcionException(
                    "No se puede usar el plan " + plan.getNombre() + ": hay " + uso.operadores()
                            + " operadores y el máximo es " + plan.getMaxOperadores() + ".");
        }
        if (plan.getMaxConsultorios() != null && uso.consultorios() > plan.getMaxConsultorios()) {
            throw new ReglaSuscripcionException(
                    "No se puede usar el plan " + plan.getNombre() + ": hay " + uso.consultorios()
                            + " consultorios y el máximo es " + plan.getMaxConsultorios() + ".");
        }
        if (plan.getMaxCentros() != null && uso.centros() > plan.getMaxCentros()) {
            throw new ReglaSuscripcionException(
                    "No se puede usar el plan " + plan.getNombre() + ": hay " + uso.centros()
                            + " centros y el máximo es " + plan.getMaxCentros() + ".");
        }
        if (plan.getMaxEspecialidades() != null && uso.especialidades() > plan.getMaxEspecialidades()) {
            throw new ReglaSuscripcionException(
                    "No se puede usar el plan " + plan.getNombre() + ": hay " + uso.especialidades()
                            + " especialidades y el máximo es " + plan.getMaxEspecialidades() + ".");
        }
        if (plan.getPrecioPorProfesional() != null && plan.getPrecioPorProfesional().signum() > 0) {
            int min = plan.getMinProfesionales() == null ? 1 : plan.getMinProfesionales();
            int max = plan.getMaxProfesionales() == null ? Integer.MAX_VALUE : plan.getMaxProfesionales();
            int seats = Math.max(profesionalesContratados, (int) uso.medicos());
            if (seats < min || seats > max) {
                throw new ReglaSuscripcionException(
                        "El plan " + plan.getNombre() + " se contrata de " + min + " a " + max + " profesionales.");
            }
        }
    }

    private static void validarTope(Integer maximo, long actual, String etiqueta) {
        if (maximo != null && actual >= maximo) {
            throw new ReglaSuscripcionException(
                    "El plan permite como máximo " + maximo + " " + etiqueta + ".");
        }
    }
}
