package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.OperadorResumenDTO;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;

class OperadorResumenServiceTest {

    private final TurnoRepository turnos = mock(TurnoRepository.class);
    private final ListaEsperaRepository listaEspera = mock(ListaEsperaRepository.class);
    private final CentroAccessGuard centroAccess = mock(CentroAccessGuard.class);
    private OperadorResumenService service;

    private final Set<Integer> centro = Set.of(1);
    private final LocalDate hoy = LocalDate.of(2026, 11, 18);
    private final LocalDateTime ahora = LocalDateTime.of(hoy, LocalTime.of(15, 0));

    @BeforeEach
    void setUp() {
        service = new OperadorResumenService(turnos, listaEspera, centroAccess);
    }

    @Test
    void cuentaSoloElCentroDelOperadorYDejaAfueraLoQueYaPasoOSeCancelo() {
        when(centroAccess.centrosPermitidos()).thenReturn(centro);
        Turno pasado = turno(10, LocalTime.of(9, 0), LocalTime.of(9, 30), EstadoTurno.PROGRAMADO, "Ana", "Pérez");
        Turno proximo = turno(11, LocalTime.of(16, 0), LocalTime.of(16, 30), EstadoTurno.CONFIRMADO, "Luis", "Gómez");
        Turno cancelado = turno(12, LocalTime.of(18, 0), LocalTime.of(18, 30), EstadoTurno.CANCELADO, "Eva", "Sosa");
        when(turnos.findByCentroAtencion_IdInAndFecha(centro, hoy)).thenReturn(List.of(pasado, proximo, cancelado));
        when(turnos.countByCentroAtencion_IdInAndEstado(centro, EstadoTurno.PROGRAMADO)).thenReturn(4L);
        when(turnos.countByCentroAtencion_IdInAndEstadoAndFechaBetween(
                centro, EstadoTurno.CANCELADO, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)))
                .thenReturn(2L);
        when(listaEspera.countByEstadoAndCentroAtencion_IdIn("PENDIENTE", centro)).thenReturn(3L);

        OperadorResumenDTO resumen = service.resumen(ahora);

        assertEquals(2, resumen.getTurnosHoy());
        assertEquals(4, resumen.getPendientesConfirmar());
        assertEquals(2, resumen.getCancelacionesSemana());
        assertEquals(3, resumen.getListaEspera());
        assertEquals(1, resumen.getProximosHoy().size());
        assertEquals(11, resumen.getProximosHoy().get(0).getId());
        assertEquals("16:00", resumen.getProximosHoy().get(0).getHora());
        assertEquals("Luis Gómez", resumen.getProximosHoy().get(0).getPaciente());
        assertEquals("Marta Ruiz", resumen.getProximosHoy().get(0).getMedico());
        assertEquals("Clínica médica", resumen.getProximosHoy().get(0).getEspecialidad());
        assertEquals("CONFIRMADO", resumen.getProximosHoy().get(0).getEstado());
        verify(turnos).countByCentroAtencion_IdInAndEstado(centro, EstadoTurno.PROGRAMADO);
        verify(listaEspera).countByEstadoAndCentroAtencion_IdIn("PENDIENTE", centro);
    }

    @Test
    void sinCentroNoConsultaTurnosNiListaDeEspera() {
        when(centroAccess.centrosPermitidos()).thenReturn(Set.of());

        OperadorResumenDTO resumen = service.resumen(ahora);

        assertEquals(0, resumen.getTurnosHoy());
        assertEquals(0, resumen.getPendientesConfirmar());
        assertEquals(0, resumen.getListaEspera());
        assertTrue(resumen.getProximosHoy().isEmpty());
        verifyNoInteractions(turnos, listaEspera);
    }

    @Test
    void accesoGlobalNoRecorreTodosLosCentros() {
        when(centroAccess.centrosPermitidos()).thenReturn(null);

        OperadorResumenDTO resumen = service.resumen(ahora);

        assertEquals(0, resumen.getTurnosHoy());
        verifyNoInteractions(turnos, listaEspera);
    }

    private static Turno turno(int id, LocalTime inicio, LocalTime fin, EstadoTurno estado,
            String nombre, String apellido) {
        Paciente paciente = new Paciente();
        paciente.setNombre(nombre);
        paciente.setApellido(apellido);
        Medico medico = new Medico();
        medico.setNombre("Marta");
        medico.setApellido("Ruiz");
        StaffMedico staff = new StaffMedico();
        staff.setMedico(medico);
        Especialidad especialidad = new Especialidad();
        especialidad.setNombre("Clínica médica");
        Turno turno = new Turno();
        turno.setId(id);
        turno.setHoraInicio(inicio);
        turno.setHoraFin(fin);
        turno.setEstado(estado);
        turno.setPaciente(paciente);
        turno.setStaffMedico(staff);
        turno.setEspecialidad(especialidad);
        return turno;
    }
}
