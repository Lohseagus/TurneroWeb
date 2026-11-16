package unpsjb.labprog.backend.presenter;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import unpsjb.labprog.backend.business.hce.HceAccessDeniedException;
import unpsjb.labprog.backend.business.hce.HceAdjuntoService;
import unpsjb.labprog.backend.business.hce.HceFhirService;
import unpsjb.labprog.backend.business.hce.HceImmutableException;
import unpsjb.labprog.backend.business.hce.HceNotFoundException;
import unpsjb.labprog.backend.business.hce.HcePdfService;
import unpsjb.labprog.backend.business.hce.HistoriaClinicaService;
import unpsjb.labprog.backend.dto.hce.ConsentimientoDTO;
import unpsjb.labprog.backend.dto.hce.ConsultaWriteDTO;
import unpsjb.labprog.backend.dto.hce.EnmiendaWriteDTO;
import unpsjb.labprog.backend.dto.hce.PerfilClinicoDTO;
import unpsjb.labprog.backend.model.Paciente;

@RestController
@RequestMapping("/api/hce")
public class HistoriaClinicaPresenter {

    private final HistoriaClinicaService historia;
    private final HceAdjuntoService adjuntos;
    private final HcePdfService pdf;
    private final HceFhirService fhir;

    public HistoriaClinicaPresenter(HistoriaClinicaService historia, HceAdjuntoService adjuntos, HcePdfService pdf,
            HceFhirService fhir) {
        this.historia = historia;
        this.adjuntos = adjuntos;
        this.pdf = pdf;
        this.fhir = fhir;
    }

    @GetMapping("/cie10")
    public ResponseEntity<Object> cie10(@RequestParam(name = "q", required = false) String q) {
        return ejecutar(() -> ok(historia.buscarCie10(q), "Catálogo CIE-10"));
    }

    @GetMapping("/mis-pacientes")
    public ResponseEntity<Object> misPacientes() {
        return ejecutar(() -> ok(historia.misPacientes(), "Pacientes atendidos"));
    }

    @GetMapping("/turnos/{turnoId}")
    public ResponseEntity<Object> contextoTurno(@PathVariable Integer turnoId) {
        return ejecutar(() -> ok(historia.contextoTurno(turnoId), "Contexto del turno"));
    }

    @GetMapping("/mi")
    public ResponseEntity<Object> miHistoria() {
        return ejecutar(() -> {
            Paciente paciente = historia.pacienteActual();
            Map<String, Object> data = new HashMap<>();
            data.put("perfil", historia.obtenerPerfil(paciente.getId()));
            data.put("timeline", historia.timeline(paciente.getId(), null, null, null, null));
            data.put("adjuntos", adjuntos.listar(paciente.getId()));
            return ok(data, "Historia clínica");
        });
    }

    @PutMapping("/mi/consentimiento")
    public ResponseEntity<Object> consentimiento(@RequestBody ConsentimientoDTO dto) {
        return ejecutar(() -> ok(historia.actualizarConsentimiento(dto != null && dto.isComparteEntreCentros()),
                "Consentimiento actualizado"));
    }

    @GetMapping("/mi/pdf")
    public ResponseEntity<Object> miPdf() {
        return ejecutarPdf(() -> {
            Paciente paciente = historia.pacienteActual();
            return pdf.pdfResumen(paciente.getId());
        }, "historia-clinica.pdf");
    }

    @GetMapping("/pacientes/{pacienteId}/perfil")
    public ResponseEntity<Object> perfil(@PathVariable Integer pacienteId) {
        return ejecutar(() -> ok(historia.obtenerPerfil(pacienteId), "Perfil clínico"));
    }

    @PutMapping("/pacientes/{pacienteId}/perfil")
    public ResponseEntity<Object> actualizarPerfil(@PathVariable Integer pacienteId, @RequestBody PerfilClinicoDTO dto) {
        return ejecutar(() -> ok(historia.actualizarPerfil(pacienteId, dto), "Perfil clínico actualizado"));
    }

    @GetMapping("/pacientes/{pacienteId}/timeline")
    public ResponseEntity<Object> timeline(@PathVariable Integer pacienteId,
            @RequestParam(required = false) Integer especialidadId,
            @RequestParam(required = false) Integer centroId,
            @RequestParam(required = false) Integer medicoId,
            @RequestParam(required = false) String tipo) {
        return ejecutar(() -> ok(historia.timeline(pacienteId, especialidadId, centroId, medicoId, tipo),
                "Línea de tiempo"));
    }

    @GetMapping("/pacientes/{pacienteId}/metadata")
    public ResponseEntity<Object> metadata(@PathVariable Integer pacienteId) {
        return ejecutar(() -> ok(historia.metadata(pacienteId), "Metadatos de la historia clínica"));
    }

    @GetMapping("/pacientes/{pacienteId}/auditoria")
    public ResponseEntity<Object> auditoria(@PathVariable Integer pacienteId) {
        return ejecutar(() -> ok(historia.auditoria(pacienteId), "Registro de accesos"));
    }

    @GetMapping("/pacientes/{pacienteId}/pdf")
    public ResponseEntity<Object> pdfPaciente(@PathVariable Integer pacienteId) {
        return ejecutarPdf(() -> pdf.pdfResumen(pacienteId), "historia-clinica-" + pacienteId + ".pdf");
    }

