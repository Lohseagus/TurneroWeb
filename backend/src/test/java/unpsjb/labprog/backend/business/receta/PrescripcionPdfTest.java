package unpsjb.labprog.backend.business.receta;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.EstadoPrescripcion;
import unpsjb.labprog.backend.model.hce.ListaControlada;
import unpsjb.labprog.backend.model.hce.Receta;
import unpsjb.labprog.backend.model.hce.RecetaItem;
import unpsjb.labprog.backend.model.hce.TipoReceta;

class PrescripcionPdfTest {

    @Test
    void elPdfDeUnaRecetaEmitidaEmpiezaComoPdf() throws Exception {
        byte[] pdf = PrescripcionPdf.receta(ejemplo(), "http://localhost:4200/verificar-receta/demo");
        assertTrue(pdf.length > 4);
        assertTrue(new String(pdf, 0, 4).startsWith("%PDF"));
        Path salida = Path.of("target", "receta-ejemplo.pdf");
        Files.createDirectories(salida.getParent());
        Files.write(salida, pdf);
    }

    private Receta ejemplo() {
        Paciente paciente = new Paciente();
        paciente.setId(10);
        paciente.setNombre("María");
        paciente.setApellido("González");
        paciente.setDni(30111222L);
        Medico medico = new Medico();
        medico.setId(1);
        medico.setNombre("Juan");
        medico.setApellido("Pérez");
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setNombre("Clínica Santa María");
        Consulta consulta = new Consulta();
        consulta.setId(8L);
        consulta.setPaciente(paciente);
        consulta.setMedico(medico);
        consulta.setCentroAtencion(centro);
        Receta receta = new Receta();
        receta.setId(1L);
        receta.setConsulta(consulta);
        receta.setPaciente(paciente);
        receta.setMedico(medico);
        receta.setCentroAtencion(centro);
        receta.setEstado(EstadoPrescripcion.EMITIDA);
        receta.setTipo(TipoReceta.COMUN);
        receta.setCreadaEn(LocalDateTime.of(2026, 11, 17, 10, 0));
        receta.setFechaEmision(LocalDate.of(2026, 11, 17));
        receta.setFechaVencimiento(LocalDate.of(2026, 12, 17));
        receta.setIdentificador("CTR20261117ABCDEFGHJKMNPQRSTVWX");
        receta.setTipoFirma("ELECTRONICA_LOCAL");
        receta.setDiagnosticoCode("R51");
        receta.setDiagnosticoDisplay("Cefalea");
        receta.setPacienteNombre("María");
        receta.setPacienteApellido("González");
        receta.setPacienteDni(30111222L);
        receta.setPacienteSexo("F");
        receta.setPacienteNacimiento(LocalDate.of(1990, 5, 12));
        receta.setObraSocialNombre("OSDE");
        receta.setNumeroAfiliado("12345678");
        receta.setPrescriptorNombre("Juan");
        receta.setPrescriptorApellido("Pérez");
        receta.setMatricula("MP-12345");
        receta.setMatriculaProvincial("MP-12345");
        receta.setEspecialidadNombre("Clínica médica");
        receta.setCentroNombre("Clínica Santa María");
        RecetaItem item = new RecetaItem();
        item.setOrden(1);
        item.setNombreGenerico("ibuprofeno");
        item.setConcentracion("400 mg");
        item.setFormaFarmaceutica("comprimidos");
        item.setCantidad(2);
        item.setCantidadEnLetras("dos");
        item.setDosis("1 comprimido");
        item.setFrecuencia("cada 8 horas");
        item.setDuracion("3 días");
        item.setVia("oral");
        item.setNoSustituir(false);
        item.setLista(ListaControlada.NINGUNA);
        receta.setItems(List.of(item));
        receta.setHashContenido(CodigosDocumento.sha256(ContenidoCanonico.receta(receta)));
        return receta;
    }
}
