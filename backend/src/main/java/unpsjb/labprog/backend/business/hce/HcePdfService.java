package unpsjb.labprog.backend.business.hce;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import unpsjb.labprog.backend.dto.hce.AlergiaDTO;
import unpsjb.labprog.backend.dto.hce.ConsultaDTO;
import unpsjb.labprog.backend.dto.hce.DiagnosticoDTO;
import unpsjb.labprog.backend.dto.hce.EnmiendaDTO;
import unpsjb.labprog.backend.dto.hce.MedicacionDTO;
import unpsjb.labprog.backend.dto.hce.PerfilClinicoDTO;
import unpsjb.labprog.backend.dto.hce.VacunaDTO;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;
import unpsjb.labprog.backend.model.hce.Consulta;

@Service
public class HcePdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final HistoriaClinicaService historia;
    private final HceAccessGuard guard;

    public HcePdfService(HistoriaClinicaService historia, HceAccessGuard guard) {
        this.historia = historia;
        this.guard = guard;
    }

    @Transactional
    public byte[] pdfConsulta(Long id) {
        Consulta consulta = historia.cargar(id);
        guard.assertLeeConsulta(consulta);
        ConsultaDTO dto = historia.aConsulta(consulta);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.EXPORTAR_PDF, "CONSULTA", String.valueOf(id),
                consulta.getCentroAtencion().getId(), "PDF de consulta");
        return documento("Consulta " + id, doc -> escribirConsulta(doc, dto));
    }

    @Transactional
    public byte[] pdfResumen(Integer pacienteId) {
        Paciente paciente = historia.cargarPaciente(pacienteId);
        guard.assertLeePerfil(paciente);
        PerfilClinicoDTO perfil = historia.obtenerPerfil(pacienteId);
        guard.auditar(paciente, AccionAuditoriaHce.EXPORTAR_PDF, "RESUMEN", String.valueOf(pacienteId), null,
                "PDF de resumen de historia clínica");
        return documento("Historia clínica", doc -> {
            doc.add(new Paragraph("Historia clínica electrónica — CheTurno").simulateBold().setFontSize(16));
            doc.add(new Paragraph(perfil.getApellido() + ", " + perfil.getNombre()));
            doc.add(new Paragraph("DNI: " + valor(perfil.getDni())));
            if (perfil.getIdentificadorFederado() != null) {
                doc.add(new Paragraph("Identificador federado: " + perfil.getIdentificadorFederado()));
            }
            doc.add(new Paragraph("Grupo sanguíneo: " + valor(perfil.getGrupoSanguineo())));
            doc.add(new Paragraph("Antecedentes personales: " + valor(perfil.getAntecedentesPersonales())));
            doc.add(new Paragraph("Antecedentes familiares: " + valor(perfil.getAntecedentesFamiliares())));
            doc.add(new Paragraph("Antecedentes quirúrgicos: " + valor(perfil.getAntecedentesQuirurgicos())));
            doc.add(new Paragraph("Hábitos: " + valor(perfil.getHabitos())));
            doc.add(new Paragraph("Comparte historia entre centros: " + (perfil.isComparteEntreCentros() ? "sí" : "no")));
            doc.add(new Paragraph("Alergias").simulateBold());
            if (perfil.getAlergias().isEmpty()) {
                doc.add(new Paragraph("Sin alergias registradas"));
            }
            for (AlergiaDTO alergia : perfil.getAlergias()) {
                doc.add(new Paragraph("- " + alergia.getSustancia()
                        + (alergia.isActiva() ? "" : " (inactiva)")
                        + (alergia.getReaccion() != null ? " — " + alergia.getReaccion() : "")));
            }
            doc.add(new Paragraph("Medicación habitual").simulateBold());
            for (MedicacionDTO medicacion : perfil.getMedicaciones()) {
                if (!medicacion.isActiva()) {
                    continue;
                }
                doc.add(new Paragraph("- " + medicacion.getFarmaco()
                        + (medicacion.getDosis() != null ? " " + medicacion.getDosis() : "")
                        + (medicacion.getFrecuencia() != null ? " " + medicacion.getFrecuencia() : "")));
            }
            doc.add(new Paragraph("Vacunas").simulateBold());
            for (VacunaDTO vacuna : perfil.getVacunas()) {
                doc.add(new Paragraph("- " + vacuna.getNombre()
                        + (vacuna.getFechaAplicacion() != null ? " (" + vacuna.getFechaAplicacion() + ")" : "")));
            }
            doc.add(new Paragraph("Evoluciones").simulateBold());
            for (ConsultaDTO consulta : historia.consultasVisibles(pacienteId)) {
                escribirConsulta(doc, consulta);
            }
        });
    }

    private void escribirConsulta(Document doc, ConsultaDTO consulta) {
        doc.add(new Paragraph(" ").setFontSize(8));
        doc.add(new Paragraph("Consulta #" + consulta.getId() + " — " + consulta.getEstado()).simulateBold());
        doc.add(new Paragraph("Paciente: " + consulta.getPacienteApellido() + ", " + consulta.getPacienteNombre()
                + " — DNI " + valor(consulta.getDni())));
        doc.add(new Paragraph("Profesional: " + valor(consulta.getMedicoNombre())
                + " — Matrícula " + valor(consulta.getMatricula())
                + (consulta.getRefepsId() != null ? " — REFEPS " + consulta.getRefepsId() : "")));
        doc.add(new Paragraph("Centro: " + valor(consulta.getCentroNombre())
                + (consulta.getEspecialidadNombre() != null ? " — " + consulta.getEspecialidadNombre() : "")));
        if (consulta.getCreadaEn() != null) {
            doc.add(new Paragraph("Fecha: " + consulta.getCreadaEn().format(FECHA)
                    + (consulta.getCerradaEn() != null ? " — cierre " + consulta.getCerradaEn().format(FECHA) : "")));
        }
        doc.add(new Paragraph("Motivo: " + valor(consulta.getMotivoConsulta())));
        doc.add(new Paragraph("Anamnesis: " + valor(consulta.getAnamnesis())));
        doc.add(new Paragraph("Examen físico: " + valor(consulta.getExamenFisico())));
        doc.add(new Paragraph("Signos vitales: TA "
                + valor(consulta.getTensionSistolica()) + "/" + valor(consulta.getTensionDiastolica())
                + " mmHg, FC " + valor(consulta.getFrecuenciaCardiaca())
                + ", Temp " + valor(consulta.getTemperatura())
                + ", Peso " + valor(consulta.getPesoKg()) + " kg, Talla " + valor(consulta.getTallaCm())
                + " cm, IMC " + valor(consulta.getImc())));
        doc.add(new Paragraph("Diagnósticos").simulateBold());
        if (consulta.getDiagnosticos().isEmpty()) {
            doc.add(new Paragraph("Sin diagnósticos codificados"));
        }
        for (DiagnosticoDTO diagnostico : consulta.getDiagnosticos()) {
            doc.add(new Paragraph("- " + diagnostico.getCode() + " " + diagnostico.getDisplay()
                    + " (" + diagnostico.getTipo() + ")"));
        }
        doc.add(new Paragraph("Indicaciones: " + valor(consulta.getIndicaciones())));
        doc.add(new Paragraph("Plan: " + valor(consulta.getPlan())));
        doc.add(new Paragraph("Próximo control: " + valor(consulta.getProximoControl())));
        for (EnmiendaDTO enmienda : consulta.getEnmiendas()) {
            doc.add(new Paragraph("Enmienda " + enmienda.getCreadaEn().format(FECHA)
                    + " — " + enmienda.getMedicoNombre() + " — motivo: " + enmienda.getMotivo()).simulateItalic());
            doc.add(new Paragraph(enmienda.getTexto()));
        }
    }

    private byte[] documento(String titulo, PdfBody body) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.getDocumentInfo().setTitle(titulo);
            Document document = new Document(pdf);
            body.write(document);
            document.close();
            return out.toByteArray();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    private String valor(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    @FunctionalInterface
    private interface PdfBody {
        void write(Document document);
    }
}
