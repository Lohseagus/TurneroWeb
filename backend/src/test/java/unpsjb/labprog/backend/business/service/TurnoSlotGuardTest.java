package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.exception.TurnoTomadoException;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;

class TurnoSlotGuardTest {

    @Test
    void cuatroReservasSimultaneasDejanUnaSola() throws Exception {
        TurnoRepository repository = mock(TurnoRepository.class);
        ConcurrentHashMap<String, Turno> ocupados = new ConcurrentHashMap<>();
        AtomicInteger ids = new AtomicInteger(1);
        when(repository.bloquearSlotActivo(any(), any(), any())).thenAnswer(invocation -> {
            String clave = invocation.getArgument(0) + "|" + invocation.getArgument(1) + "|" + invocation.getArgument(2);
            Turno ya = ocupados.get(clave);
            return ya == null ? List.of() : List.of(ya);
        });
        when(repository.save(any(Turno.class))).thenAnswer(invocation -> {
            Turno turno = invocation.getArgument(0);
            String clave = turno.getStaffMedico().getId() + "|" + turno.getFecha() + "|" + turno.getHoraInicio();
            if (!Boolean.TRUE.equals(turno.getSobreturno()) && ocupados.putIfAbsent(clave, turno) != null) {
                throw new DataIntegrityViolationException("ux_turno_slot_activo");
            }
            turno.setId(ids.getAndIncrement());
            return turno;
        });
        TurnoSlotGuard guard = new TurnoSlotGuard(repository);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger tomados = new AtomicInteger();
        AtomicInteger guardados = new AtomicInteger();
        try {
            List<Future<?>> tareas = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tareas.add(pool.submit(() -> {
                    largada.await();
                    try {
                        guard.guardar(turnoNormal());
                        guardados.incrementAndGet();
                    } catch (TurnoTomadoException ex) {
                        tomados.incrementAndGet();
                        assertEquals(TurnoTomadoException.MENSAJE, ex.getMessage());
                    }
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, guardados.get());
        assertEquals(3, tomados.get());
        assertEquals(TurnoTomadoException.MENSAJE, "Ese turno ya fue tomado");
    }

    @Test
    void elSobreturnoPedidoAPropositoNoPisaElCandado() {
        TurnoRepository repository = mock(TurnoRepository.class);
        when(repository.save(any(Turno.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TurnoSlotGuard guard = new TurnoSlotGuard(repository);
        Turno primero = turnoNormal();
        primero.setSobreturno(true);
        Turno segundo = turnoNormal();
        segundo.setSobreturno(true);

        guard.guardar(primero);
        guard.guardar(segundo);

        assertTrue(Boolean.TRUE.equals(primero.getSobreturno()));
    }

    @Test
    void laBaseRechazaElDuplicadoConElMensajeDeTurnoTomado() {
        TurnoRepository repository = mock(TurnoRepository.class);
        when(repository.bloquearSlotActivo(any(), any(), any())).thenReturn(List.of());
        when(repository.save(any(Turno.class))).thenThrow(new DataIntegrityViolationException("ux_turno_slot_activo"));
        TurnoSlotGuard guard = new TurnoSlotGuard(repository);

        TurnoTomadoException error = assertThrows(TurnoTomadoException.class, () -> guard.guardar(turnoNormal()));
        assertEquals("Ese turno ya fue tomado", error.getMessage());
    }

    private static Turno turnoNormal() {
        StaffMedico staff = new StaffMedico();
        staff.setId(8);
        Turno turno = new Turno();
        turno.setFecha(LocalDate.of(2026, 12, 7));
        turno.setHoraInicio(LocalTime.of(10, 0));
        turno.setHoraFin(LocalTime.of(10, 30));
        turno.setEstado(EstadoTurno.PROGRAMADO);
        turno.setStaffMedico(staff);
        turno.setSobreturno(false);
        return turno;
    }
}
