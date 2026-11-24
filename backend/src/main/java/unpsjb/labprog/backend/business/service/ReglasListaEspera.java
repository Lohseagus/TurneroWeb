package unpsjb.labprog.backend.business.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;

import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.DiaDeLaSemana;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.ListaEspera;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.PreferenciaHoraria;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

/**
 * Reglas de la cola, separadas del acceso a la base.
 *
 * <p>La fecha de ingreso la pone el servidor. Un paciente no elige la urgencia:
 * queda en {@link ListaEspera.UrgenciaMedica#BAJA} (prioridad normal). La pueden
 * fijar operador, administrador y superadmin, y el médico cuando la solicitud es
 * de un paciente suyo.
 *
 * <p>Un hueco liberado se ofrece si el inicio es posterior a ahora más
 * {@link #HORAS_MINIMAS_PARA_OFRECER} horas. Con menos tiempo no alcanza para
 * avisar y que alguien llegue. Una ausencia no llama a esta oferta: el médico no
 * está y ese horario no se puede volver a reservar.
 *
 * <p>Para tomar el hueco, la solicitud tiene que seguir pendiente y coincidir en
 * especialidad. Si pidió centro, médico o un rango de fechas, eso también tiene
 * que coincidir. Si el paciente cargó preferencias horarias, el horario tiene que
 * caer en alguna. Y no puede tener ya otro turno que se pise con ese.
 */
public final class ReglasListaEspera {

    public static final int HORAS_MINIMAS_PARA_OFRECER = 2;

    public static final ListaEspera.UrgenciaMedica URGENCIA_POR_DEFECTO = ListaEspera.UrgenciaMedica.BAJA;

    private ReglasListaEspera() {
    }

    public static boolean puedeElegirUrgencia(User user, boolean medicoDeEstePaciente) {
        if (user == null || user.getRole() == null || user.getRole() == Role.PACIENTE) {
            return false;
        }
        if (user.getRole().hasAccessTo(Role.OPERADOR)) {
            return true;
        }
        return user.getRole() == Role.MEDICO && medicoDeEstePaciente;
    }

    public static ListaEspera.UrgenciaMedica urgenciaEfectiva(User user, ListaEspera.UrgenciaMedica pedida,
            boolean medicoDeEstePaciente) {
        if (!puedeElegirUrgencia(user, medicoDeEstePaciente)) {
            if (user == null) {
                return pedida != null ? pedida : URGENCIA_POR_DEFECTO;
            }
            return URGENCIA_POR_DEFECTO;
        }
        return pedida != null ? pedida : URGENCIA_POR_DEFECTO;
    }

