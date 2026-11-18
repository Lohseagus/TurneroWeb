package unpsjb.labprog.backend.business.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

/**
 * Decide si la cuenta autenticada es la titular de una ficha de paciente.
 *
 * No hay FK paciente→user: el titular es la cuenta cuyo email coincide con el de la
 * ficha. Un pacienteId que llega del cliente solo se compara contra ese resultado.
 *
 * Sin usuario autenticado no se restringe nada: solo pasa con security.dev.mode=true,
 * porque con el modo cerrado las reglas de SecurityConfig ya exigieron sesión.
 */
@Service
public class PacienteAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(PacienteAccessGuard.class);

    static final String MENSAJE = "No tenés permiso para ver datos de otro paciente";

    private final PacienteRepository pacienteRepository;

    public PacienteAccessGuard(PacienteRepository pacienteRepository) {
        this.pacienteRepository = pacienteRepository;
    }

    /** Ficha de la cuenta autenticada, si tiene una. */
    public Optional<Integer> fichaPropia() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return Optional.empty();
        }
        return pacienteRepository.findByUserEmail(user.getEmail().trim()).map(Paciente::getId);
    }

    public boolean esTitular(Integer pacienteId) {
        return pacienteId != null && fichaPropia().filter(pacienteId::equals).isPresent();
    }

    /** Solo el titular. El personal del centro tampoco entra, salvo a su propia ficha. */
    public void exigirTitular(Integer pacienteId) {
        User user = AuditContext.getCurrentUserObject();
        if (user == null) {
            return;
        }
        if (!esTitular(pacienteId)) {
            denegar(user, pacienteId);
        }
    }

    public void exigirTitularDelTurno(Turno turno) {
        exigirTitular(turno == null || turno.getPaciente() == null ? null : turno.getPaciente().getId());
    }

    /** El paciente solo entra a su ficha; el personal del centro pasa. */
    public void exigirTitularOPersonal(Integer pacienteId) {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() != Role.PACIENTE) {
            return;
        }
        if (!esTitular(pacienteId)) {
            denegar(user, pacienteId);
        }
    }

    /**
     * Paciente a nombre de quien se da un alta. Para una cuenta PACIENTE es siempre su
     * ficha, mande lo que mande el cliente; el personal del centro elige el paciente.
     */
    public Integer pacienteParaAlta(Integer pacienteIdPedido) {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() != Role.PACIENTE) {
            return pacienteIdPedido;
        }
        return fichaPropia().orElseThrow(() -> {
            log.warn("La cuenta {} tiene rol PACIENTE y no tiene ficha de paciente", user.getEmail());
            return new AccessDeniedException("Tu cuenta no tiene una ficha de paciente vinculada");
        });
    }

    private void denegar(User user, Integer pacienteId) {
        log.warn("Acceso denegado: {} ({}) pidió datos del paciente {}", user.getEmail(), user.getRole(), pacienteId);
        throw new AccessDeniedException(MENSAJE);
    }
}
