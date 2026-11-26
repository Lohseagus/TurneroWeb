package unpsjb.labprog.backend.presenter;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import unpsjb.labprog.backend.Response;
import unpsjb.labprog.backend.business.service.CalendarioService;

/**
 * Grilla mensual: turnos del rango visible y los feriados que lo pisan.
 * El alcance lo resuelve {@link CalendarioService}.
 */
@RestController
@RequestMapping("/api/turno")
public class CalendarioPresenter {

    private final CalendarioService calendarioService;

    public CalendarioPresenter(CalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping("/calendario")
    public ResponseEntity<Object> calendario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer centroId,
            @RequestParam(required = false) Integer medicoId,
            @RequestParam(required = false) Integer especialidadId,
            @RequestParam(required = false) Integer consultorioId) {
        try {
            return Response.ok(
                    calendarioService.consultar(desde, hasta, centroId, medicoId, especialidadId, consultorioId),
                    "Calendario del mes");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Response.error(null, e.getMessage());
        }
    }
}
