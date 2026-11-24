package unpsjb.labprog.backend.business.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.dto.TurnoDTO;
import unpsjb.labprog.backend.exception.OverlapException;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.EsquemaTurno;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

/**
 * Reglas de reserva, reagendado, confirmación y cancelación automática.
 *
 * <p>Ventana de confirmación (por centro si hay override, si no la global):
 * se puede confirmar desde {@code dias_max_no_confirm} días antes hasta
 * {@code dias_min_confirmacion} días antes. Con los defaults, de 7 días a 2 días
 * (48 horas) antes del turno.
 *
 * <p>Zona cerrada y cancelación automática. Los dos jobs (el horario
 * {@code turnos.auto-cancel.hours-before} y el de medianoche) usan la misma
 * ventana: {@code max(horas del property, dias_min_confirmacion * 24)} del centro
 * del turno. Cancelan turnos PROGRAMADO o REAGENDADO cuyo inicio cae dentro de
 * esa ventana y todavía no empezó.
 *
 * <p>Un turno que se reserva o se reagenda dentro de esa zona nace CONFIRMADO.
 * Reservar tan cerca equivale a confirmar: la ventana ya cerró (o el job lo
 * cancelaría si siguiera sin confirmar). Por eso esos turnos no entran en la
 * cancelación automática.
 */
@Service
public class ReglasReservaTurno {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    public static final String MENSAJE_AUTOASIGNACION = "Un médico no puede asignarse turnos a sí mismo";

    private final EsquemaTurnoRepository esquemaTurnoRepository;
    private final TurnoRepository turnoRepository;
    private final ConfiguracionExcepcionalService configuracionExcepcionalService;
    private final ConfiguracionService configuracionService;
    private final PacienteAccessGuard pacienteAccess;
    private final int horasAutoCancel;

    public ReglasReservaTurno(
            EsquemaTurnoRepository esquemaTurnoRepository,
            TurnoRepository turnoRepository,
            ConfiguracionExcepcionalService configuracionExcepcionalService,
            ConfiguracionService configuracionService,
            PacienteAccessGuard pacienteAccess,
            @Value("${turnos.auto-cancel.hours-before:48}") int horasAutoCancel) {
        this.esquemaTurnoRepository = esquemaTurnoRepository;
        this.turnoRepository = turnoRepository;
        this.configuracionExcepcionalService = configuracionExcepcionalService;
        this.configuracionService = configuracionService;
        this.pacienteAccess = pacienteAccess;
        this.horasAutoCancel = horasAutoCancel <= 0 ? 48 : horasAutoCancel;
    }

    /**
     * El paciente no elige la ficha, el estado ni el sobreturno. El personal de
     * mostrador (operador, administrador, superadmin) sí puede pedir sobreturno.
     */
    public void aplicarIdentidadDeReserva(TurnoDTO dto) {
        if (dto == null) {
            return;
        }
        User user = AuditContext.getCurrentUserObject();
        if (user != null && user.getRole() == Role.PACIENTE) {
            dto.setPacienteId(pacienteAccess.pacienteParaAlta(dto.getPacienteId()));
            dto.setEstado(null);
            dto.setPermitirSolapamiento(false);
            return;
        }
        if (!puedeRegistrarSobreturno(user)) {
            dto.setPermitirSolapamiento(false);
        }
    }

    public boolean puedeRegistrarSobreturno() {
        return puedeRegistrarSobreturno(AuditContext.getCurrentUserObject());
    }

    public boolean puedeRegistrarSobreturno(User user) {
        return user != null && user.getRole() != null && user.getRole().hasAccessTo(Role.OPERADOR);
    }

    public void validarReserva(Turno turno, boolean permitirSolapamiento, LocalDateTime ahora) {
        if (turno.getFecha() == null || turno.getHoraInicio() == null || turno.getHoraFin() == null) {
            throw new IllegalArgumentException("Indicá la fecha y el horario del turno.");
        }
        if (turno.getStaffMedico() == null || turno.getStaffMedico().getId() == null) {
            throw new IllegalArgumentException("El médico es obligatorio.");
        }
        validarFuturo(turno, ahora);
        validarAgenda(turno);
        validarBloqueos(turno);
        validarNoEsElMismoMedico(turno);
        validarDobleTurnoPaciente(turno);
        validarSolapamientoMedico(turno, permitirSolapamiento);
    }

