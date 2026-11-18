package unpsjb.labprog.backend.business.service;

import unpsjb.labprog.backend.dto.PacienteDTO;

/**
 * Resultado de POST /api/pacientes/me.
 * vinculada: ya había una ficha con el email de la cuenta y se reutilizó.
 */
public record AltaFichaPropia(PacienteDTO ficha, boolean vinculada) {
}
