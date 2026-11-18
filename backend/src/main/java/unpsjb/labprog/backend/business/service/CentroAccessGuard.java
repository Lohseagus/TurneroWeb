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
