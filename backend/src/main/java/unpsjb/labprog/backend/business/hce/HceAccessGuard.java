package unpsjb.labprog.backend.business.hce;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PerfilClinicoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.exception.MedicoSinFichaException;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;
import unpsjb.labprog.backend.model.hce.Consulta;

@Service
public class HceAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(HceAccessGuard.class);

    private final MedicoRepository medicoRepository;
    private final StaffMedicoRepository staffMedicoRepository;
    private final PerfilClinicoRepository perfilRepository;
    private final ConsultaRepository consultaRepository;
    private final TurnoRepository turnoRepository;
    private final HceAuditoriaService auditoria;

    public HceAccessGuard(MedicoRepository medicoRepository, StaffMedicoRepository staffMedicoRepository,
            PerfilClinicoRepository perfilRepository, ConsultaRepository consultaRepository,
            TurnoRepository turnoRepository, HceAuditoriaService auditoria) {
        this.medicoRepository = medicoRepository;
        this.staffMedicoRepository = staffMedicoRepository;
        this.perfilRepository = perfilRepository;
        this.consultaRepository = consultaRepository;
        this.turnoRepository = turnoRepository;
        this.auditoria = auditoria;
    }

    public User usuarioActual() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() == null) {
            throw new HceAccessDeniedException("Debe iniciar sesión para acceder a la historia clínica", 401);
        }
        return user;
    }

    public boolean esTitular(User user, Paciente paciente) {
        return user.getRole() == Role.PACIENTE
                && user.getEmail() != null
                && paciente.getEmail() != null
                && user.getEmail().equalsIgnoreCase(paciente.getEmail());
    }

    public Medico exigirMedico(User user) {
        if (user.getRole() != Role.MEDICO) {
            throw new HceAccessDeniedException("Solo el médico que atiende puede modificar la historia clínica");
        }
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        return medicoRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    log.warn("Usuario {} tiene rol MEDICO y no tiene una ficha Medico vinculada por email", email);
                    return new HceNotFoundException(MedicoSinFichaException.MENSAJE);
                });
    }

    public boolean puedeLeerConsulta(Consulta consulta) {
        User user = usuarioActual();
        HceAccessPolicy.Nivel nivel = HceAccessPolicy.nivel(user.getRole(), esTitular(user, consulta.getPaciente()));
        if (nivel != HceAccessPolicy.Nivel.CLINICO) {
            return false;
        }
        if (user.getRole() == Role.PACIENTE) {
            return true;
        }
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        Medico medico = email.isEmpty() ? null : medicoRepository.findByEmailIgnoreCase(email).orElse(null);
        if (medico == null) {
            return false;
        }
        boolean staff = staffMedicoRepository.existsByMedicoIdAndCentroId(
                medico.getId(), consulta.getCentroAtencion().getId());
        boolean autor = consulta.getMedico().getId().equals(medico.getId());
        return HceAccessPolicy.medicoPuedeLeer(staff, autor, consentimiento(consulta.getPaciente()));
    }

    public void assertLeeConsulta(Consulta consulta) {
        if (!puedeLeerConsulta(consulta)) {
            User user = usuarioActual();
            HceAccessPolicy.Nivel nivel = HceAccessPolicy.nivel(user.getRole(), esTitular(user, consulta.getPaciente()));
            if (nivel == HceAccessPolicy.Nivel.METADATOS) {
                denegar(consulta.getPaciente(), "Su rol no puede ver contenido clínico");
            }
            denegar(consulta.getPaciente(), user.getRole() == Role.PACIENTE
                    ? "Sin acceso a esta historia clínica"
                    : "La historia de otro centro requiere el consentimiento del paciente");
        }
    }

    public void assertLeePerfil(Paciente paciente) {
        User user = usuarioActual();
        HceAccessPolicy.Nivel nivel = HceAccessPolicy.nivel(user.getRole(), esTitular(user, paciente));
        if (nivel == HceAccessPolicy.Nivel.DENEGADO) {
            denegar(paciente, "Sin acceso a esta historia clínica");
        }
        if (nivel == HceAccessPolicy.Nivel.METADATOS) {
            denegar(paciente, "Su rol no puede ver contenido clínico");
        }
        if (user.getRole() == Role.PACIENTE) {
            return;
        }
        Medico medico = exigirMedico(user);
        if (!medicoPuedeVerPaciente(medico, paciente)) {
            denegar(paciente, "La historia de otro centro requiere el consentimiento del paciente");
        }
    }

    public void assertEscribePerfil(Paciente paciente) {
        User user = usuarioActual();
        if (user.getRole() != Role.MEDICO) {
            denegar(paciente, "Solo un médico puede actualizar el perfil clínico");
        }
        assertLeePerfil(paciente);
    }

    public Medico assertPuedeCrear(Paciente paciente, Integer centroId, Turno turno) {
        User user = usuarioActual();
        if (user.getRole() != Role.MEDICO) {
            denegar(paciente, "Solo un médico puede registrar una consulta");
        }
        Medico medico = exigirMedico(user);
        boolean staff = centroId != null && staffMedicoRepository.existsByMedicoIdAndCentroId(medico.getId(), centroId);
        boolean vinculo = vinculoDeAtencion(medico, paciente, centroId, turno);
        if (!HceAccessPolicy.medicoPuedeCrear(staff, vinculo)) {
            denegar(paciente, "Solo puede registrar la consulta de un paciente que atiende en su centro");
        }
        return medico;
    }

    public void assertPuedeEnmendar(Consulta consulta) {
        User user = usuarioActual();
        if (user.getRole() != Role.MEDICO) {
            denegar(consulta.getPaciente(), "Solo un médico puede registrar una enmienda");
        }
        assertLeeConsulta(consulta);
        Medico medico = exigirMedico(user);
        boolean staff = staffMedicoRepository.existsByMedicoIdAndCentroId(
                medico.getId(), consulta.getCentroAtencion().getId());
        boolean autor = consulta.getMedico().getId().equals(medico.getId());
        if (!staff && !autor) {
            denegar(consulta.getPaciente(), "La enmienda la registra el médico tratante o uno del mismo centro");
        }
    }

    public void assertLeeMetadata(Paciente paciente) {
        User user = usuarioActual();
        HceAccessPolicy.Nivel nivel = HceAccessPolicy.nivel(user.getRole(), esTitular(user, paciente));
        if (nivel == HceAccessPolicy.Nivel.DENEGADO) {
            denegar(paciente, "Sin acceso a esta historia clínica");
        }
        if (nivel == HceAccessPolicy.Nivel.CLINICO) {
            if (user.getRole() == Role.PACIENTE) {
                return;
            }
            Medico medico = exigirMedico(user);
            if (!medicoPuedeVerPaciente(medico, paciente)) {
                denegar(paciente, "La historia de otro centro requiere el consentimiento del paciente");
            }
            return;
        }
        if (user.getRole() == Role.SUPERADMIN) {
            return;
        }
        Integer centroId = user.getCentroAtencion() != null ? user.getCentroAtencion().getId() : null;
        if (centroId == null || !pacienteVistoEnCentro(paciente, centroId)) {
            denegar(paciente, "Sin acceso a la historia de otro centro");
        }
    }

    public boolean medicoPuedeVerPaciente(Medico medico, Paciente paciente) {
        Set<Integer> centros = centrosDe(medico);
        if (centros.isEmpty()) {
            return false;
        }
        if (consentimiento(paciente)) {
            return true;
        }
        for (Consulta consulta : consultaRepository.findByPaciente_IdOrderByCreadaEnDesc(paciente.getId())) {
            if (centros.contains(consulta.getCentroAtencion().getId())) {
                return true;
            }
        }
        for (Turno turno : turnoRepository.findByPaciente_Id(paciente.getId())) {
            if (turno.getEstado() == EstadoTurno.CANCELADO || turno.getCentroAtencion() == null) {
                continue;
            }
            if (centros.contains(turno.getCentroAtencion().getId())) {
                return true;
            }
        }
        return false;
    }

    public boolean consentimiento(Paciente paciente) {
        return perfilRepository.findByPaciente_Id(paciente.getId())
                .map(perfil -> perfil.isComparteEntreCentros())
                .orElse(false);
    }

    public Set<Integer> centrosDe(Medico medico) {
        Set<Integer> centros = new HashSet<>();
        staffMedicoRepository.findByMedicoId(medico.getId())
                .forEach(staff -> {
                    if (staff.getCentroAtencion() != null) {
                        centros.add(staff.getCentroAtencion().getId());
                    }
                });
        return centros;
    }

    private boolean vinculoDeAtencion(Medico medico, Paciente paciente, Integer centroId, Turno turno) {
        if (turno != null) {
            return turno.getMedico() != null
                    && turno.getMedico().getId().equals(medico.getId())
                    && turno.getPaciente() != null
                    && turno.getPaciente().getId().equals(paciente.getId())
                    && turno.getCentroAtencion() != null
                    && turno.getCentroAtencion().getId().equals(centroId)
                    && turno.getEstado() != EstadoTurno.CANCELADO;
        }
        if (centroId == null) {
            return false;
        }
        boolean atendido = turnoRepository.existsByPaciente_IdAndMedico_IdAndCentroAtencion_IdAndEstadoNot(
                paciente.getId(), medico.getId(), centroId, EstadoTurno.CANCELADO);
        boolean previa = consultaRepository.existsByPaciente_IdAndCentroAtencion_IdAndMedico_Id(
                paciente.getId(), centroId, medico.getId());
        return atendido || previa;
    }

    private boolean pacienteVistoEnCentro(Paciente paciente, Integer centroId) {
        for (Turno turno : turnoRepository.findByPaciente_Id(paciente.getId())) {
            if (turno.getCentroAtencion() != null && centroId.equals(turno.getCentroAtencion().getId())) {
                return true;
            }
        }
        return consultaRepository.findByPaciente_IdOrderByCreadaEnDesc(paciente.getId()).stream()
                .anyMatch(consulta -> centroId.equals(consulta.getCentroAtencion().getId()));
    }

    private void denegar(Paciente paciente, String detalle) {
        if (paciente != null && paciente.getId() != null) {
            auditoria.registrar(paciente.getId(), AccionAuditoriaHce.ACCESO_DENEGADO, "HCE", null, null, detalle);
        }
        throw new HceAccessDeniedException(detalle);
    }

    public void auditar(Paciente paciente, AccionAuditoriaHce accion, String recursoTipo, String recursoId,
            Integer centroId, String detalle) {
        if (paciente != null) {
            auditoria.registrar(paciente.getId(), accion, recursoTipo, recursoId, centroId, detalle);
        }
    }
}
