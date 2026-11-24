package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.DiaDeLaSemana;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.ListaEspera;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.PreferenciaHoraria;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

class ReglasListaEsperaTest {

    @Test
    void elOrdenEsUrgenciaDespuesIngresoYReputacion() {
        ListaEspera urgenteTarde = solicitud(1, ListaEspera.UrgenciaMedica.URGENTE, hace(1), 0);
        ListaEspera bajaVieja = solicitud(2, ListaEspera.UrgenciaMedica.BAJA, hace(30), 99);
        ListaEspera alta = solicitud(3, ListaEspera.UrgenciaMedica.ALTA, hace(2), 0);
        ListaEspera bajaNueva = solicitud(4, ListaEspera.UrgenciaMedica.BAJA, hace(1), 10);
        ListaEspera bajaMismaFechaMejor = solicitud(5, ListaEspera.UrgenciaMedica.BAJA, hace(30), 5);
        bajaVieja.getPaciente().setReputacion(1);

        List<ListaEspera> ordenadas = List.of(bajaNueva, bajaVieja, alta, urgenteTarde, bajaMismaFechaMejor)
                .stream().sorted(OrdenListaEspera.COMPARADOR).toList();

        assertEquals(List.of(1L, 3L, 5L, 2L, 4L), ordenadas.stream().map(ListaEspera::getId).toList());
    }

    @Test
    void laReputacionNulaEmpataEnCero() {
        ListaEspera sinPuntaje = solicitud(1, ListaEspera.UrgenciaMedica.MEDIA, hace(3), null);
        ListaEspera enCero = solicitud(2, ListaEspera.UrgenciaMedica.MEDIA, hace(3), 0);
        assertEquals(0, OrdenListaEspera.comparar(sinPuntaje, enCero));
        assertTrue(OrdenListaEspera.comparar(sinPuntaje, solicitud(3, ListaEspera.UrgenciaMedica.MEDIA, hace(3), 2)) > 0);
    }

    @Test
    void elPacienteNoEligeUrgenciaYElMostradorSi() {
        User paciente = usuario(Role.PACIENTE);
        User operador = usuario(Role.OPERADOR);
        User admin = usuario(Role.ADMINISTRADOR);
        assertEquals(ListaEspera.UrgenciaMedica.BAJA,
                ReglasListaEspera.urgenciaEfectiva(paciente, ListaEspera.UrgenciaMedica.URGENTE, false));
        assertEquals(ListaEspera.UrgenciaMedica.URGENTE,
                ReglasListaEspera.urgenciaEfectiva(operador, ListaEspera.UrgenciaMedica.URGENTE, false));
        assertEquals(ListaEspera.UrgenciaMedica.ALTA,
                ReglasListaEspera.urgenciaEfectiva(admin, ListaEspera.UrgenciaMedica.ALTA, false));
        assertFalse(ReglasListaEspera.puedeElegirUrgencia(paciente, true));
        assertTrue(ReglasListaEspera.puedeElegirUrgencia(usuario(Role.MEDICO), true));
        assertFalse(ReglasListaEspera.puedeElegirUrgencia(usuario(Role.MEDICO), false));
    }

    @Test
    void unHuecoMuyProximoNoSeOfrece() {
        LocalDateTime ahora = LocalDateTime.of(2026, 12, 1, 10, 0);
        assertFalse(ReglasListaEspera.convieneOfrecer(ahora.plusHours(2), ahora));
        assertFalse(ReglasListaEspera.convieneOfrecer(ahora.minusMinutes(5), ahora));
        assertTrue(ReglasListaEspera.convieneOfrecer(ahora.plusHours(2).plusMinutes(1), ahora));
    }

