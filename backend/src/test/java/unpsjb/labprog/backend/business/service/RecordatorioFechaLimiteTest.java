package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Turno;

class RecordatorioFechaLimiteTest {

    @Test
    void elPlazoSaleDeLaFechaDelTurnoYNoDeHoy() {
        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.getDiasMinConfirmacion(1)).thenReturn(2);
        RecordatorioService service = new RecordatorioService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "configuracionService", configuracion);

        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        Turno turno = new Turno();
        turno.setId(15);
        turno.setFecha(LocalDate.of(2026, 12, 10));
        turno.setHoraInicio(LocalTime.of(10, 0));
        turno.setCentroAtencion(centro);

        assertEquals(LocalDateTime.of(2026, 12, 8, 10, 0), service.limiteParaConfirmar(turno));
        String texto = service.construirDetallesRecordatorio(turno);
        assertTrue(texto.contains("08/12/2026 a las 10:00"));
        assertFalse(texto.contains(LocalDate.now().plusDays(2).toString()));
    }
}
