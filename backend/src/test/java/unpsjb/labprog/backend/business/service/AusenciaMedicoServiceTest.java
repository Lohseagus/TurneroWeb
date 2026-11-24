package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.AusenciaResultadoDTO;
import unpsjb.labprog.backend.dto.AusenciaSolicitudDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.AuditLog;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.ReglaSuscripcionException;
import unpsjb.labprog.backend.saas.ResultadoEscritura;
import unpsjb.labprog.backend.saas.SaasEnforcement;
import unpsjb.labprog.backend.saas.SuscripcionAccessService;

@ExtendWith(MockitoExtension.class)
class AusenciaMedicoServiceTest {

    @Mock
    private ConfiguracionExcepcionalRepository repository;
    @Mock
    private StaffMedicoRepository staffMedicoRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private TurnoService turnoService;
    @Mock
    private SuscripcionAccessService suscripcionAccessService;
    @Mock
    private SaasEnforcement saasEnforcement;
    @Mock
    private ConfiguracionService configuracionService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AusenciaMedicoService service;

    private StaffMedico staff;
    private LocalDate hoy;

    @BeforeEach
    void setUp() {
        hoy = LocalDate.now(AusenciaMedicoService.ZONA);
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setNombre("Clínica Santa María");
        staff = new StaffMedico();
        staff.setId(8);
        staff.setCentroAtencion(centro);
        lenient().when(configuracionService.getDiasMaxNoConfirm(1)).thenReturn(0);
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cancelaLosTurnosFuturosYNoOfreceElHuecoALaListaDeEspera() {
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        when(repository.findAusenciasVigentes(any(), any(), any())).thenReturn(List.of());
        Turno futuro = turno(20, hoy.plusDays(5), EstadoTurno.CONFIRMADO);
        Turno reagendado = turno(23, hoy.plusDays(4), EstadoTurno.REAGENDADO);
        Turno hoyMismo = turno(21, hoy, EstadoTurno.PROGRAMADO);
        Turno pasado = turno(22, hoy.minusDays(2), EstadoTurno.PROGRAMADO);
        when(turnoRepository.findAfectablesPorAusencia(eq(8), eq(hoy.plusDays(1)), eq(hoy.plusDays(6)), any()))
                .thenReturn(List.of(futuro, reagendado, hoyMismo, pasado));
        when(repository.save(any())).thenAnswer(invocation -> {
            ConfiguracionExcepcional guardada = invocation.getArgument(0);
            guardada.setId(70);
            return guardada;
        });

        AusenciaResultadoDTO resultado = service.confirmar(solicitud(hoy.plusDays(1), hoy.plusDays(6)), "medico@turnero.com");

        verify(turnoService).cancelarTurno(
                eq(20), eq("Ausencia del médico: vacaciones"), eq("medico@turnero.com"), eq(false),
                eq(AusenciaMedicoService.TIPO_DEEP_LINK));
        verify(turnoService).cancelarTurno(
                eq(23), eq("Ausencia del médico: vacaciones"), eq("medico@turnero.com"), eq(false),
                eq(AusenciaMedicoService.TIPO_DEEP_LINK));
        verify(turnoService, never()).cancelarTurno(eq(21), any(), any(), anyBoolean(), any());
        verify(turnoService, never()).cancelarTurno(eq(22), any(), any(), anyBoolean(), any());
        org.mockito.ArgumentCaptor<java.util.Collection<EstadoTurno>> estados = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(turnoRepository).findAfectablesPorAusencia(eq(8), eq(hoy.plusDays(1)), eq(hoy.plusDays(6)), estados.capture());
        assertTrue(estados.getValue().contains(EstadoTurno.REAGENDADO));
        assertTrue(estados.getValue().contains(EstadoTurno.CONFIRMADO));
        assertTrue(estados.getValue().contains(EstadoTurno.PROGRAMADO));
        assertEquals(2, resultado.getCancelados().size());
        assertEquals(2, resultado.getRequierenGestionManual().size());
        assertEquals(70, resultado.getAusencia().getId());
    }

    @Test
    void laVistaPreviaNoCancelaNiGuarda() {
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        when(turnoRepository.findAfectablesPorAusencia(any(), any(), any(), any()))
                .thenReturn(List.of(turno(20, hoy.plusDays(4), EstadoTurno.PROGRAMADO)));

        AusenciaResultadoDTO resultado = service.previsualizar(solicitud(hoy.plusDays(3), hoy.plusDays(4)));

        assertEquals(1, resultado.getCancelados().size());
        verify(turnoService, never()).cancelarTurno(any(), any(), any(), anyBoolean(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void conElInterruptorApagadoLaSuscripcionNoCortaLaAusencia() {
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        when(saasEnforcement.habilitado()).thenReturn(false);
        when(repository.findAusenciasVigentes(any(), any(), any())).thenReturn(List.of());
        when(turnoRepository.findAfectablesPorAusencia(any(), any(), any(), any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.confirmar(solicitud(hoy.plusDays(2), hoy.plusDays(2)), "medico@turnero.com");

        verify(suscripcionAccessService, never()).evaluarEscritura(any());
        verify(repository).save(any());
    }

    @Test
    void conElInterruptorPrendidoUnaSuscripcionEnSoloLecturaNoDejaCargarLaAusencia() {
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        when(saasEnforcement.habilitado()).thenReturn(true);
        when(suscripcionAccessService.evaluarEscritura(1))
                .thenReturn(ResultadoEscritura.bloquear("La suscripción está suspendida. La cuenta es de solo lectura.", false));

        ReglaSuscripcionException error = assertThrows(ReglaSuscripcionException.class,
                () -> service.confirmar(solicitud(hoy.plusDays(2), hoy.plusDays(2)), "medico@turnero.com"));

        assertTrue(error.getMessage().contains("solo lectura"));
        verify(repository, never()).save(any());
        verify(turnoService, never()).cancelarTurno(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void elMedicoNoPuedeCargarUnaAusenciaConMenosDiasQueElMinimo() {
        autenticar(Role.MEDICO);
        when(configuracionService.getDiasMaxNoConfirm(1)).thenReturn(7);
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.confirmar(solicitud(hoy.plusDays(6), hoy.plusDays(6)), "medico@turnero.com"));

        assertTrue(error.getMessage().contains("al menos 7 días"));
        assertTrue(error.getMessage().contains("administración del centro"));
        verify(repository, never()).save(any());
    }

    @Test
    void elMedicoPuedeCargarUnaAusenciaJustoEnElMinimo() {
        autenticar(Role.MEDICO);
        when(configuracionService.getDiasMaxNoConfirm(1)).thenReturn(7);
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        when(repository.findAusenciasVigentes(any(), any(), any())).thenReturn(List.of());
        when(turnoRepository.findAfectablesPorAusencia(any(), any(), any(), any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            ConfiguracionExcepcional guardada = invocation.getArgument(0);
            guardada.setId(71);
            return guardada;
        });

        service.confirmar(solicitud(hoy.plusDays(7), hoy.plusDays(7)), "medico@turnero.com");

        ArgumentCaptor<ConfiguracionExcepcional> captor = ArgumentCaptor.forClass(ConfiguracionExcepcional.class);
        verify(repository).save(captor.capture());
        assertEquals(false, captor.getValue().isUrgente());
    }

    @Test
    void laAdministracionCargaUnaUrgenciaConMotivo() {
        autenticar(Role.ADMINISTRADOR);
        when(configuracionService.getDiasMaxNoConfirm(1)).thenReturn(7);
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        when(repository.findAusenciasVigentes(any(), any(), any())).thenReturn(List.of());
        when(turnoRepository.findAfectablesPorAusencia(any(), any(), any(), any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            ConfiguracionExcepcional guardada = invocation.getArgument(0);
            guardada.setId(72);
            return guardada;
        });
        AusenciaSolicitudDTO solicitud = solicitud(hoy.plusDays(2), hoy.plusDays(2));
        solicitud.setUrgente(true);
        solicitud.setMotivo("Guardia imprevista");

        service.confirmar(solicitud, "admin@turnero.com");

        ArgumentCaptor<ConfiguracionExcepcional> captor = ArgumentCaptor.forClass(ConfiguracionExcepcional.class);
        verify(repository).save(captor.capture());
        assertEquals(true, captor.getValue().isUrgente());
        assertEquals("Guardia imprevista", captor.getValue().getDescripcion());
        verify(auditLogService).logGenericAction(eq("AUSENCIA_MEDICO"), eq(72L), eq(AuditLog.Actions.CREATE),
                eq("admin@turnero.com"), any(), eq("URGENTE"), any(), any(), eq("Guardia imprevista"));
    }

    @Test
    void laUrgenciaSinMotivoSeRechaza() {
        autenticar(Role.OPERADOR);
        when(configuracionService.getDiasMaxNoConfirm(1)).thenReturn(7);
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        AusenciaSolicitudDTO solicitud = solicitud(hoy.plusDays(1), hoy.plusDays(1));
        solicitud.setUrgente(true);
        solicitud.setMotivo("  ");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.confirmar(solicitud, "operador@turnero.com"));

        assertTrue(error.getMessage().contains("motivo"));
        verify(repository, never()).save(any());
    }

    @Test
    void elMedicoNoPuedeSaltearElPlazoConElFlagDeUrgencia() {
        autenticar(Role.MEDICO);
        when(configuracionService.getDiasMaxNoConfirm(1)).thenReturn(7);
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        AusenciaSolicitudDTO solicitud = solicitud(hoy.plusDays(6), hoy.plusDays(8));
        solicitud.setUrgente(true);
        solicitud.setMotivo("Quiero salir igual");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.confirmar(solicitud, "medico@turnero.com"));

        assertTrue(error.getMessage().contains("al menos 7 días"));
        verify(repository, never()).save(any());
    }

    @Test
    void moverLaFechaDeInicioAdentroDelPlazoSeRechaza() {
        autenticar(Role.MEDICO);
        when(configuracionService.getDiasMaxNoConfirm(1)).thenReturn(7);
        ConfiguracionExcepcional existente = new ConfiguracionExcepcional();
        existente.setId(9);
        existente.setActivo(true);
        existente.setTipo(ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO);
        existente.setFecha(hoy.plusDays(20));
        existente.setFechaFin(hoy.plusDays(22));
        existente.setStaffMedico(staff);
        when(repository.findById(9)).thenReturn(Optional.of(existente));
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));

        AusenciaSolicitudDTO solicitud = solicitud(hoy.plusDays(3), hoy.plusDays(4));
        assertThrows(IllegalArgumentException.class,
                () -> service.actualizar(9, solicitud, "medico@turnero.com"));
        verify(repository, never()).save(any());
    }

    private void autenticar(Role rol) {
        User user = new User();
        user.setEmail("quien@turnero.com");
        user.setRole(rol);
        user.setHashedPassword("x");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private AusenciaSolicitudDTO solicitud(LocalDate inicio, LocalDate fin) {
        AusenciaSolicitudDTO solicitud = new AusenciaSolicitudDTO();
        solicitud.setStaffMedicoId(8);
        solicitud.setCentroAtencionId(1);
        solicitud.setFechaInicio(inicio);
        solicitud.setFechaFin(fin);
        solicitud.setMotivo("vacaciones");
        return solicitud;
    }

    private static Turno turno(int id, LocalDate fecha, EstadoTurno estado) {
        Paciente paciente = new Paciente();
        paciente.setId(3);
        paciente.setNombre("María");
        paciente.setApellido("González");
        Turno turno = new Turno();
        turno.setId(id);
        turno.setFecha(fecha);
        turno.setHoraInicio(LocalTime.of(10, 0));
        turno.setHoraFin(LocalTime.of(10, 30));
        turno.setEstado(estado);
        turno.setPaciente(paciente);
        return turno;
    }
}
