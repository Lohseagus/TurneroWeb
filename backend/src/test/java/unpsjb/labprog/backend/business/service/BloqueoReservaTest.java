package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;

@ExtendWith(MockitoExtension.class)
class BloqueoReservaTest {

    @Mock
    private ConfiguracionExcepcionalRepository repository;
    @Mock
    private ConsultorioRepository consultorioRepository;
    @Mock
    private EsquemaTurnoRepository esquemaTurnoRepository;

    @InjectMocks
    private ConfiguracionExcepcionalService configuracionExcepcionalService;

    private final LocalDate lunes = LocalDate.of(2026, 12, 7);

    @Test
    void unFeriadoUnMantenimientoOUnaAusenciaRechazanLaReservaNueva() {
        when(repository.existsByFechaAndTipoAndActivoTrue(lunes, ConfiguracionExcepcional.TipoExcepcion.FERIADO))
                .thenReturn(true);
        assertEquals("No se pueden reservar turnos en un feriado",
                configuracionExcepcionalService.motivoBloqueoReserva(lunes, LocalTime.of(10, 0),
                        LocalTime.of(10, 30), 4, 8));

        when(repository.existsByFechaAndTipoAndActivoTrue(any(), any())).thenReturn(false);
        ConfiguracionExcepcional mantenimiento = ventana(LocalTime.of(9, 0), LocalTime.of(12, 0));
        mantenimiento.setTipo(ConfiguracionExcepcional.TipoExcepcion.MANTENIMIENTO);
        when(repository.findByFechaAndConsultorio_IdAndActivoTrue(lunes, 4)).thenReturn(List.of(mantenimiento));
        assertEquals("No se pueden reservar turnos: el consultorio está en mantenimiento",
                configuracionExcepcionalService.motivoBloqueoReserva(lunes, LocalTime.of(10, 0),
                        LocalTime.of(10, 30), 4, 8));

        when(repository.findByFechaAndConsultorio_IdAndActivoTrue(any(), anyInt())).thenReturn(List.of());
        when(repository.findAusenciasVigentes(eq(8), eq(lunes), eq(ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO)))
                .thenReturn(List.of(new ConfiguracionExcepcional()));
        assertEquals("No se pueden reservar turnos: el médico está ausente en ese horario",
                configuracionExcepcionalService.motivoBloqueoReserva(lunes, LocalTime.of(10, 0),
                        LocalTime.of(10, 30), 4, 8));
    }

    @Test
    void laVentanaHorariaYOtroProfesionalNoBloqueanElResto() {
        when(repository.existsByFechaAndTipoAndActivoTrue(any(), any())).thenReturn(false);
        when(repository.findByFechaAndConsultorio_IdAndActivoTrue(any(), anyInt())).thenReturn(List.of());
        when(repository.findAusenciasVigentes(eq(8), eq(lunes), any()))
                .thenReturn(List.of(ventana(LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(repository.findAusenciasVigentes(eq(9), eq(lunes), any())).thenReturn(List.of());

        assertNull(configuracionExcepcionalService.motivoBloqueoReserva(
                lunes, LocalTime.of(14, 0), LocalTime.of(14, 30), 4, 8));
        assertNull(configuracionExcepcionalService.motivoBloqueoReserva(
                lunes, LocalTime.of(10, 0), LocalTime.of(10, 30), 4, 9));
    }

    private static ConfiguracionExcepcional ventana(LocalTime inicio, LocalTime fin) {
        ConfiguracionExcepcional config = new ConfiguracionExcepcional();
        config.setHoraInicio(inicio);
        config.setHoraFin(fin);
        config.setTipo(ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO);
        config.setActivo(true);
        return config;
    }
}
