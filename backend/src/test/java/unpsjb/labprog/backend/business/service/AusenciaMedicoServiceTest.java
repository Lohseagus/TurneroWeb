package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import unpsjb.labprog.backend.model.Turno;
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
    }

    @Test
    void cancelaLosTurnosFuturosYNoOfreceElHuecoALaListaDeEspera() {
        when(staffMedicoRepository.findById(8)).thenReturn(Optional.of(staff));
        when(repository.findAusenciasVigentes(any(), any(), any())).thenReturn(List.of());
        Turno futuro = turno(20, hoy.plusDays(5), EstadoTurno.CONFIRMADO);
        Turno hoyMismo = turno(21, hoy, EstadoTurno.PROGRAMADO);
        Turno pasado = turno(22, hoy.minusDays(2), EstadoTurno.PROGRAMADO);
        when(turnoRepository.findAfectablesPorAusencia(eq(8), eq(hoy.plusDays(1)), eq(hoy.plusDays(6)), any()))
                .thenReturn(List.of(futuro, hoyMismo, pasado));
        when(repository.save(any())).thenAnswer(invocation -> {
            ConfiguracionExcepcional guardada = invocation.getArgument(0);
            guardada.setId(70);
            return guardada;
        });

        AusenciaResultadoDTO resultado = service.confirmar(solicitud(hoy.plusDays(1), hoy.plusDays(6)), "medico@turnero.com");

        verify(turnoService).cancelarTurno(
                eq(20), eq("Ausencia del médico: vacaciones"), eq("medico@turnero.com"), eq(false),
                eq(AusenciaMedicoService.TIPO_DEEP_LINK));
        verify(turnoService, never()).cancelarTurno(eq(21), any(), any(), anyBoolean(), any());
        verify(turnoService, never()).cancelarTurno(eq(22), any(), any(), anyBoolean(), any());
        assertEquals(1, resultado.getCancelados().size());
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
