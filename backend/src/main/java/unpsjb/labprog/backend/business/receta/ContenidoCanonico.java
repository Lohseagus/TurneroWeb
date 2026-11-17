package unpsjb.labprog.backend.business.receta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import unpsjb.labprog.backend.model.hce.DocumentoAsistencial;
import unpsjb.labprog.backend.model.hce.OrdenEstudio;
import unpsjb.labprog.backend.model.hce.OrdenEstudioItem;
import unpsjb.labprog.backend.model.hce.Receta;
import unpsjb.labprog.backend.model.hce.RecetaItem;

/**
 * Texto canónico versionado. El estado, el motivo de anulación y el CUIR
 * quedan afuera: la huella prueba el contenido emitido, no el ciclo de vida.
 */
public final class ContenidoCanonico {

    private ContenidoCanonico() {
    }

    public static String receta(Receta receta) {
        StringBuilder out = new StringBuilder();
        encabezado(out, "RECETA", receta);
        out.append("tipoReceta=").append(receta.getTipo() == null ? "" : receta.getTipo().name()).append('\n');
        List<RecetaItem> items = new ArrayList<>(receta.getItems() == null ? List.of() : receta.getItems());
        items.sort(Comparator.comparingInt(RecetaItem::getOrden));
        for (RecetaItem item : items) {
            out.append("item=")
                    .append(item.getOrden()).append('|')
                    .append(campo(item.getNombreGenerico())).append('|')
                    .append(campo(item.getMarcaComercial())).append('|')
                    .append(campo(item.getConcentracion())).append('|')
                    .append(campo(item.getFormaFarmaceutica())).append('|')
                    .append(campo(item.getPresentacion())).append('|')
                    .append(item.getCantidad() == null ? "" : item.getCantidad()).append('|')
                    .append(campo(item.getCantidadEnLetras())).append('|')
                    .append(campo(item.getDosis())).append('|')
                    .append(campo(item.getFrecuencia())).append('|')
                    .append(campo(item.getDuracion())).append('|')
                    .append(campo(item.getVia())).append('|')
                    .append(campo(item.getIndicaciones())).append('|')
                    .append(item.isNoSustituir()).append('|')
                    .append(campo(item.getJustificacionNoSustituir())).append('\n');
        }
        return out.toString();
    }

    public static String orden(OrdenEstudio orden) {
        StringBuilder out = new StringBuilder();
        encabezado(out, "ORDEN", orden);
        out.append("urgencia=").append(orden.getUrgencia() == null ? "" : orden.getUrgencia().name()).append('\n');
        out.append("indicacion=").append(campo(orden.getIndicacionClinica())).append('\n');
        List<OrdenEstudioItem> items = new ArrayList<>(orden.getItems() == null ? List.of() : orden.getItems());
        items.sort(Comparator.comparingInt(OrdenEstudioItem::getOrdenNumero));
        for (OrdenEstudioItem item : items) {
            out.append("item=")
                    .append(item.getOrdenNumero()).append('|')
                    .append(campo(item.getNombrePractica())).append('|')
                    .append(campo(item.getSystemUri())).append('|')
                    .append(campo(item.getCode())).append('\n');
        }
        return out.toString();
    }

    private static void encabezado(StringBuilder out, String tipo, DocumentoAsistencial doc) {
        out.append("v1\n");
        out.append("tipoDocumento=").append(tipo).append('\n');
        out.append("identificador=").append(campo(doc.getIdentificador())).append('\n');
        out.append("consultaId=").append(doc.getConsulta() == null ? "" : doc.getConsulta().getId()).append('\n');
        out.append("paciente=")
                .append(campo(doc.getPacienteApellido())).append('|')
                .append(campo(doc.getPacienteNombre())).append('|')
                .append(doc.getPacienteDni() == null ? "" : doc.getPacienteDni()).append('|')
                .append(campo(doc.getPacienteSexo())).append('|')
                .append(fecha(doc.getPacienteNacimiento())).append('|')
                .append(campo(doc.getObraSocialNombre())).append('|')
                .append(campo(doc.getNumeroAfiliado())).append('\n');
        out.append("prescriptor=")
                .append(campo(doc.getPrescriptorApellido())).append('|')
                .append(campo(doc.getPrescriptorNombre())).append('|')
                .append(campo(doc.getMatricula())).append('|')
                .append(campo(doc.getMatriculaNacional())).append('|')
                .append(campo(doc.getMatriculaProvincial())).append('|')
                .append(campo(doc.getRefepsId())).append('|')
                .append(campo(doc.getEspecialidadNombre())).append('\n');
        out.append("centro=")
                .append(doc.getCentroAtencion() == null ? "" : doc.getCentroAtencion().getId()).append('|')
                .append(campo(doc.getCentroNombre())).append('\n');
        out.append("emision=").append(fecha(doc.getFechaEmision())).append('\n');
        out.append("vencimiento=").append(fecha(doc.getFechaVencimiento())).append('\n');
        out.append("diagnostico=")
                .append(campo(doc.getDiagnosticoSystem())).append('|')
                .append(campo(doc.getDiagnosticoCode())).append('|')
                .append(campo(doc.getDiagnosticoDisplay())).append('\n');
    }

    private static String campo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("|", "/").replace("\n", " ").trim();
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.toString();
    }
}
