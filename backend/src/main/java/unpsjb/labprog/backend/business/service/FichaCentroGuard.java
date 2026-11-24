package unpsjb.labprog.backend.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

/**
 * Un ADMIN o un OPERADOR lee la ficha de un paciente solo si ese paciente tiene
 * un turno o una solicitud en la lista de espera de su centro. SUPERADMIN no
 * tiene ese límite. Si la ficha no existe, no decide: el endpoint responde 404.
 * Sin sesión no restringe, para el modo de desarrollo y Cucumber.
 */
@Service
public class FichaCentroGuard {

    private static final Logger log = LoggerFactory.getLogger(FichaCentroGuard.class);

    public static final String MENSAJE = "No tenés permiso para ver pacientes de otro centro";

    private final PacienteRepository pacientes;
    private final TurnoRepository turnos;
    private final ListaEsperaRepository listaEspera;

    public FichaCentroGuard(PacienteRepository pacientes, TurnoRepository turnos, ListaEsperaRepository listaEspera) {
        this.pacientes = pacientes;
        this.turnos = turnos;
        this.listaEspera = listaEspera;
    }

    public void exigir(Integer pacienteId) {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() == null || user.tieneAccesoGlobal()) {
            return;
        }
        if (user.getRole() != Role.ADMINISTRADOR && user.getRole() != Role.OPERADOR) {
            return;
        }
        if (pacienteId == null || !pacientes.existsById(pacienteId)) {
            return;
        }
        Integer centroId = user.getCentroAtencion() == null ? null : user.getCentroAtencion().getId();
        boolean relacionado = centroId != null && (turnos.existsByPaciente_IdAndCentroAtencion_Id(pacienteId, centroId)
                || listaEspera.existsByPaciente_IdAndCentroAtencion_Id(pacienteId, centroId));
        if (!relacionado) {
            log.warn("Acceso denegado: {} ({}) pidió la ficha del paciente {}", user.getEmail(), user.getRole(),
                    pacienteId);
            throw new AccessDeniedException(MENSAJE);
        }
    }
}
