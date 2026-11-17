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
import unpsjb.labprog.backend.business.receta.PrescripcionPdfService;
import unpsjb.labprog.backend.business.receta.RecetaService;
import unpsjb.labprog.backend.dto.receta.AnulacionDTO;
import unpsjb.labprog.backend.dto.receta.EmitirDTO;
import unpsjb.labprog.backend.dto.receta.RecetaWriteDTO;

@RestController
@RequestMapping("/api/recetas")
public class RecetaPresenter {

    private final RecetaService recetas;
    private final PrescripcionPdfService pdf;

    public RecetaPresenter(RecetaService recetas, PrescripcionPdfService pdf) {
        this.recetas = recetas;
        this.pdf = pdf;
    }

    @GetMapping("/verificar/{codigo}")
    public ResponseEntity<Object> verificar(@PathVariable String codigo, HttpServletRequest request) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(
                recetas.verificarPublico(codigo, ip(request)), "Verificación de receta"));
    }

    @GetMapping("/catalogo")
    public ResponseEntity<Object> catalogo(@RequestParam(name = "q", required = false) String q) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(recetas.catalogo(q), "Vademécum"));
    }

    @GetMapping("/mias")
    public ResponseEntity<Object> mias() {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(recetas.mias(), "Mis recetas"));
    }

    @GetMapping
    public ResponseEntity<Object> porConsulta(@RequestParam Long consultaId) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(recetas.porConsulta(consultaId), "Recetas"));
    }

    @PostMapping
    public ResponseEntity<Object> crear(@RequestBody RecetaWriteDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.creado(recetas.crear(dto), "Receta en borrador"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> obtener(@PathVariable Long id) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(recetas.obtener(id), "Receta"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> actualizar(@PathVariable Long id, @RequestBody RecetaWriteDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(recetas.actualizar(id, dto), "Borrador actualizado"));
    }

    @PostMapping("/{id}/emitir")
    public ResponseEntity<Object> emitir(@PathVariable Long id, @RequestBody(required = false) EmitirDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(
                recetas.emitir(id, dto == null ? null : dto.getFechaVencimiento()), "Receta emitida"));
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<Object> anular(@PathVariable Long id, @RequestBody(required = false) AnulacionDTO dto) {
        return RespuestaClinica.ejecutar(() -> RespuestaClinica.ok(
                recetas.anular(id, dto == null ? null : dto.getMotivo()), "Receta anulada"));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Object> pdf(@PathVariable Long id) {
        return RespuestaClinica.pdf(() -> pdf.receta(id), "receta-" + id + ".pdf");
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