    @GetMapping("/pacientes/{pacienteId}/fhir")
    public ResponseEntity<Object> fhirPaciente(@PathVariable Integer pacienteId) {
        return ejecutar(() -> ok(fhir.bundle(pacienteId), "Exportación FHIR de lectura"));
    }

    @GetMapping("/pacientes/{pacienteId}/adjuntos")
    public ResponseEntity<Object> adjuntos(@PathVariable Integer pacienteId) {
        return ejecutar(() -> ok(adjuntos.listar(pacienteId), "Adjuntos"));
    }

    @PostMapping("/pacientes/{pacienteId}/adjuntos")
    public ResponseEntity<Object> subirAdjuntoPaciente(@PathVariable Integer pacienteId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) String tipo) {
        return ejecutar(() -> creado(adjuntos.subirEnPaciente(pacienteId, archivo, tipo), "Archivo guardado"));
    }

    @PostMapping("/consultas")
    public ResponseEntity<Object> crear(@RequestBody ConsultaWriteDTO dto) {
        return ejecutar(() -> creado(historia.crear(dto), "Consulta registrada como borrador"));
    }

    @GetMapping("/consultas/{id}")
    public ResponseEntity<Object> obtener(@PathVariable Long id) {
        return ejecutar(() -> ok(historia.obtener(id), "Consulta"));
    }

    @PutMapping("/consultas/{id}")
    public ResponseEntity<Object> actualizar(@PathVariable Long id, @RequestBody ConsultaWriteDTO dto) {
        return ejecutar(() -> ok(historia.actualizar(id, dto), "Borrador actualizado"));
    }

    @PostMapping("/consultas/{id}/cerrar")
    public ResponseEntity<Object> cerrar(@PathVariable Long id) {
        return ejecutar(() -> ok(historia.cerrar(id), "Consulta cerrada"));
    }

    @PostMapping("/consultas/{id}/enmiendas")
    public ResponseEntity<Object> enmendar(@PathVariable Long id, @RequestBody EnmiendaWriteDTO dto) {
        return ejecutar(() -> creado(historia.enmendar(id, dto), "Enmienda registrada"));
    }

    @GetMapping("/consultas/{id}/pdf")
    public ResponseEntity<Object> pdfConsulta(@PathVariable Long id) {
        return ejecutarPdf(() -> pdf.pdfConsulta(id), "consulta-" + id + ".pdf");
    }

    @PostMapping("/consultas/{id}/adjuntos")
    public ResponseEntity<Object> subirAdjunto(@PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) String tipo) {
        return ejecutar(() -> creado(adjuntos.subirEnConsulta(id, archivo, tipo), "Archivo guardado"));
    }

    @GetMapping("/adjuntos/{id}")
    public ResponseEntity<Object> descargar(@PathVariable Long id) {
        try {
            HceAdjuntoService.ArchivoDescarga archivo = adjuntos.descargar(id);
            if (!Files.exists(archivo.path())) {
                return json(HttpStatus.NOT_FOUND, "El archivo no está en el almacenamiento", null);
            }
            MediaType media = MediaType.APPLICATION_OCTET_STREAM;
            try {
                media = MediaType.parseMediaType(archivo.contentType());
            } catch (Exception ignored) {
                media = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(media)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombre() + "\"")
                    .body(new FileSystemResource(archivo.path()));
        } catch (HceAccessDeniedException e) {
            return json(HttpStatus.valueOf(e.getStatus()), e.getMessage(), null);
        } catch (HceNotFoundException e) {
            return json(HttpStatus.NOT_FOUND, e.getMessage(), null);
        } catch (RuntimeException e) {
            return json(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        }
    }

    private ResponseEntity<Object> ejecutar(Proveedor proveedor) {
        try {
            return proveedor.obtener();
        } catch (HceAccessDeniedException e) {
            return json(HttpStatus.valueOf(e.getStatus()), e.getMessage(), null);
        } catch (HceImmutableException e) {
            return json(HttpStatus.CONFLICT, e.getMessage(), null);
        } catch (HceNotFoundException e) {
            return json(HttpStatus.NOT_FOUND, e.getMessage(), null);
        } catch (IllegalStateException e) {
            return json(HttpStatus.CONFLICT, e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            return json(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        } catch (RuntimeException e) {
            return json(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación", null);
        }
    }

    private ResponseEntity<Object> ejecutarPdf(PdfProveedor proveedor, String filename) {
        try {
            byte[] bytes = proveedor.obtener();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(new ByteArrayResource(bytes));
        } catch (HceAccessDeniedException e) {
            return json(HttpStatus.valueOf(e.getStatus()), e.getMessage(), null);
        } catch (HceNotFoundException e) {
            return json(HttpStatus.NOT_FOUND, e.getMessage(), null);
        } catch (RuntimeException e) {
            return json(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        }
    }

    private ResponseEntity<Object> ok(Object data, String mensaje) {
        return json(HttpStatus.OK, mensaje, data);
    }

    private ResponseEntity<Object> creado(Object data, String mensaje) {
        return json(HttpStatus.CREATED, mensaje, data);
    }

    private ResponseEntity<Object> json(HttpStatus status, String mensaje, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("status_code", status.value());
        body.put("status_text", mensaje);
        body.put("data", data);
        return ResponseEntity.status(status).body(body);
    }

    @FunctionalInterface
    private interface Proveedor {
        ResponseEntity<Object> obtener();
    }

    @FunctionalInterface
    private interface PdfProveedor {
        byte[] obtener();
    }
}
