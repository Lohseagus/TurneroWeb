package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.TurnoDTO;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Turno;

@ExtendWith(MockitoExtension.class)
class ReservaTurnoWiringTest {

    @Mock private TurnoRepository repository;
    @Mock private ReglasReservaTurno reglas;
    @Mock private CentroAccessGuard centroAccess;
    @InjectMocks private TurnoService service;

    @Test
    void asignarAplicaLaIdentidadAntesDeGuardar() {
        doThrow(new AccessDeniedException("Tu cuenta no tiene una ficha de paciente vinculada"))
                .when(reglas).aplicarIdentidadDeReserva(any());
        assertThrows(AccessDeniedException.class, () -> service.save(new TurnoDTO(), "ana@test.com"));
        verify(repository, never()).save(any());
    }

    @Test
    void reagendarNoGuardaSiElHorarioNoPasaLasReglas() {
        when(repository.findById(1)).thenReturn(Optional.of(turnoProgramado()));
        when(centroAccess.puedeOperar(nullable(Integer.class))).thenReturn(true);
        doThrow(new IllegalArgumentException("El horario no coincide con la agenda del médico."))
                .when(reglas).validarReserva(any(), anyBoolean(), any());
        TurnoDTO nuevos = new TurnoDTO();
        nuevos.setFecha(LocalDate.of(2026, 12, 14));
        nuevos.setHoraInicio(LocalTime.of(9, 15));
        nuevos.setHoraFin(LocalTime.of(9, 45));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.reagendarTurno(1, nuevos, "cambio de horario", "ana@test.com"));
        assertEquals("El horario no coincide con la agenda del médico.", error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void laAusenciaNoSeCargaPorElCambioDeEstado() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.changeEstado(1, EstadoTurno.AUSENTE, "no vino", "operador@test.com"));
        assertTrueMensaje(error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void marcarInasistenciaPasaAAusente() {
        Turno turno = turnoProgramado();
        when(repository.findById(1)).thenReturn(Optional.of(turno));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TurnoDTO resultado = service.marcarAsistencia(1, false, "operador@test.com");
        assertEquals("AUSENTE", resultado.getEstado());
        assertEquals(false, resultado.getAsistio());
    }

    private static void assertTrueMensaje(String mensaje) {
        if (mensaje == null || !mensaje.contains("marcando la asistencia")) {
            throw new AssertionError(mensaje);
        }
    }

    private static Turno turnoProgramado() {
        Paciente paciente = new Paciente();
        paciente.setId(3);
        paciente.setNombre("Ana");
        paciente.setApellido("López");
        Turno turno = new Turno();
        turno.setId(1);
        turno.setFecha(LocalDate.of(2026, 12, 14));
        turno.setHoraInicio(LocalTime.of(10, 0));
        turno.setHoraFin(LocalTime.of(10, 30));
        turno.setEstado(EstadoTurno.PROGRAMADO);
        turno.setPaciente(paciente);
        return turno;
    }
}
