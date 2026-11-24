package unpsjb.labprog.backend.business.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

/**
 * Aislamiento por centro de atención para el personal.
 *
 * - SUPERADMIN: sin filtro.
 * - ADMINISTRADOR y OPERADOR: el centro de su cuenta.
 * - MEDICO: el centro de su cuenta más los centros donde tiene un StaffMedico
 *   (la ficha Medico se resuelve por el email de la cuenta).
 * - PACIENTE: no está atado a un centro; sobre un turno se le exige ser el titular.
 *
 * Sin usuario autenticado no se restringe nada: pasa con security.dev.mode=true y en
 * las tareas programadas; con el modo cerrado SecurityConfig ya exigió sesión.
 */
@Service
public class CentroAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(CentroAccessGuard.class);

    public static final String MENSAJE = "No tenés permiso para operar datos de otro centro";

    private final MedicoRepository medicoRepository;
    private final StaffMedicoRepository staffMedicoRepository;
    private final TurnoRepository turnoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final DisponibilidadMedicoRepository disponibilidadRepository;
    private final EsquemaTurnoRepository esquemaTurnoRepository;
    private final PacienteAccessGuard pacienteAccess;

    public CentroAccessGuard(MedicoRepository medicoRepository, StaffMedicoRepository staffMedicoRepository,
            TurnoRepository turnoRepository, ConsultorioRepository consultorioRepository,
            DisponibilidadMedicoRepository disponibilidadRepository,
            EsquemaTurnoRepository esquemaTurnoRepository, PacienteAccessGuard pacienteAccess) {
        this.medicoRepository = medicoRepository;
        this.staffMedicoRepository = staffMedicoRepository;
        this.turnoRepository = turnoRepository;
        this.consultorioRepository = consultorioRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.esquemaTurnoRepository = esquemaTurnoRepository;
        this.pacienteAccess = pacienteAccess;
    }

    /**
     * Centros sobre los que puede operar la cuenta autenticada.
     *
     * @return null si no hay restricción; un conjunto (puede estar vacío) si la hay
     */
    public Set<Integer> centrosPermitidos() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() == null || !user.estáLimitadoACentro()) {
            return null;
        }
        Set<Integer> centros = new LinkedHashSet<>();
        if (user.getCentroAtencion() != null && user.getCentroAtencion().getId() != null) {
            centros.add(user.getCentroAtencion().getId());
        }
        if (user.getRole() == Role.MEDICO && user.getEmail() != null && !user.getEmail().isBlank()) {
            medicoRepository.findByEmailIgnoreCase(user.getEmail().trim()).ifPresent(medico -> {
                for (StaffMedico staff : staffMedicoRepository.findByMedicoId(medico.getId())) {
                    if (staff.getCentroAtencion() != null && staff.getCentroAtencion().getId() != null) {
                        centros.add(staff.getCentroAtencion().getId());
                    }
                }
            });
        }
        return centros;
    }

    public boolean puedeOperar(Integer centroId) {
        Set<Integer> centros = centrosPermitidos();
        return centros == null || (centroId != null && centros.contains(centroId));
    }

    /** 403 si el recurso es de un centro al que la cuenta no pertenece. */
    public void exigirCentro(Integer centroId) {
        if (!puedeOperar(centroId)) {
            User user = AuditContext.getCurrentUserObject();
            log.warn("Acceso denegado: {} ({}) pidió un recurso del centro {}",
                    user.getEmail(), user.getRole(), centroId);
            throw new AccessDeniedException(MENSAJE);
        }
    }

    /**
     * Turno por id: el personal solo opera los de sus centros y el paciente solo los
     * suyos. Si el turno no existe no decide nada: el endpoint responde su 404.
     */
    public void exigirTurno(Integer turnoId) {
        if (turnoId == null || AuditContext.getCurrentUserObject() == null) {
            return;
        }
        turnoRepository.findById(turnoId).ifPresent(this::exigirTurno);
    }

    public void exigirTurno(Turno turno) {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || turno == null) {
            return;
        }
        if (user.getRole() == Role.PACIENTE) {
            pacienteAccess.exigirTitularDelTurno(turno);
            return;
        }
        exigirCentro(centroDe(turno));
    }

    // Recursos del centro por id. Si el id es null o el recurso no existe no deciden
    // nada: el endpoint responde su error de validación o su 404.

    public void exigirConsultorio(Integer consultorioId) {
        if (consultorioId != null && restringe()) {
            consultorioRepository.findById(consultorioId).ifPresent(consultorio -> exigirCentro(
                    consultorio.getCentroAtencion() == null ? null : consultorio.getCentroAtencion().getId()));
        }
    }

    public void exigirStaffMedico(Integer staffMedicoId) {
        if (staffMedicoId != null && restringe()) {
            staffMedicoRepository.findById(staffMedicoId).ifPresent(staff -> exigirCentro(centroDe(staff)));
        }
    }

    public void exigirDisponibilidad(Integer disponibilidadId) {
        if (disponibilidadId != null && restringe()) {
            disponibilidadRepository.findById(disponibilidadId)
                    .ifPresent(disponibilidad -> exigirCentro(centroDe(disponibilidad.getStaffMedico())));
        }
    }

    public void exigirEsquemaTurno(Integer esquemaId) {
        if (esquemaId != null && restringe()) {
            esquemaTurnoRepository.findById(esquemaId).ifPresent(esquema -> exigirCentro(
                    esquema.getCentroAtencion() != null
                            ? esquema.getCentroAtencion().getId()
                            : centroDe(esquema.getStaffMedico())));
        }
    }

    /** Un centro que viene en el cuerpo del pedido; si no viene, no decide. */
    public void exigirCentroSiViene(Integer centroId) {
        if (centroId != null) {
            exigirCentro(centroId);
        }
    }

    /**
     * Alta y baja de un centro. Solo SUPERADMIN. Sin sesión no restringe:
     * es el modo de desarrollo y la suite Cucumber.
     */
    public void exigirSuperadmin() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null) {
            return;
        }
        if (user.getRole() != Role.SUPERADMIN) {
            throw new AccessDeniedException("Solo un superadmin puede crear o eliminar centros de atención");
        }
    }

    /** SUPERADMIN edita cualquiera. Un ADMIN, solo el suyo. El operador no edita. */
    public void exigirEdicionDeCentro(Integer centroId) {
        User user = AuditContext.getCurrentUserObject();
        if (user == null) {
            return;
        }
        if (user.getRole() == Role.SUPERADMIN) {
            return;
        }
        if (user.getRole() != Role.ADMINISTRADOR) {
            throw new AccessDeniedException("No tenés permiso para modificar centros de atención");
        }
        Integer propio = centroDe(user);
        if (centroId == null || propio == null || !propio.equals(centroId)) {
            throw new AccessDeniedException("Solo podés editar tu centro de atención");
        }
    }

    /** El operador consulta solo su centro. El catálogo público sigue abierto para el resto. */
    public void exigirLecturaDeCentro(Integer centroId) {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() != Role.OPERADOR) {
            return;
        }
        Integer propio = centroDe(user);
        if (centroId == null || propio == null || !propio.equals(centroId)) {
            throw new AccessDeniedException("Solo podés ver tu centro de atención");
        }
    }

    /**
     * Presente o ausente. El personal del centro del turno sigue pudiendo.
     * Un médico solo marca los turnos donde él es el profesional.
     */
    public void exigirMarcarAsistencia(Integer turnoId) {
        exigirTurno(turnoId);
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() != Role.MEDICO || turnoId == null) {
            return;
        }
        turnoRepository.findById(turnoId).ifPresent(turno -> {
            Integer propio = medicoPropioId();
            Integer delTurno = medicoDelTurno(turno);
            if (propio == null || delTurno == null || !propio.equals(delTurno)) {
                log.warn("Acceso denegado: {} intentó marcar la asistencia del turno {}", user.getEmail(), turnoId);
                throw new AccessDeniedException("Solo podés marcar la asistencia de tus turnos");
            }
        });
    }

    /**
     * Horarios. Un médico solo los suyos, en todos los centros donde atiende.
     * Admin y operador, los de los médicos de su centro.
     */
    public void exigirStaffDelActor(Integer staffMedicoId) {
        User user = actorLimitado();
        if (user == null || staffMedicoId == null) {
            return;
        }
        staffMedicoRepository.findById(staffMedicoId).ifPresent(staff -> aplicarStaff(user, staff));
    }

    public void exigirDisponibilidadDelActor(Integer disponibilidadId) {
        User user = actorLimitado();
        if (user == null || disponibilidadId == null) {
            return;
        }
        disponibilidadRepository.findById(disponibilidadId).ifPresent(disponibilidad -> aplicarStaff(user, disponibilidad.getStaffMedico()));
    }

    /** Listado por médico: el profesional solo el propio; el personal, si atiende en su centro. */
    public void exigirMedicoVisible(Integer medicoId) {
        User user = actorLimitado();
        if (user == null || medicoId == null) {
            return;
        }
        if (user.getRole() == Role.MEDICO) {
            Integer propio = medicoPropioId();
            if (propio == null || !propio.equals(medicoId)) {
                throw new AccessDeniedException("Solo podés ver y editar tus horarios");
            }
            return;
        }
        boolean enCentro = staffMedicoRepository.findByMedicoId(medicoId).stream()
                .anyMatch(staff -> puedeOperar(centroDe(staff)));
        if (!enCentro) {
            throw new AccessDeniedException(MENSAJE);
        }
    }

    public boolean puedeVerStaff(Integer staffMedicoId) {
        User user = actorLimitado();
        if (user == null) {
            return true;
        }
        if (staffMedicoId == null) {
            return false;
        }
        if (user.getRole() == Role.MEDICO) {
            return staffPropioIds().contains(staffMedicoId);
        }
        return staffMedicoRepository.findById(staffMedicoId)
                .map(staff -> puedeOperar(centroDe(staff)))
                .orElse(false);
    }

    /**
     * Disparo manual de un job. null significa todos los centros (superadmin, o sin
     * sesión en desarrollo). Un ADMIN queda atado a su centro. Médico y operador, no.
     */
    public Integer centroDeEjecucionManual() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() == null || user.getRole() == Role.SUPERADMIN) {
            return null;
        }
        if (user.getRole() == Role.ADMINISTRADOR) {
            Integer centroId = centroDe(user);
            if (centroId == null) {
                throw new AccessDeniedException("Tu usuario no tiene un centro asignado");
            }
            return centroId;
        }
        throw new AccessDeniedException(
                "Solo un administrador del centro o un superadmin puede ejecutar esta tarea");
    }

    public Set<Integer> staffPropioIds() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return Set.of();
        }
        return medicoRepository.findByEmailIgnoreCase(user.getEmail().trim())
                .map(medico -> {
                    Set<Integer> ids = new LinkedHashSet<>();
                    for (StaffMedico staff : staffMedicoRepository.findByMedicoId(medico.getId())) {
                        if (staff.getId() != null) {
                            ids.add(staff.getId());
                        }
                    }
                    return ids;
                })
                .orElse(Set.of());
    }

    /** null si no hay que restringir (sin sesión o superadmin). */
    private User actorLimitado() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() == null || user.getRole() == Role.SUPERADMIN) {
            return null;
        }
        return user;
    }

    private void aplicarStaff(User user, StaffMedico staff) {
        if (user.getRole() == Role.MEDICO) {
            exigirEsSuStaff(staff);
            return;
        }
        exigirCentro(centroDe(staff));
    }

    private void exigirEsSuStaff(StaffMedico staff) {
        Integer propio = medicoPropioId();
        Integer delStaff = staff == null || staff.getMedico() == null ? null : staff.getMedico().getId();
        if (propio == null || delStaff == null || !propio.equals(delStaff)) {
            throw new AccessDeniedException("Solo podés ver y editar tus horarios");
        }
    }

    private Integer medicoPropioId() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return null;
        }
        return medicoRepository.findByEmailIgnoreCase(user.getEmail().trim()).map(medico -> medico.getId()).orElse(null);
    }

    private static Integer medicoDelTurno(Turno turno) {
        if (turno.getMedico() != null && turno.getMedico().getId() != null) {
            return turno.getMedico().getId();
        }
        if (turno.getStaffMedico() != null && turno.getStaffMedico().getMedico() != null) {
            return turno.getStaffMedico().getMedico().getId();
        }
        return null;
    }

    private static Integer centroDe(User user) {
        return user.getCentroAtencion() == null ? null : user.getCentroAtencion().getId();
    }

    private boolean restringe() {
        User user = AuditContext.getCurrentUserObject();
        return user != null && user.getRole() != null && user.estáLimitadoACentro();
    }

    public static Integer centroDe(StaffMedico staff) {
        return staff == null || staff.getCentroAtencion() == null ? null : staff.getCentroAtencion().getId();
    }

    /**
     * Centro a usar en un listado que filtra por un solo centro. Un centro pedido que
     * no es de la cuenta se rechaza en vez de ignorarse. Sin centro pedido se usa el de
     * la cuenta; con null no se filtra (solo para cuentas sin restricción).
     */
    public Integer centroParaFiltro(Integer centroPedido) {
        Set<Integer> centros = centrosPermitidos();
        if (centros == null) {
            return centroPedido;
        }
        if (centroPedido != null) {
            exigirCentro(centroPedido);
            return centroPedido;
        }
        if (centros.isEmpty()) {
            log.warn("La cuenta {} está limitada a un centro y no tiene ninguno asignado",
                    AuditContext.getCurrentUser());
            throw new AccessDeniedException(MENSAJE);
        }
        return centros.iterator().next();
    }

    /** Deja solo los elementos de los centros de la cuenta. */
    public <T> List<T> filtrar(Collection<T> elementos, Function<T, Integer> centroDe) {
        Set<Integer> centros = centrosPermitidos();
        if (centros == null) {
            return new java.util.ArrayList<>(elementos);
        }
        return elementos.stream()
                .filter(elemento -> {
                    Integer centroId = centroDe.apply(elemento);
                    return centroId != null && centros.contains(centroId);
                })
                .toList();
    }

    public static Integer centroDe(Turno turno) {
        if (turno.getCentroAtencion() != null) {
            return turno.getCentroAtencion().getId();
        }
        if (turno.getConsultorio() != null && turno.getConsultorio().getCentroAtencion() != null) {
            return turno.getConsultorio().getCentroAtencion().getId();
        }
        return null;
    }
}