    public static ListaEspera.UrgenciaMedica parsearUrgencia(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return ListaEspera.UrgenciaMedica.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * @return true si vale la pena ofrecer el hueco. Con el inicio a menos de
     *         {@link #HORAS_MINIMAS_PARA_OFRECER} horas, o ya pasado, no.
     */
    public static boolean convieneOfrecer(LocalDateTime inicio, LocalDateTime ahora) {
        return inicio != null && ahora != null && inicio.isAfter(ahora.plusHours(HORAS_MINIMAS_PARA_OFRECER));
    }

    public static boolean esCompatible(ListaEspera solicitud, Turno hueco, Collection<Turno> turnosDelPaciente) {
        if (solicitud == null || hueco == null || !"PENDIENTE".equals(solicitud.getEstado())) {
            return false;
        }
        if (!mismoId(idEspecialidad(hueco), idEspecialidad(solicitud))) {
            return false;
        }
        if (solicitud.getCentroAtencion() != null
                && !mismoId(idCentro(hueco), solicitud.getCentroAtencion().getId())) {
            return false;
        }
        if (solicitud.getMedicoPreferido() != null
                && !mismoId(idMedico(hueco), solicitud.getMedicoPreferido().getId())) {
            return false;
        }
        if (!entraEnRango(solicitud, hueco.getFecha())) {
            return false;
        }
        if (!cumplePreferencia(solicitud.getPaciente(), hueco)) {
            return false;
        }
        return !tieneTurnoSolapado(solicitud.getPaciente(), hueco, turnosDelPaciente);
    }

    private static boolean entraEnRango(ListaEspera solicitud, LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        if (solicitud.getFechaDeseadaDesde() != null && fecha.isBefore(solicitud.getFechaDeseadaDesde())) {
            return false;
        }
        return solicitud.getFechaDeseadaHasta() == null || !fecha.isAfter(solicitud.getFechaDeseadaHasta());
    }

    private static boolean cumplePreferencia(Paciente paciente, Turno hueco) {
        if (paciente == null || paciente.getPreferenciasHorarias() == null
                || paciente.getPreferenciasHorarias().isEmpty()) {
            return true;
        }
        if (hueco.getFecha() == null || hueco.getHoraInicio() == null) {
            return false;
        }
        DiaDeLaSemana dia = diaDe(hueco.getFecha());
        if (dia == null) {
            return true;
        }
        LocalTime inicio = hueco.getHoraInicio();
        for (PreferenciaHoraria preferencia : paciente.getPreferenciasHorarias()) {
            if (preferencia == null || preferencia.getDiaDeLaSemana() != dia) {
                continue;
            }
            if (preferencia.getHoraDesde() != null && inicio.isBefore(preferencia.getHoraDesde())) {
                continue;
            }
            if (preferencia.getHoraHasta() != null && !inicio.isBefore(preferencia.getHoraHasta())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean tieneTurnoSolapado(Paciente paciente, Turno hueco, Collection<Turno> turnos) {
        if (paciente == null || paciente.getId() == null || turnos == null || hueco.getFecha() == null) {
            return false;
        }
        for (Turno otro : turnos) {
            if (otro == null || otro.getEstado() == EstadoTurno.CANCELADO) {
                continue;
            }
            if (hueco.getId() != null && hueco.getId().equals(otro.getId())) {
                continue;
            }
            if (otro.getFecha() == null || !otro.getFecha().equals(hueco.getFecha())) {
                continue;
            }
            if (sePisan(hueco.getHoraInicio(), hueco.getHoraFin(), otro.getHoraInicio(), otro.getHoraFin())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sePisan(LocalTime inicio, LocalTime fin, LocalTime otroInicio, LocalTime otroFin) {
        if (inicio == null || fin == null || otroInicio == null || otroFin == null) {
            return false;
        }
        return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
    }

    private static DiaDeLaSemana diaDe(LocalDate fecha) {
        try {
            return DiaDeLaSemana.valueOf(ReglasReservaTurno.diaEnEspanol(fecha.getDayOfWeek().name()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Integer idEspecialidad(Turno turno) {
        Especialidad especialidad = turno.getEspecialidad();
        if (especialidad == null && turno.getStaffMedico() != null) {
            especialidad = turno.getStaffMedico().getEspecialidad();
        }
        return especialidad == null ? null : especialidad.getId();
    }

    private static Integer idEspecialidad(ListaEspera solicitud) {
        return solicitud.getEspecialidad() == null ? null : solicitud.getEspecialidad().getId();
    }

    private static Integer idCentro(Turno turno) {
        if (turno.getCentroAtencion() != null) {
            return turno.getCentroAtencion().getId();
        }
        if (turno.getStaffMedico() != null && turno.getStaffMedico().getCentroAtencion() != null) {
            return turno.getStaffMedico().getCentroAtencion().getId();
        }
        CentroAtencion delConsultorio = turno.getConsultorio() == null ? null
                : turno.getConsultorio().getCentroAtencion();
        return delConsultorio == null ? null : delConsultorio.getId();
    }

    private static Integer idMedico(Turno turno) {
        Medico medico = turno.getMedico();
        if (medico == null && turno.getStaffMedico() != null) {
            medico = turno.getStaffMedico().getMedico();
        }
        return medico == null ? null : medico.getId();
    }

    private static boolean mismoId(Integer a, Integer b) {
        return a != null && a.equals(b);
    }
}
