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
        ConfiguracionExcepcional global = new ConfiguracionExcepcional();
        global.setTipo(ConfiguracionExcepcional.TipoExcepcion.FERIADO);
        global.setActivo(true);
        when(repository.findByTipoAndFechaAndActivoTrue(ConfiguracionExcepcional.TipoExcepcion.FERIADO, lunes))
                .thenReturn(List.of(global));
        assertEquals("No se pueden reservar turnos en un feriado",
                configuracionExcepcionalService.motivoBloqueoReserva(lunes, LocalTime.of(10, 0),
                        LocalTime.of(10, 30), 4, 8));

        when(repository.findByTipoAndFechaAndActivoTrue(any(), any())).thenReturn(List.of());
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
        when(repository.findByTipoAndFechaAndActivoTrue(any(), any())).thenReturn(List.of());
        when(repository.findByFechaAndConsultorio_IdAndActivoTrue(any(), anyInt())).thenReturn(List.of());
        when(repository.findAusenciasVigentes(eq(8), eq(lunes), any()))
                .thenReturn(List.of(ventana(LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(repository.findAusenciasVigentes(eq(9), eq(lunes), any())).thenReturn(List.of());

        assertNull(configuracionExcepcionalService.motivoBloqueoReserva(
                lunes, LocalTime.of(14, 0), LocalTime.of(14, 30), 4, 8));
        assertNull(configuracionExcepcionalService.motivoBloqueoReserva(
                lunes, LocalTime.of(10, 0), LocalTime.of(10, 30), 4, 9));
    }

    @Test
    void unFeriadoDeUnCentroNoBloqueaOtroCentro() {
        unpsjb.labprog.backend.model.CentroAtencion centro = new unpsjb.labprog.backend.model.CentroAtencion();
        centro.setId(1);
        ConfiguracionExcepcional delCentro = new ConfiguracionExcepcional();
        delCentro.setTipo(ConfiguracionExcepcional.TipoExcepcion.FERIADO);
        delCentro.setActivo(true);
        delCentro.setCentroAtencion(centro);
        when(repository.findByTipoAndFechaAndActivoTrue(ConfiguracionExcepcional.TipoExcepcion.FERIADO, lunes))
                .thenReturn(List.of(delCentro));
        when(repository.findByFechaAndConsultorio_IdAndActivoTrue(any(), anyInt())).thenReturn(List.of());
        when(repository.findAusenciasVigentes(any(), any(), any())).thenReturn(List.of());

        unpsjb.labprog.backend.model.Consultorio consultorioAjeno = new unpsjb.labprog.backend.model.Consultorio();
        unpsjb.labprog.backend.model.CentroAtencion otro = new unpsjb.labprog.backend.model.CentroAtencion();
        otro.setId(2);
        consultorioAjeno.setCentroAtencion(otro);
        when(consultorioRepository.findById(9)).thenReturn(java.util.Optional.of(consultorioAjeno));

        unpsjb.labprog.backend.model.Consultorio consultorioPropio = new unpsjb.labprog.backend.model.Consultorio();
        consultorioPropio.setCentroAtencion(centro);
        when(consultorioRepository.findById(4)).thenReturn(java.util.Optional.of(consultorioPropio));

        assertNull(configuracionExcepcionalService.motivoBloqueoReserva(
                lunes, LocalTime.of(10, 0), LocalTime.of(10, 30), 9, 8));
        assertEquals("No se pueden reservar turnos en un feriado",
                configuracionExcepcionalService.motivoBloqueoReserva(lunes, LocalTime.of(10, 0),
                        LocalTime.of(10, 30), 4, 8));
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
