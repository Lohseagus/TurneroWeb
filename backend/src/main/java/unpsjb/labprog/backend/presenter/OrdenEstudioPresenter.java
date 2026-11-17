package unpsjb.labprog.backend.presenter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import unpsjb.labprog.backend.business.receta.OrdenEstudioService;
import unpsjb.labprog.backend.business.receta.PrescripcionPdfService;
import unpsjb.labprog.backend.dto.receta.AnulacionDTO;
import unpsjb.labprog.backend.dto.receta.EmitirDTO;
import unpsjb.labprog.backend.dto.receta.OrdenEstudioWriteDTO;

@RestController
@RequestMapping("/api/ordenes-estudio")
public class OrdenEstudioPresenter {

    private final OrdenEstudioService ordenes;
    private final PrescripcionPdfService pdf;

    public OrdenEstudioPresenter(OrdenEstudioService ordenes, PrescripcionPdfService pdf) {
        this.ordenes = ordenes;
        this.pdf = pdf;
    }

    @GetMapping("/verificar/{codigo}")
    public ResponseEntity<Object> verificar(@PathVariable String codigo, HttpServletRequest request) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(
                ordenes.verificarPublico(codigo, ip(request)), "Verificación de orden"));
    }

    @GetMapping("/mias")
    public ResponseEntity<Object> mias() {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(ordenes.mias(), "Mis órdenes"));
    }

    @GetMapping
    public ResponseEntity<Object> porConsulta(@RequestParam Long consultaId) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(ordenes.porConsulta(consultaId), "Órdenes"));
    }

    @PostMapping
    public ResponseEntity<Object> crear(@RequestBody OrdenEstudioWriteDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.creado(ordenes.crear(dto), "Orden en borrador"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> obtener(@PathVariable Long id) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(ordenes.obtener(id), "Orden de estudio"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> actualizar(@PathVariable Long id, @RequestBody OrdenEstudioWriteDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(ordenes.actualizar(id, dto), "Borrador actualizado"));
    }

    @PostMapping("/{id}/emitir")
    public ResponseEntity<Object> emitir(@PathVariable Long id, @RequestBody(required = false) EmitirDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(
                ordenes.emitir(id, dto == null ? null : dto.getFechaVencimiento()), "Orden emitida"));
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<Object> anular(@PathVariable Long id, @RequestBody(required = false) AnulacionDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(
                ordenes.anular(id, dto == null ? null : dto.getMotivo()), "Orden anulada"));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Object> pdf(@PathVariable Long id) {
        return RespuestaClinica.pdf(() -> pdf.orden(id), "orden-" + id + ".pdf");
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
