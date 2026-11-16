package unpsjb.labprog.backend.business.hce;

import unpsjb.labprog.backend.model.Role;

/**
 * Reglas de acceso a la historia clínica, evaluadas sobre el rol primario
 * (no sobre la jerarquía de Spring). ADMINISTRADOR y SUPERADMIN heredan
 * MEDICO en SecurityConfig, pero no ven contenido clínico.
 */
public final class HceAccessPolicy {

    public enum Nivel {
        CLINICO,
        METADATOS,
        DENEGADO
    }

    private HceAccessPolicy() {
    }

    public static Nivel nivel(Role role, boolean esTitular) {
        if (role == null) {
            return Nivel.DENEGADO;
        }
        return switch (role) {
            case PACIENTE -> esTitular ? Nivel.CLINICO : Nivel.DENEGADO;
            case MEDICO -> Nivel.CLINICO;
            case OPERADOR, ADMINISTRADOR, SUPERADMIN -> Nivel.METADATOS;
        };
    }

    /**
     * Lectura de un recurso asociado a un centro.
     * Mismo centro (staff), autor de la entrada, o consentimiento de cruce.
     */
    public static boolean medicoPuedeLeer(boolean staffDelCentro, boolean esAutor, boolean consentimiento) {
        return staffDelCentro || esAutor || consentimiento;
    }

    /**
     * Alta de una consulta: el médico pertenece al centro y el turno,
     * si viene informado, es suyo. Sin turno, solo si ya atendió ahí.
     */
    public static boolean medicoPuedeCrear(boolean staffDelCentro, boolean vinculoValido) {
        return staffDelCentro && vinculoValido;
    }
}