    @Test
    void salteaCentroMedicoRangoPreferenciaYSolape() {
        Turno hueco = hueco(LocalDate.of(2026, 12, 7), LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertTrue(ReglasListaEspera.esCompatible(pendiente(1, 1, null, null, null), hueco, List.of()));

        ListaEspera otroCentro = pendiente(1, 9, null, null, null);
        assertFalse(ReglasListaEspera.esCompatible(otroCentro, hueco, List.of()));

        ListaEspera sinCentro = pendiente(1, null, null, null, null);
        assertTrue(ReglasListaEspera.esCompatible(sinCentro, hueco, List.of()));

        ListaEspera otroMedico = pendiente(1, 1, 99, null, null);
        assertFalse(ReglasListaEspera.esCompatible(otroMedico, hueco, List.of()));

        ListaEspera fueraDeRango = pendiente(1, 1, null, LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9));
        assertFalse(ReglasListaEspera.esCompatible(fueraDeRango, hueco, List.of()));

        ListaEspera deTarde = pendiente(1, 1, null, null, null);
        PreferenciaHoraria tarde = new PreferenciaHoraria();
        tarde.setDiaDeLaSemana(DiaDeLaSemana.LUNES);
        tarde.setHoraDesde(LocalTime.of(18, 0));
        tarde.setHoraHasta(LocalTime.of(20, 0));
        deTarde.getPaciente().getPreferenciasHorarias().add(tarde);
        assertFalse(ReglasListaEspera.esCompatible(deTarde, hueco, List.of()));

        ListaEspera conTurno = pendiente(1, 1, null, null, null);
        Turno ocupado = new Turno();
        ocupado.setId(50);
        ocupado.setFecha(hueco.getFecha());
        ocupado.setHoraInicio(LocalTime.of(10, 15));
        ocupado.setHoraFin(LocalTime.of(10, 45));
        ocupado.setEstado(EstadoTurno.CONFIRMADO);
        assertFalse(ReglasListaEspera.esCompatible(conTurno, hueco, List.of(ocupado)));

        ListaEspera otraEspecialidad = pendiente(2, 1, null, null, null);
        assertFalse(ReglasListaEspera.esCompatible(otraEspecialidad, hueco, List.of()));
    }

    private static ListaEspera solicitud(long id, ListaEspera.UrgenciaMedica urgencia, LocalDateTime ingreso,
            Integer reputacion) {
        Paciente paciente = new Paciente();
        paciente.setId((int) id);
        paciente.setReputacion(reputacion);
        ListaEspera solicitud = new ListaEspera();
        solicitud.setId(id);
        solicitud.setPaciente(paciente);
        solicitud.setUrgenciaMedica(urgencia);
        solicitud.setFechaSolicitud(ingreso);
        solicitud.setEstado("PENDIENTE");
        return solicitud;
    }

    private static LocalDateTime hace(int dias) {
        return LocalDateTime.of(2026, 11, 1, 9, 0).minusDays(dias);
    }

    private static User usuario(Role rol) {
        User user = new User();
        user.setRole(rol);
        user.setEmail(rol.name().toLowerCase() + "@turnero.com");
        return user;
    }

    private static ListaEspera pendiente(int especialidadId, Integer centroId, Integer medicoId,
            LocalDate desde, LocalDate hasta) {
        Especialidad especialidad = new Especialidad();
        especialidad.setId(especialidadId);
        especialidad.setNombre("Clinica");
        Paciente paciente = new Paciente();
        paciente.setId(7);
        ListaEspera solicitud = new ListaEspera();
        solicitud.setId(4L);
        solicitud.setEstado("PENDIENTE");
        solicitud.setUrgenciaMedica(ListaEspera.UrgenciaMedica.BAJA);
        solicitud.setFechaSolicitud(LocalDateTime.of(2026, 11, 1, 9, 0));
        solicitud.setEspecialidad(especialidad);
        solicitud.setPaciente(paciente);
        solicitud.setFechaDeseadaDesde(desde);
        solicitud.setFechaDeseadaHasta(hasta);
        if (centroId != null) {
            CentroAtencion centro = new CentroAtencion();
            centro.setId(centroId);
            solicitud.setCentroAtencion(centro);
        }
        if (medicoId != null) {
            Medico medico = new Medico();
            medico.setId(medicoId);
            solicitud.setMedicoPreferido(medico);
        }
        return solicitud;
    }

    private static Turno hueco(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        Especialidad especialidad = new Especialidad();
        especialidad.setId(1);
        especialidad.setNombre("Clinica");
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setNombre("Centro");
        Medico medico = new Medico();
        medico.setId(4);
        medico.setNombre("Juan");
        medico.setApellido("Paz");
        StaffMedico staff = new StaffMedico();
        staff.setId(8);
        staff.setEspecialidad(especialidad);
        staff.setCentroAtencion(centro);
        staff.setMedico(medico);
        Turno turno = new Turno();
        turno.setId(15);
        turno.setFecha(fecha);
        turno.setHoraInicio(inicio);
        turno.setHoraFin(fin);
        turno.setEstado(EstadoTurno.CANCELADO);
        turno.setEspecialidad(especialidad);
        turno.setCentroAtencion(centro);
        turno.setMedico(medico);
        turno.setStaffMedico(staff);
        return turno;
    }
}