    /**
     * El turno se crea (o queda, si se reagenda) ya confirmado cuando el inicio
     * cae en la zona donde la confirmación está cerrada y el job lo cancelaría.
     */
    public boolean naceConfirmado(Turno turno, LocalDateTime ahora) {
        if (turno.getFecha() == null || turno.getHoraInicio() == null || ahora == null) {
            return false;
        }
        LocalDateTime inicio = inicio(turno);
        if (!inicio.isAfter(ahora)) {
            return false;
        }
        return !inicio.isAfter(ahora.plusHours(horasZonaCerrada(centroId(turno))));
    }

    public void validarVentanaConfirmacion(Turno turno, LocalDateTime ahora) {
        if (turno.getFecha() == null || turno.getHoraInicio() == null) {
            throw new IllegalArgumentException("El turno no tiene fecha u horario.");
        }
        LocalDateTime inicio = inicio(turno);
        if (!inicio.isAfter(ahora)) {
            throw new IllegalArgumentException("No se puede confirmar un turno que ya pasó.");
        }
        Integer centroId = centroId(turno);
        int diasMin = Math.max(1, configuracionService.getDiasMinConfirmacion(centroId));
        int diasMax = Math.max(diasMin + 1, configuracionService.getDiasMaxNoConfirm(centroId));
        LocalDateTime apertura = inicio.minusHours(diasMax * 24L);
        LocalDateTime cierre = inicio.minusHours(diasMin * 24L);
        if (ahora.isBefore(apertura)) {
            throw new IllegalArgumentException(
                    "Todavía no podés confirmar este turno. La confirmación se habilita "
                            + diasMax + " días antes.");
        }
        if (ahora.isAfter(cierre)) {
            throw new IllegalArgumentException(
                    "Ya no podés confirmar este turno. La confirmación cierra "
                            + diasMin + " días antes.");
        }
    }

