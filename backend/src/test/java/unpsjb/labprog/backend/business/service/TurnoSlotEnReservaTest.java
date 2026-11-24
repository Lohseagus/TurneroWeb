package unpsjb.labprog.backend.business.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.TurnoDTO;
import unpsjb.labprog.backend.exception.TurnoTomadoException;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Turno;

@ExtendWith(MockitoExtension.class)
class TurnoSlotEnReservaTest {

    @Mock
    private TurnoRepository repository;
    @Mock
    private CentroAccessGuard centroAccess;
    @Mock
    private TurnoSlotGuard slotGuard;
    @InjectMocks
    private TurnoService service;

    @Test
    void reagendarPasaPorElMismoCandado() {
        when(repository.findById(1)).thenReturn(Optional.of(turno()));
        when(centroAccess.puedeOperar(nullable(Integer.class))).thenReturn(true);
        when(slotGuard.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TurnoDTO nuevos = new TurnoDTO();
        nuevos.setFecha(LocalDate.of(2026, 12, 20));
        nuevos.setHoraInicio(LocalTime.of(9, 0));
        nuevos.setHoraFin(LocalTime.of(9, 30));

        service.reagendarTurno(1, nuevos, "cambio de horario", "operador@test.com");

        verify(slotGuard).guardar(any());
        verify(repository, never()).save(any());
    }

    @Test
    void siElCandadoRechazaElReagendadoNoSeGuardaPorOtroLado() {
        when(repository.findById(1)).thenReturn(Optional.of(turno()));
        when(centroAccess.puedeOperar(nullable(Integer.class))).thenReturn(true);
        when(slotGuard.guardar(any())).thenThrow(new TurnoTomadoException());
        TurnoDTO nuevos = new TurnoDTO();
        nuevos.setFecha(LocalDate.of(2026, 12, 20));
        nuevos.setHoraInicio(LocalTime.of(9, 0));
        nuevos.setHoraFin(LocalTime.of(9, 30));

        org.junit.jupiter.api.Assertions.assertThrows(TurnoTomadoException.class,
                () -> service.reagendarTurno(1, nuevos, "cambio de horario", "operador@test.com"));
        verify(repository, never()).save(any());
    }

    private static Turno turno() {
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
