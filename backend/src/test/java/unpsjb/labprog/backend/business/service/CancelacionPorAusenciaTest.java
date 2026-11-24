package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.OperadorRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.repository.UserRepository;
import unpsjb.labprog.backend.model.EsquemaTurno;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.SuscripcionAccessService;

@ExtendWith(MockitoExtension.class)
class CancelacionPorAusenciaTest {

    @Mock
    private TurnoRepository repository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private StaffMedicoRepository staffMedicoRepository;
    @Mock
    private ListaEsperaService listaEsperaService;
    @Mock
    private ConsultorioRepository consultorioRepository;
    @Mock
    private EsquemaTurnoRepository esquemaTurnoRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CentroAccessGuard centroAccess;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserService userService;
    @Mock
    private OperadorRepository operadorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConfiguracionService configuracionService;
    @Mock
    private EncuestaInvitacionService encuestaInvitacionService;
    @Mock
    private SuscripcionAccessService suscripcionAccessService;
    @Mock
    private ConfiguracionExcepcionalService configuracionExcepcionalService;
    @Mock
    private ReglasReservaTurno reglasReserva;

    @InjectMocks
    private TurnoService turnoService;

    @Test
    void laAusenciaNoPideUnReemplazoDeLaListaDeEsperaYMandaElEnlaceDeAusencia() {
        Turno turno = turnoFuturo();
        when(repository.findById(15)).thenReturn(Optional.of(turno));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(centroAccess.puedeOperar(any())).thenReturn(true);
        User cuenta = new User();
        cuenta.setEmailVerified(true);
        when(userService.findByEmail("maria@turnero.com")).thenReturn(Optional.of(cuenta));

        turnoService.cancelarTurno(15, "Ausencia del médico: vacaciones", "medico@turnero.com", false, "AUSENCIA");

        verify(listaEsperaService, never()).ofrecerHuecoLiberado(any());
        verify(listaEsperaService, never()).buscarPacienteParaReasignar(any());
        verify(emailService).sendAppointmentCancellationEmail(
                eq("maria@turnero.com"), any(), any(), eq(3), eq(15), eq("AUSENCIA"));
        verify(notificacionService).crearNotificacionCancelacion(
                eq(3), eq(15), any(), any(), eq("Ausencia del médico: vacaciones"));
    }

    @Test
    void unaCancelacionComunSigueOfreciendoElHueco() {
        Turno turno = turnoFuturo();
        when(repository.findById(15)).thenReturn(Optional.of(turno));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(centroAccess.puedeOperar(any())).thenReturn(true);
        when(userService.findByEmail("maria@turnero.com")).thenReturn(Optional.empty());

        turnoService.cancelarTurno(15, "el paciente avisó", "operador@turnero.com");

        verify(listaEsperaService).ofrecerHuecoLiberado(turno);
    }

    @Test
    void laCancelacionDeMedianocheOfreceElHueco() {
        when(configuracionService.isHabilitadaCancelacionAutomatica()).thenReturn(true);
        when(reglasReserva.horasParaBuscarCandidatos()).thenReturn(48);
        when(reglasReserva.correspondeCancelacionAutomatica(any(), any())).thenReturn(true);
        Turno turno = turnoFuturo();
        when(repository.findTurnosParaCancelacionAutomatica(any(), any(), any())).thenReturn(List.of(turno));
        when(repository.findById(15)).thenReturn(Optional.of(turno));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(centroAccess.puedeOperar(any())).thenReturn(true);
        when(userService.findByEmail("maria@turnero.com")).thenReturn(Optional.empty());

        turnoService.cancelarTurnosNoConfirmadosAutomaticamente();

        verify(listaEsperaService).ofrecerHuecoLiberado(turno);
    }

    @Test
    void unaReservaNuevaEnUnDiaBloqueadoSeRechaza() throws Exception {
        EsquemaTurno esquema = new EsquemaTurno();
        EsquemaTurno.Horario horario = new EsquemaTurno.Horario();
        horario.setDia("LUNES");
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFin(LocalTime.of(18, 0));
        esquema.setHorarios(List.of(horario));
        when(esquemaTurnoRepository.findByStaffMedicoId(8)).thenReturn(List.of(esquema));
        when(configuracionExcepcionalService.motivoBloqueoReserva(any(), any(), any(), any(), any()))
                .thenReturn("No se pueden reservar turnos: el médico está ausente en ese horario");

        Turno turno = new Turno();
        turno.setFecha(LocalDate.of(2026, 12, 7));
        turno.setHoraInicio(LocalTime.of(10, 0));
        turno.setHoraFin(LocalTime.of(10, 30));
        StaffMedico staff = new StaffMedico();
        staff.setId(8);
        Medico medico = new Medico();
        medico.setNombre("Juan");
        medico.setApellido("Pérez");
        staff.setMedico(medico);
        turno.setStaffMedico(staff);

        Method metodo = TurnoService.class.getDeclaredMethod("validarDisponibilidadMedico", Turno.class);
        metodo.setAccessible(true);
        Exception error = assertThrows(Exception.class, () -> metodo.invoke(turnoService, turno));
        assertEquals("No se pueden reservar turnos: el médico está ausente en ese horario",
                error.getCause().getMessage());
    }

    private static Turno turnoFuturo() {
        Paciente paciente = new Paciente();
        paciente.setId(3);
        paciente.setNombre("María");
        paciente.setApellido("González");
        paciente.setEmail("maria@turnero.com");
        Turno turno = new Turno();
        turno.setId(15);
        turno.setFecha(LocalDate.now(AusenciaMedicoService.ZONA).plusDays(4));
        turno.setHoraInicio(LocalTime.of(10, 0));
        turno.setHoraFin(LocalTime.of(10, 30));
        turno.setEstado(EstadoTurno.PROGRAMADO);
        turno.setPaciente(paciente);
        return turno;
    }
}
