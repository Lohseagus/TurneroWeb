package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.TurnoDTO;
import unpsjb.labprog.backend.exception.OverlapException;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Consultorio;
import unpsjb.labprog.backend.model.EsquemaTurno;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

@ExtendWith(MockitoExtension.class)
class ReglasReservaTurnoTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 12, 7);
    private static final LocalDateTime ANTES = LocalDateTime.of(2026, 11, 20, 9, 0);

    @Mock private EsquemaTurnoRepository esquemas;
    @Mock private TurnoRepository turnos;
    @Mock private ConfiguracionExcepcionalService excepciones;
    @Mock private ConfiguracionService configuracion;
    @Mock private PacienteAccessGuard pacientes;

    private ReglasReservaTurno reglas;

    @BeforeEach
    void setUp() {
        reglas = new ReglasReservaTurno(esquemas, turnos, excepciones, configuracion, pacientes, 48);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elPacienteNoPuedeElegirOtraFichaNiElEstadoNiElSobreturno() {
        autenticar(Role.PACIENTE, "ana@test.com", 30111222L);
        when(pacientes.pacienteParaAlta(any())).thenReturn(7);
        TurnoDTO dto = new TurnoDTO();
        dto.setPacienteId(99);
        dto.setEstado("CONFIRMADO");
        dto.setPermitirSolapamiento(true);

        reglas.aplicarIdentidadDeReserva(dto);

        assertEquals(7, dto.getPacienteId());
        assertEquals(null, dto.getEstado());
        assertEquals(false, dto.getPermitirSolapamiento());
    }

    @Test
    void elMedicoNoPuedePedirSobreturnoYElOperadorSi() {
        autenticar(Role.MEDICO, "medico@test.com", 20111000L);
        TurnoDTO delMedico = pedido(4, true, "PROGRAMADO");
        reglas.aplicarIdentidadDeReserva(delMedico);
        assertEquals(4, delMedico.getPacienteId());
        assertEquals("PROGRAMADO", delMedico.getEstado());
        assertEquals(false, delMedico.getPermitirSolapamiento());

        autenticar(Role.OPERADOR, "operador@test.com", 20111001L);
        TurnoDTO delOperador = pedido(4, true, "PROGRAMADO");
        reglas.aplicarIdentidadDeReserva(delOperador);
        assertEquals(true, delOperador.getPermitirSolapamiento());

        autenticar(Role.ADMINISTRADOR, "admin@test.com", 20111002L);
        assertTrue(reglas.puedeRegistrarSobreturno());
        autenticar(Role.SUPERADMIN, "root@test.com", 20111003L);
        assertTrue(reglas.puedeRegistrarSobreturno());
    }

    @Test
    void rechazaUnaFechaPasadaYUnHorarioFueraDeLaAgenda() {
        Turno pasado = turno(LUNES.minusDays(1), LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertTrue(mensaje(pasado, false, LUNES.atTime(18, 0)).contains("pasado"));

        agendaDeLunes();
        Turno corrido = turno(LUNES, LocalTime.of(9, 15), LocalTime.of(9, 45));
        assertTrue(mensaje(corrido, false, ANTES).contains("agenda del médico"));
    }

    @Test
    void aceptaElTurnoAlineadoAlEsquema() {
        agendaDeLunes();
        sinCruces();
        reglas.validarReserva(turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30)), false, ANTES);
    }

    @Test
    void rechazaFeriadoAusenciaOMantenimiento() {
        agendaDeLunes();
        when(excepciones.motivoBloqueoReserva(any(), any(), any(), any(), any()))
                .thenReturn("No se pueden reservar turnos en un feriado");
        assertEquals("No se pueden reservar turnos en un feriado",
                mensaje(turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30)), false, ANTES));
    }

    @Test
    void rechazaSolapamientoDelMedicoYDobleTurnoDelPaciente() {
        agendaDeLunes();
        when(excepciones.motivoBloqueoReserva(any(), any(), any(), any(), any())).thenReturn(null);
        when(excepciones.obtenerAtencionEspecial(any(), anyInt())).thenReturn(Optional.empty());

        Turno ocupado = turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30));
        ocupado.setId(50);
        ocupado.getPaciente().setNombre("Otro");
        ocupado.getPaciente().setApellido("Paciente");
        when(turnos.findByFechaAndStaffMedico_Id(LUNES, 8)).thenReturn(List.of(ocupado));
        when(turnos.findByPaciente_Id(3)).thenReturn(List.of());
        autenticar(Role.OPERADOR, "operador@test.com", 1L);
        assertThrows(OverlapException.class, () -> reglas.validarReserva(
                turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30)), false, ANTES));

        reglas.validarReserva(turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30)), true, ANTES);

        SecurityContextHolder.clearContext();
        assertTrue(mensaje(turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30)), false, ANTES)
                .contains("ocupado"));

        lenient().when(turnos.findByFechaAndStaffMedico_Id(LUNES, 8)).thenReturn(List.of());
        Turno propio = turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30));
        propio.setId(80);
        when(turnos.findByPaciente_Id(3)).thenReturn(List.of(propio));
        assertTrue(mensaje(turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30)), false, ANTES)
                .contains("Ya tenés otro turno"));
    }

    @Test
    void unMedicoNoPuedeAsignarseTurnosASiMismo() {
        agendaDeLunes();
        sinCruces();
        Turno mismoDni = turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30));
        mismoDni.getPaciente().setDni(mismoDni.getStaffMedico().getMedico().getDni());
        assertEquals(ReglasReservaTurno.MENSAJE_AUTOASIGNACION, mensaje(mismoDni, false, ANTES));

        Turno mismoMail = turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30));
        mismoMail.getPaciente().setEmail(mismoMail.getStaffMedico().getMedico().getEmail());
        assertEquals(ReglasReservaTurno.MENSAJE_AUTOASIGNACION, mensaje(mismoMail, false, ANTES));
    }

    @Test
    void laConfirmacionSoloEntraEntreDosYSieteDias() {
        when(configuracion.getDiasMinConfirmacion(any())).thenReturn(2);
        when(configuracion.getDiasMaxNoConfirm(any())).thenReturn(7);
        Turno turno = turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30));
        LocalDateTime inicio = LocalDateTime.of(LUNES, LocalTime.of(10, 0));

        assertTrue(mensajeConfirmacion(turno, inicio.minusDays(10)).contains("se habilita 7 días antes"));
        reglas.validarVentanaConfirmacion(turno, inicio.minusDays(4));
        assertTrue(reglas.puedeConfirmar(turno, inicio.minusDays(4)));
        assertTrue(mensajeConfirmacion(turno, inicio.minusHours(30)).contains("cierra 2 días antes"));
        assertFalse(reglas.puedeConfirmar(turno, inicio.minusHours(30)));
        assertTrue(mensajeConfirmacion(turno, inicio.plusHours(1)).contains("ya pasó"));
    }

    @Test
    void unTurnoReservadoDentroDe48HorasNaceConfirmadoYNoSeAutoCancela() {
        when(configuracion.getDiasMinConfirmacion(any())).thenReturn(2);
        Turno cerca = turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30));
        LocalDateTime ahora = LocalDateTime.of(LUNES, LocalTime.of(10, 0)).minusHours(20);
        assertTrue(reglas.naceConfirmado(cerca, ahora));
        cerca.setEstado(EstadoTurno.CONFIRMADO);
        assertFalse(reglas.correspondeCancelacionAutomatica(cerca, ahora));

        Turno lejos = turno(LUNES, LocalTime.of(10, 0), LocalTime.of(10, 30));
        lejos.setEstado(EstadoTurno.PROGRAMADO);
        assertFalse(reglas.naceConfirmado(lejos, ANTES));
        assertFalse(reglas.correspondeCancelacionAutomatica(lejos, ANTES));

        lejos.setEstado(EstadoTurno.PROGRAMADO);
        assertTrue(reglas.correspondeCancelacionAutomatica(lejos, ahora));
        lejos.setEstado(EstadoTurno.REAGENDADO);
        assertTrue(reglas.correspondeCancelacionAutomatica(lejos, ahora));
    }

    @Test
    void laReprogramacionUsaLaMismaValidacionDeAgendaYSolapamiento() {
        agendaDeLunes();
        when(excepciones.motivoBloqueoReserva(any(), any(), any(), any(), any())).thenReturn(null);
        when(turnos.findByPaciente_Id(3)).thenReturn(List.of());

        Turno reprogramado = turno(LUNES, LocalTime.of(18, 0), LocalTime.of(18, 30));
        assertTrue(mensaje(reprogramado, false, ANTES).contains("agenda del médico"));

        Turno ocupado = turno(LUNES, LocalTime.of(11, 0), LocalTime.of(11, 30));
        ocupado.setId(90);
        when(turnos.findByFechaAndStaffMedico_Id(LUNES, 8)).thenReturn(List.of(ocupado));
        assertTrue(mensaje(turno(LUNES, LocalTime.of(11, 0), LocalTime.of(11, 30)), false, ANTES).contains("ocupado"));
    }

    @Test
    void laMaquinaDeEstadosIncluyeAusenteYLosFinalesNoSalen() {
        assertTrue(TurnoService.transicionesPermitidas(EstadoTurno.PROGRAMADO).contains(EstadoTurno.AUSENTE));
        assertTrue(TurnoService.transicionesPermitidas(EstadoTurno.CONFIRMADO).contains(EstadoTurno.AUSENTE));
        assertTrue(TurnoService.transicionesPermitidas(EstadoTurno.CONFIRMADO).contains(EstadoTurno.COMPLETO));
        assertTrue(TurnoService.transicionesPermitidas(EstadoTurno.REAGENDADO).contains(EstadoTurno.CONFIRMADO));
        assertFalse(TurnoService.transicionesPermitidas(EstadoTurno.REAGENDADO).contains(EstadoTurno.AUSENTE));
        assertTrue(TurnoService.transicionesPermitidas(EstadoTurno.AUSENTE).isEmpty());
        assertTrue(TurnoService.transicionesPermitidas(EstadoTurno.CANCELADO).isEmpty());
        assertTrue(TurnoService.transicionesPermitidas(EstadoTurno.COMPLETO).isEmpty());
    }

    private void agendaDeLunes() {
        EsquemaTurno esquema = new EsquemaTurno();
        esquema.setId(1);
        esquema.setIntervalo(30);
        Consultorio consultorio = new Consultorio();
        consultorio.setId(4);
        esquema.setConsultorio(consultorio);
        EsquemaTurno.Horario horario = new EsquemaTurno.Horario();
        horario.setDia("LUNES");
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFin(LocalTime.of(12, 0));
        esquema.setHorarios(List.of(horario));
        lenient().when(esquemas.findByStaffMedicoId(8)).thenReturn(List.of(esquema));
        lenient().when(excepciones.obtenerAtencionEspecial(any(), anyInt())).thenReturn(Optional.empty());
    }

    private void sinCruces() {
        lenient().when(excepciones.motivoBloqueoReserva(any(), any(), any(), any(), any())).thenReturn(null);
        lenient().when(excepciones.obtenerAtencionEspecial(any(), anyInt())).thenReturn(Optional.empty());
        lenient().when(turnos.findByFechaAndStaffMedico_Id(any(), any())).thenReturn(List.of());
        lenient().when(turnos.findByPaciente_Id(3)).thenReturn(List.of());
    }

    private String mensaje(Turno turno, boolean sobreturno, LocalDateTime ahora) {
        return assertThrows(IllegalArgumentException.class,
                () -> reglas.validarReserva(turno, sobreturno, ahora)).getMessage();
    }

    private String mensajeConfirmacion(Turno turno, LocalDateTime ahora) {
        return assertThrows(IllegalArgumentException.class,
                () -> reglas.validarVentanaConfirmacion(turno, ahora)).getMessage();
    }

    private static TurnoDTO pedido(int pacienteId, boolean solapar, String estado) {
        TurnoDTO dto = new TurnoDTO();
        dto.setPacienteId(pacienteId);
        dto.setPermitirSolapamiento(solapar);
        dto.setEstado(estado);
        return dto;
    }

    private static Turno turno(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        Consultorio consultorio = new Consultorio();
        consultorio.setId(4);
        consultorio.setCentroAtencion(centro);
        Medico medico = new Medico();
        medico.setId(2);
        medico.setNombre("Juan");
        medico.setApellido("Pérez");
        medico.setDni(20000000L);
        medico.setEmail("medico@test.com");
        StaffMedico staff = new StaffMedico();
        staff.setId(8);
        staff.setMedico(medico);
        staff.setCentroAtencion(centro);
        Paciente paciente = new Paciente();
        paciente.setId(3);
        paciente.setNombre("Ana");
        paciente.setApellido("López");
        paciente.setDni(30111222L);
        paciente.setEmail("ana@test.com");
        Turno turno = new Turno();
        turno.setFecha(fecha);
        turno.setHoraInicio(inicio);
        turno.setHoraFin(fin);
        turno.setEstado(EstadoTurno.PROGRAMADO);
        turno.setStaffMedico(staff);
        turno.setConsultorio(consultorio);
        turno.setCentroAtencion(centro);
        turno.setPaciente(paciente);
        return turno;
    }

    private static void autenticar(Role rol, String email, Long dni) {
        User user = new User("Nombre", "Apellido", dni, email, "x", "+5492804000000", rol);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
