package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.repository.AuditLogRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.model.AuditLog;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Turno;

@ExtendWith(MockitoExtension.class)
class CancelacionAutomaticaTest {

    @Mock private TurnoRepository turnoRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private EmailService emailService;
    @Mock private TurnoService turnoService;
    @Mock private ReglasReservaTurno reglasReserva;
    @Mock private ListaEsperaService listaEsperaService;
    @InjectMocks private TurnoAutomationService automation;

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditLogService auditoria;

    @BeforeEach
    void habilitar() {
        ReflectionTestUtils.setField(automation, "autoCancelEnabled", true);
        ReflectionTestUtils.setField(automation, "horasAnticipacion", 48);
        ReflectionTestUtils.setField(automation, "appUrl", "http://localhost:4200");
    }

    @Test
    void elJobHorarioCancelaLoQueLaReglaCompartidaMarcaYAuditaComoSistema() {
        Turno programado = turno(4, EstadoTurno.PROGRAMADO);
        when(turnoService.candidatosCancelacionAutomatica(any())).thenReturn(List.of(programado));

        automation.cancelarTurnosNoConfirmados();

        assertEquals(EstadoTurno.CANCELADO, programado.getEstado());
        verify(auditLogService).logTurnoCancelledAutomatically(eq(4L), eq(3L), any(), eq("PROGRAMADO"));
        verify(turnoRepository).save(programado);
        verify(listaEsperaService).ofrecerHuecoLiberado(programado);
    }

    @Test
    void laAuditoriaDeLaCancelacionAutomaticaQuedaComoAccionDelSistema() {
        Turno referencia = new Turno();
        referencia.setId(9);
        when(turnoRepository.getReferenceById(9)).thenReturn(referencia);
        when(auditLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        auditoria.logTurnoCancelledAutomatically(9L, 3L, "sin confirmar", "REAGENDADO");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("SISTEMA", log.getPerformedBy());
        assertEquals(9, log.getTurno().getId());
        assertEquals("REAGENDADO", log.getEstadoAnterior());
        assertEquals("CANCELADO", log.getEstadoNuevo());
        assertTrue(log.getNewValues().contains("actor=SISTEMA"));
    }

    private static Turno turno(int id, EstadoTurno estado) {
        Paciente paciente = new Paciente();
        paciente.setId(3);
        paciente.setNombre("Ana");
        paciente.setApellido("López");
        paciente.setEmail("ana@test.com");
        Turno turno = new Turno();
        turno.setId(id);
        turno.setEstado(estado);
        turno.setPaciente(paciente);
        turno.setFecha(java.time.LocalDate.of(2026, 12, 8));
        turno.setHoraInicio(java.time.LocalTime.of(10, 0));
        return turno;
    }
}
