package unpsjb.labprog.backend.business.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.OperadorResumenDTO;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.saas.RelojArgentina;

/**
 * Resumen del centro para el operador. Usa {@link CentroAccessGuard}: un operador
 * solo cuenta turnos y lista de espera de su centro. Sin centro, o con acceso
 * global, no recorre todos los centros.
 */
@Service
public class OperadorResumenService {

    static final int PROXIMOS_LIMITE = 8;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final TurnoRepository turnos;
    private final ListaEsperaRepository listaEspera;
    private final CentroAccessGuard centroAccess;

    public OperadorResumenService(TurnoRepository turnos, ListaEsperaRepository listaEspera,
            CentroAccessGuard centroAccess) {
        this.turnos = turnos;
        this.listaEspera = listaEspera;
        this.centroAccess = centroAccess;
    }

    @Transactional(readOnly = true)
    public OperadorResumenDTO resumen() {
        return resumen(RelojArgentina.ahora());
    }

    @Transactional(readOnly = true)
    OperadorResumenDTO resumen(LocalDateTime ahora) {
        OperadorResumenDTO dto = new OperadorResumenDTO();
        Set<Integer> centros = centroAccess.centrosPermitidos();
        if (centros == null || centros.isEmpty()) {
            return dto;
        }

        LocalDate hoy = ahora.toLocalDate();
        LocalTime hora = ahora.toLocalTime();
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        LocalDate domingo = hoy.with(DayOfWeek.SUNDAY);

        var deHoy = turnos.findByCentroAtencion_IdInAndFecha(centros, hoy);
        dto.setTurnosHoy((int) deHoy.stream().filter(turno -> turno.getEstado() != EstadoTurno.CANCELADO).count());
        dto.setPendientesConfirmar((int) turnos.countByCentroAtencion_IdInAndEstado(centros, EstadoTurno.PROGRAMADO));
        dto.setCancelacionesSemana((int) turnos.countByCentroAtencion_IdInAndEstadoAndFechaBetween(
                centros, EstadoTurno.CANCELADO, lunes, domingo));
        dto.setListaEspera((int) listaEspera.countByEstadoAndCentroAtencion_IdIn("PENDIENTE", centros));

        deHoy.stream()
                .filter(turno -> esProximo(turno, hora))
                .sorted(Comparator.comparing(Turno::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(PROXIMOS_LIMITE)
                .map(this::compacto)
                .forEach(dto.getProximosHoy()::add);
        return dto;
    }

    private boolean esProximo(Turno turno, LocalTime ahora) {
        EstadoTurno estado = turno.getEstado();
        if (estado == EstadoTurno.CANCELADO || estado == EstadoTurno.COMPLETO || estado == EstadoTurno.AUSENTE) {
            return false;
        }
        LocalTime fin = turno.getHoraFin() != null ? turno.getHoraFin() : turno.getHoraInicio();
        return fin == null || !fin.isBefore(ahora);
    }

    private OperadorResumenDTO.TurnoCompacto compacto(Turno turno) {
        OperadorResumenDTO.TurnoCompacto item = new OperadorResumenDTO.TurnoCompacto();
        item.setId(turno.getId());
        item.setHora(turno.getHoraInicio() == null ? "" : turno.getHoraInicio().format(HORA));
        item.setPaciente(nombrePaciente(turno.getPaciente()));
        item.setMedico(nombreMedico(turno.getStaffMedico()));
        item.setEspecialidad(nombreEspecialidad(turno.getEspecialidad()));
        item.setEstado(turno.getEstado() == null ? "" : turno.getEstado().name());
        return item;
    }

    private static String nombrePaciente(Paciente paciente) {
        if (paciente == null) {
            return "Sin paciente";
        }
        return unir(paciente.getNombre(), paciente.getApellido(), "Sin paciente");
    }

    private static String nombreMedico(StaffMedico staff) {
        if (staff == null || staff.getMedico() == null) {
            return "Sin médico";
        }
        Medico medico = staff.getMedico();
        return unir(medico.getNombre(), medico.getApellido(), "Sin médico");
    }

    private static String nombreEspecialidad(Especialidad especialidad) {
        if (especialidad == null || especialidad.getNombre() == null || especialidad.getNombre().isBlank()) {
            return "";
        }
        return especialidad.getNombre().trim();
    }

    private static String unir(String nombre, String apellido, String vacio) {
        String n = nombre == null ? "" : nombre.trim();
        String a = apellido == null ? "" : apellido.trim();
        String junto = (n + " " + a).trim();
        return junto.isEmpty() ? vacio : junto;
    }
}
