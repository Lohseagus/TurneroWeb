package unpsjb.labprog.backend.business.receta;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;

import unpsjb.labprog.backend.model.hce.DocumentoAsistencial;
import unpsjb.labprog.backend.model.hce.EstadoPrescripcion;
import unpsjb.labprog.backend.model.hce.OrdenEstudio;
import unpsjb.labprog.backend.model.hce.OrdenEstudioItem;
import unpsjb.labprog.backend.model.hce.Receta;
import unpsjb.labprog.backend.model.hce.RecetaItem;

public final class PrescripcionPdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PrescripcionPdf() {
    }

    public static byte[] receta(Receta receta, String url) {
        return documento(doc -> {
            titulo(doc, "Receta electrónica", receta.getEstado());
            encabezado(doc, receta);
            doc.add(new Paragraph("Diagnóstico: " + diagnostico(receta)).setMarginTop(8));
            doc.add(new Paragraph("Rp/").simulateBold().setMarginTop(10));
            for (RecetaItem item : receta.getItems()) {
                doc.add(new Paragraph(item.getOrden() + ". " + item.getNombreGenerico()
                        + (item.getMarcaComercial() == null ? "" : " (" + item.getMarcaComercial() + ")")
                        + " — " + valor(item.getConcentracion()) + " — " + valor(item.getFormaFarmaceutica())));
                doc.add(new Paragraph("Cantidad: " + valor(item.getCantidad()) + " (" + valor(item.getCantidadEnLetras())
                        + ")" + (item.getPresentacion() == null ? "" : " — " + item.getPresentacion())));
                doc.add(new Paragraph("Dosis: " + valor(item.getDosis())
                        + " · " + valor(item.getFrecuencia())
                        + (item.getDuracion() == null ? "" : " · " + item.getDuracion())
                        + " · vía " + valor(item.getVia())));
                if (item.getIndicaciones() != null) {
                    doc.add(new Paragraph("Indicaciones: " + item.getIndicaciones()));
                }
                if (item.isNoSustituir()) {
                    doc.add(new Paragraph("No sustituir. " + valor(item.getJustificacionNoSustituir())).simulateBold());
                }
            }
            firma(doc, receta, url);
        });
    }

    public static byte[] orden(OrdenEstudio orden, String url) {
        return documento(doc -> {
            titulo(doc, "Orden de estudio", orden.getEstado());
            encabezado(doc, orden);
            doc.add(new Paragraph("Diagnóstico: " + diagnostico(orden)).setMarginTop(8));
            doc.add(new Paragraph("Urgencia: " + orden.getUrgencia().name()));
            doc.add(new Paragraph("Indicación clínica: " + valor(orden.getIndicacionClinica())));
            doc.add(new Paragraph("Prácticas").simulateBold().setMarginTop(10));
            for (OrdenEstudioItem item : orden.getItems()) {
                doc.add(new Paragraph(item.getOrdenNumero() + ". " + item.getNombrePractica()
                        + (item.getCode() == null ? "" : " — " + valor(item.getSystemUri()) + " " + item.getCode())));
            }
            firma(doc, orden, url);
        });
    }

    private static void titulo(Document doc, String titulo, EstadoPrescripcion estado) {
        doc.add(new Paragraph("CheTurno").simulateBold().setFontSize(11));
        doc.add(new Paragraph(titulo).simulateBold().setFontSize(16));
        doc.add(new Paragraph("Estado: " + (estado == null ? "" : estado.name())));
        if (estado == EstadoPrescripcion.BORRADOR) {
            doc.add(new Paragraph("BORRADOR — sin validez para la farmacia ni el laboratorio.").simulateBold());
        }
    }

    private static void encabezado(Document doc, DocumentoAsistencial documento) {
        doc.add(new Paragraph("Centro: " + valor(documento.getCentroNombre())).setMarginTop(8));
        doc.add(new Paragraph("Paciente: " + valor(documento.getPacienteApellido()) + ", "
                + valor(documento.getPacienteNombre())));
        doc.add(new Paragraph("DNI: " + valor(documento.getPacienteDni())
                + " · Sexo: " + valor(documento.getPacienteSexo())
                + " · Nacimiento: " + fecha(documento.getPacienteNacimiento())));
        doc.add(new Paragraph("Cobertura: " + valor(documento.getObraSocialNombre())
                + (documento.getNumeroAfiliado() == null ? "" : " · Afiliado " + documento.getNumeroAfiliado())));
        doc.add(new Paragraph("Profesional: " + valor(documento.getPrescriptorApellido()) + ", "
                + valor(documento.getPrescriptorNombre())
                + (documento.getEspecialidadNombre() == null ? "" : " · " + documento.getEspecialidadNombre()))
                .setMarginTop(6));
        doc.add(new Paragraph("Matrícula: " + valor(documento.getMatricula())
                + " · Nacional: " + valor(documento.getMatriculaNacional())
                + " · Provincial: " + valor(documento.getMatriculaProvincial())));
        doc.add(new Paragraph("REFEPS: " + valor(documento.getRefepsId())));
        doc.add(new Paragraph("Emisión: " + fecha(documento.getFechaEmision())
                + " · Vencimiento: " + fecha(documento.getFechaVencimiento())));
    }

    private static void firma(Document doc, DocumentoAsistencial documento, String url) {
        doc.add(new Paragraph("________________________________").setMarginTop(8));
        doc.add(new Paragraph(valor(documento.getPrescriptorApellido()) + ", " + valor(documento.getPrescriptorNombre())));
        doc.add(new Paragraph("Firma electrónica").simulateBold().setMarginTop(8));
        doc.add(new Paragraph("Tipo: " + valor(documento.getTipoFirma())
                + ". No es una firma digital de la Ley 25.506."));
        doc.add(new Paragraph("Identificador: " + valor(documento.getIdentificador())));
        doc.add(new Paragraph("CUIR: " + (documento.getCuir() == null || documento.getCuir().isBlank()
                ? "pendiente de repositorio nacional"
                : documento.getCuir())));
        doc.add(new Paragraph("Huella SHA-256: " + valor(documento.getHashContenido())));
        if (url != null && documento.getEstado() != EstadoPrescripcion.BORRADOR) {
            doc.add(new Paragraph("Verificación: " + url));
            Image qr = new Image(ImageDataFactory.create(QrPng.de(url, 180)));
            qr.setWidth(90);
            doc.add(qr);
        }
        doc.add(new Paragraph("Documento de CheTurno. No proviene de una plataforma inscripta en ReNaPDiS "
                + "hasta que el repositorio nacional asigne el CUIR.").setFontSize(8).setMarginTop(8));
    }

    private static String diagnostico(DocumentoAsistencial documento) {
        if (documento.getDiagnosticoCode() == null) {
            return "—";
        }
        return documento.getDiagnosticoCode() + " " + valor(documento.getDiagnosticoDisplay());
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "—" : fecha.format(FECHA);
    }

    private static String valor(Object valor) {
        return valor == null || String.valueOf(valor).isBlank() ? "—" : String.valueOf(valor);
    }

    private static byte[] documento(Escritor escritor) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            doc.setMargins(28, 32, 28, 32);
            escritor.escribir(doc);
            doc.close();
            return out.toByteArray();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    @FunctionalInterface
    private interface Escritor {
        void escribir(Document doc) throws Exception;
    }
}