    public boolean puedeConfirmar(Turno turno, LocalDateTime ahora) {
        if (turno == null || turno.getEstado() == null) {
            return false;
        }
        if (turno.getEstado() != EstadoTurno.PROGRAMADO && turno.getEstado() != EstadoTurno.REAGENDADO) {
            return false;
        }
        try {
            validarVentanaConfirmacion(turno, ahora);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Misma regla para el job horario y el de medianoche: sin confirmar, en el
     * futuro, y adentro de la zona cerrada del centro.
     */
    public boolean correspondeCancelacionAutomatica(Turno turno, LocalDateTime ahora) {
        if (turno == null || ahora == null || turno.getFecha() == null || turno.getHoraInicio() == null) {
            return false;
        }
        if (turno.getEstado() != EstadoTurno.PROGRAMADO && turno.getEstado() != EstadoTurno.REAGENDADO) {
            return false;
        }
        LocalDateTime inicio = inicio(turno);
        if (!inicio.isAfter(ahora)) {
            return false;
        }
        return !inicio.isAfter(ahora.plusHours(horasZonaCerrada(centroId(turno))));
    }

    /** Tope para traer candidatos: alcanza al centro con la ventana más larga. */
    public int horasParaBuscarCandidatos() {
        int porDias = Math.max(1, configuracionService.getMayorDiasMinConfirmacion()) * 24;
        return Math.max(horasAutoCancel, porDias);
    }

    public int horasZonaCerrada(Integer centroId) {
        int porDias = Math.max(1, configuracionService.getDiasMinConfirmacion(centroId)) * 24;
        return Math.max(horasAutoCancel, porDias);
    }

    public void validarSolapamientoMedico(Turno turno, boolean permitirSolapamiento) {
        List<Turno> existentes = turnoRepository.findByFechaAndStaffMedico_Id(
                turno.getFecha(), turno.getStaffMedico().getId());
        if (existentes == null) {
            return;
        }
        List<Turno> conflictos = new ArrayList<>();
        for (Turno otro : existentes) {
            if (otro.getEstado() == EstadoTurno.CANCELADO) {
                continue;
            }
            if (turno.getId() != null && turno.getId().equals(otro.getId())) {
                continue;
            }
            if (sePisan(turno.getHoraInicio(), turno.getHoraFin(), otro.getHoraInicio(), otro.getHoraFin())) {
                conflictos.add(otro);
            }
        }
        if (conflictos.isEmpty()) {
            return;
        }
        if (permitirSolapamiento && puedeRegistrarSobreturno()) {
            return;
        }
        if (puedeRegistrarSobreturno()) {
            List<Object> info = new ArrayList<>();
            for (Turno conflicto : conflictos) {
                String paciente = conflicto.getPaciente() == null ? ""
                        : conflicto.getPaciente().getNombre() + " " + conflicto.getPaciente().getApellido();
                info.add(Map.of(
                        "id", conflicto.getId(),
                        "horaInicio", conflicto.getHoraInicio().toString(),
                        "horaFin", conflicto.getHoraFin().toString(),
                        "paciente", paciente));
            }
            String medico = nombreMedico(turno);
            throw new OverlapException(
                    "Ya hay " + info.size() + " turno(s) en ese horario para " + medico
                            + " el " + turno.getFecha() + ".",
                    info);
        }
        throw new IllegalArgumentException("Ese horario ya está ocupado con este médico.");
    }

    private void validarFuturo(Turno turno, LocalDateTime ahora) {
        if (!inicio(turno).isAfter(ahora)) {
            throw new IllegalArgumentException("No se pueden reservar turnos en el pasado.");
        }
    }

    private void validarAgenda(Turno turno) {
        List<EsquemaTurno> esquemas = esquemaTurnoRepository.findByStaffMedicoId(turno.getStaffMedico().getId());
        if (esquemas == null) {
            esquemas = List.of();
        }
        String dia = diaEnEspanol(turno.getFecha().getDayOfWeek().name());
        Integer consultorioId = turno.getConsultorio() == null ? null : turno.getConsultorio().getId();
        for (EsquemaTurno esquema : esquemas) {
            if (esquema.getConsultorio() != null && esquema.getConsultorio().getId() != null
                    && consultorioId != null && !consultorioId.equals(esquema.getConsultorio().getId())) {
                continue;
            }
            int extra = sanitizacion(esquema, turno);
            if (esquema.getHorarios() == null) {
                continue;
            }
            for (EsquemaTurno.Horario horario : esquema.getHorarios()) {
                if (horario.getDia() == null || !horario.getDia().equalsIgnoreCase(dia)) {
                    continue;
                }
                if (encaja(horario.getHoraInicio(), horario.getHoraFin(),
                        turno.getHoraInicio(), turno.getHoraFin(), esquema.getIntervalo(), extra)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException(
                "El horario no coincide con la agenda del médico. Elegí un turno de su esquema, dentro del horario de atención.");
    }

    private void validarBloqueos(Turno turno) {
        Integer consultorioId = turno.getConsultorio() == null ? null : turno.getConsultorio().getId();
        String bloqueo = configuracionExcepcionalService.motivoBloqueoReserva(
                turno.getFecha(), turno.getHoraInicio(), turno.getHoraFin(),
                consultorioId, turno.getStaffMedico().getId());
        if (bloqueo != null) {
            throw new IllegalArgumentException(bloqueo);
        }
    }

    private void validarNoEsElMismoMedico(Turno turno) {
        Paciente paciente = turno.getPaciente();
        Medico medico = turno.getStaffMedico().getMedico();
        if (paciente == null || medico == null) {
            return;
        }
        if (mismaPersona(paciente.getDni(), paciente.getEmail(), medico.getDni(), medico.getEmail())) {
            throw new IllegalArgumentException(MENSAJE_AUTOASIGNACION);
        }
        User actual = AuditContext.getCurrentUserObject();
        if (actual == null) {
            return;
        }
        boolean elMedicoEsLaCuenta = mismaPersona(actual.getDni(), actual.getEmail(), medico.getDni(), medico.getEmail());
        boolean elPacienteEsLaCuenta = mismaPersona(actual.getDni(), actual.getEmail(), paciente.getDni(), paciente.getEmail());
        if (elMedicoEsLaCuenta && elPacienteEsLaCuenta) {
            throw new IllegalArgumentException(MENSAJE_AUTOASIGNACION);
        }
    }

    private void validarDobleTurnoPaciente(Turno turno) {
        if (turno.getPaciente() == null || turno.getPaciente().getId() == null) {
            return;
        }
        List<Turno> suyos = turnoRepository.findByPaciente_Id(turno.getPaciente().getId());
        if (suyos == null) {
            return;
        }
        for (Turno otro : suyos) {
            if (otro.getEstado() == EstadoTurno.CANCELADO) {
                continue;
            }
            if (turno.getId() != null && turno.getId().equals(otro.getId())) {
                continue;
            }
            if (otro.getFecha() == null || !otro.getFecha().equals(turno.getFecha())) {
                continue;
            }
            if (sePisan(turno.getHoraInicio(), turno.getHoraFin(), otro.getHoraInicio(), otro.getHoraFin())) {
                throw new IllegalArgumentException("Ya tenés otro turno en ese horario.");
            }
        }
    }

    private int sanitizacion(EsquemaTurno esquema, Turno turno) {
        if (esquema.getId() == null) {
            return 0;
        }
        Optional<ConfiguracionExcepcional> especial = configuracionExcepcionalService
                .obtenerAtencionEspecial(turno.getFecha(), esquema.getId());
        if (especial == null || especial.isEmpty() || especial.get().getDuracion() == null) {
            return 0;
        }
        return Math.max(0, especial.get().getDuracion());
    }

    static boolean encaja(LocalTime ventanaInicio, LocalTime ventanaFin, LocalTime inicio, LocalTime fin,
            int intervalo, int sanitizacion) {
        if (intervalo <= 0 || inicio == null || fin == null || ventanaInicio == null || ventanaFin == null) {
            return false;
        }
        if (inicio.isBefore(ventanaInicio) || fin.isAfter(ventanaFin)) {
            return false;
        }
        if (!fin.equals(inicio.plusMinutes(intervalo))) {
            return false;
        }
        long minutos = Duration.between(ventanaInicio, inicio).toMinutes();
        int paso = intervalo + Math.max(0, sanitizacion);
        return minutos >= 0 && minutos % paso == 0;
    }

    private static boolean sePisan(LocalTime inicio, LocalTime fin, LocalTime otroInicio, LocalTime otroFin) {
        return inicio != null && fin != null && otroInicio != null && otroFin != null
                && inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
    }

    private static boolean mismaPersona(Long dniA, String emailA, Long dniB, String emailB) {
        if (dniA != null && dniA.equals(dniB)) {
            return true;
        }
        return emailA != null && emailB != null && emailA.trim().equalsIgnoreCase(emailB.trim());
    }

    private static LocalDateTime inicio(Turno turno) {
        return LocalDateTime.of(turno.getFecha(), turno.getHoraInicio());
    }

    static Integer centroId(Turno turno) {
        if (turno.getCentroAtencion() != null && turno.getCentroAtencion().getId() != null) {
            return turno.getCentroAtencion().getId();
        }
        if (turno.getStaffMedico() != null && turno.getStaffMedico().getCentroAtencion() != null) {
            return turno.getStaffMedico().getCentroAtencion().getId();
        }
        if (turno.getConsultorio() != null && turno.getConsultorio().getCentroAtencion() != null) {
            return turno.getConsultorio().getCentroAtencion().getId();
        }
        return null;
    }

    private static String nombreMedico(Turno turno) {
        if (turno.getStaffMedico() == null || turno.getStaffMedico().getMedico() == null) {
            return "el médico";
        }
        Medico medico = turno.getStaffMedico().getMedico();
        return (medico.getNombre() == null ? "" : medico.getNombre()) + " "
                + (medico.getApellido() == null ? "" : medico.getApellido());
    }

    static String diaEnEspanol(String diaIngles) {
        return switch (diaIngles.toUpperCase()) {
            case "MONDAY" -> "LUNES";
            case "TUESDAY" -> "MARTES";
            case "WEDNESDAY" -> "MIERCOLES";
            case "THURSDAY" -> "JUEVES";
            case "FRIDAY" -> "VIERNES";
            case "SATURDAY" -> "SABADO";
            case "SUNDAY" -> "DOMINGO";
            default -> diaIngles;
        };
    }
}
