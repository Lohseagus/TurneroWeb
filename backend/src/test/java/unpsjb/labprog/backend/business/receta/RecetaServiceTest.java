package unpsjb.labprog.backend.business.receta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import unpsjb.labprog.backend.business.hce.HceAccessDeniedException;
import unpsjb.labprog.backend.business.hce.HceAccessGuard;
import unpsjb.labprog.backend.business.hce.HceAuditoriaService;
import unpsjb.labprog.backend.business.hce.HceImmutableException;
import unpsjb.labprog.backend.business.repository.CatalogoMedicamentoRepository;
import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.DiagnosticoConsultaRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.RecetaRepository;
import unpsjb.labprog.backend.dto.receta.RecetaDTO;
import unpsjb.labprog.backend.dto.receta.RecetaItemWriteDTO;
import unpsjb.labprog.backend.dto.receta.RecetaWriteDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.CodigoCie10;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.DiagnosticoConsulta;
import unpsjb.labprog.backend.model.hce.EstadoPrescripcion;
import unpsjb.labprog.backend.model.hce.Receta;
import unpsjb.labprog.backend.model.hce.RecetaItem;

@ExtendWith(MockitoExtension.class)
class RecetaServiceTest {

    @Mock private RecetaRepository recetaRepository;
    @Mock private ConsultaRepository consultaRepository;
    @Mock private CatalogoMedicamentoRepository catalogoRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private DiagnosticoConsultaRepository diagnosticoRepository;
    @Mock private HceAccessGuard guard;
    @Mock private HceAuditoriaService auditoria;
    @Mock private PlataformaRecetaElectronica plataforma;

    private RecetaProperties properties;
    private RecetaService service;
    private Medico medico;
    private User usuarioMedico;
    private Consulta consulta;

    @BeforeEach
    void setUp() {
        properties = new RecetaProperties();
        properties.setMaxItems(3);
        properties.setVigenciaDias(30);
        properties.setPlataforma("local");
        properties.setPublicBaseUrl("http://localhost:4200");
        properties.setVerificacionPorMinuto(30);
        DatosDocumento datos = new DatosDocumento(guard, pacienteRepository, diagnosticoRepository, properties);
        service = new RecetaService(recetaRepository, consultaRepository, catalogoRepository, guard, auditoria, datos,
                properties, plataforma, new LimiteVerificacion());
        medico = medico();
        usuarioMedico = new User("Juan", "Pérez", 33333333L, "medico@turnero.com", "x", "+5492804000001", Role.MEDICO);
        consulta = consulta();
    }

    @Test
    void laHuellaCambiaSiSeAlteraElContenido() {
        Receta receta = recetaEmitible();
        receta.setIdentificador("CTR20261117ABCDEFGHJKMNPQRSTV");
        receta.setFechaEmision(LocalDate.of(2026, 11, 17));
        receta.setFechaVencimiento(LocalDate.of(2026, 12, 17));
        String hash = CodigosDocumento.sha256(ContenidoCanonico.receta(receta));
        assertEquals(hash, CodigosDocumento.sha256(ContenidoCanonico.receta(receta)));
        receta.getItems().get(0).setNombreGenerico("paracetamol");
        assertNotEquals(hash, CodigosDocumento.sha256(ContenidoCanonico.receta(receta)));
    }

    @Test
    void emitidaNoSeEdita() {
        Receta receta = recetaEmitible();
        receta.setEstado(EstadoPrescripcion.EMITIDA);
        when(recetaRepository.findById(1L)).thenReturn(Optional.of(receta));
        when(guard.usuarioActual()).thenReturn(usuarioMedico);
        when(guard.exigirMedico(usuarioMedico)).thenReturn(medico);

        assertThrows(HceImmutableException.class, () -> service.actualizar(1L, borradorComun()));
        verify(recetaRepository, never()).save(any());
    }

    @Test
    void anularSinMotivoNoPersiste() {
        assertThrows(IllegalArgumentException.class, () -> service.anular(1L, "  "));
        verify(recetaRepository, never()).save(any());
    }

    @Test
    void anularGuardaMotivoAutorYFecha() {
        Receta receta = recetaEmitible();
        receta.setEstado(EstadoPrescripcion.EMITIDA);
        receta.setIdentificador("CTR20261117ABCDEFGHJKMNPQRSTV");
        when(recetaRepository.findById(1L)).thenReturn(Optional.of(receta));
        when(recetaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(guard.usuarioActual()).thenReturn(usuarioMedico);
        when(guard.exigirMedico(usuarioMedico)).thenReturn(medico);

        RecetaDTO dto = service.anular(1L, "Error de dosis");

        assertEquals("ANULADA", dto.getEstado());
        assertEquals("Error de dosis", dto.getMotivoAnulacion());
        assertEquals("medico@turnero.com", dto.getAnuladoPor());
        assertNotNull(dto.getAnuladoEn());
        verify(plataforma).anular("CTR20261117ABCDEFGHJKMNPQRSTV", "Error de dosis");
    }

    @Test
    void emitirCalculaLaHuellaDelContenido() {
        Receta receta = recetaEmitible();
        prepararEmision(receta);
        when(plataforma.emitir(any())).thenReturn(new PlataformaRecetaElectronica.ResultadoEmision(
                receta.getIdentificador(), null, "local"));

        RecetaDTO dto = service.emitir(1L, null);

        assertEquals("EMITIDA", dto.getEstado());
        assertTrue(dto.isIntegridad());
        assertNotNull(dto.getIdentificador());
        assertTrue(dto.getIdentificador().startsWith("CTR"));
        assertEquals(64, dto.getCodigoVerificacion().length());
        assertNullCuir(dto.getCuir());
        assertEquals(CodigosDocumento.sha256(ContenidoCanonico.receta(receta)), dto.getHashContenido());
        receta.getItems().get(0).setDosis("2 comprimidos");
        assertFalse(service.integridad(receta));
    }

    @Test
    void psicotropicoNoSeEmite() {
        Receta receta = recetaEmitible();
        receta.getItems().get(0).setNombreGenerico("clonazepam");
        when(recetaRepository.findById(1L)).thenReturn(Optional.of(receta));
        when(guard.usuarioActual()).thenReturn(usuarioMedico);
        when(guard.exigirMedico(usuarioMedico)).thenReturn(medico);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.emitir(1L, null));
        assertTrue(error.getMessage().contains("receta oficial archivada"));
        verify(plataforma, never()).emitir(any());
        verify(recetaRepository, never()).save(any());
    }

    @Test
    void operadorAdminYSuperadminNoCrean() {
        for (Role rol : List.of(Role.OPERADOR, Role.ADMINISTRADOR, Role.SUPERADMIN)) {
            when(guard.usuarioActual()).thenReturn(usuario(rol));
            assertThrows(HceAccessDeniedException.class, () -> service.crear(borradorComun()));
        }
        verify(recetaRepository, never()).save(any());
    }

    @Test
    void medicoDeOtroCentroNoCrea() {
        when(guard.usuarioActual()).thenReturn(usuarioMedico);
        when(consultaRepository.findById(8L)).thenReturn(Optional.of(consulta));
        when(guard.assertPuedeCrear(eq(consulta.getPaciente()), eq(1), isNull()))
                .thenThrow(new HceAccessDeniedException("Solo puede registrar la consulta de un paciente que atiende en su centro"));

        assertThrows(HceAccessDeniedException.class, () -> service.crear(borradorComun()));
        verify(recetaRepository, never()).save(any());
    }

    @Test
    void pacienteAjenoNoLeeLaReceta() {
        Receta receta = recetaEmitible();
        when(recetaRepository.findById(1L)).thenReturn(Optional.of(receta));
        org.mockito.Mockito.doThrow(new HceAccessDeniedException("Sin acceso a esta historia clínica"))
                .when(guard).assertLeeConsulta(consulta);

        assertThrows(HceAccessDeniedException.class, () -> service.obtener(1L));
    }

    @Test
    void laCantidadSeEscribeEnLetras() {
        assertEquals("tres", CantidadEnLetras.de(3));
        assertEquals("veintiuno", CantidadEnLetras.de(21));
        assertEquals("ciento dos", CantidadEnLetras.de(102));
    }

    private void prepararEmision(Receta receta) {
        when(recetaRepository.findById(1L)).thenReturn(Optional.of(receta));
        when(recetaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(guard.usuarioActual()).thenReturn(usuarioMedico);
        when(guard.exigirMedico(usuarioMedico)).thenReturn(medico);
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DiagnosticoConsulta diagnostico = new DiagnosticoConsulta();
        diagnostico.setCode("R51");
        diagnostico.setDisplay("Cefalea");
        diagnostico.setSystemUri(CodigoCie10.SISTEMA_CIE10);
        when(diagnosticoRepository.findByConsulta_IdOrderByIdAsc(8L)).thenReturn(List.of(diagnostico));
    }

    private void assertNullCuir(String cuir) {
        assertTrue(cuir == null || cuir.isBlank());
    }

    private RecetaWriteDTO borradorComun() {
        RecetaWriteDTO dto = new RecetaWriteDTO();
        dto.setConsultaId(8L);
        dto.setTipo("COMUN");
        dto.setDiagnosticoCode("R51");
        dto.setSexo("F");
        dto.setFechaNacimiento(LocalDate.of(1990, 5, 12));
        RecetaItemWriteDTO item = new RecetaItemWriteDTO();
        item.setNombreGenerico("ibuprofeno");
        item.setConcentracion("400 mg");
        item.setFormaFarmaceutica("comprimidos");
        item.setCantidad(2);
        item.setDosis("1 comprimido");
        item.setFrecuencia("cada 8 horas");
        item.setVia("oral");
        dto.setItems(new ArrayList<>(List.of(item)));
        return dto;
    }

    private Receta recetaEmitible() {
        Receta receta = new Receta();
        receta.setId(1L);
        receta.setConsulta(consulta);
        receta.setPaciente(consulta.getPaciente());
        receta.setMedico(medico);
        receta.setCentroAtencion(consulta.getCentroAtencion());
        receta.setEstado(EstadoPrescripcion.BORRADOR);
        receta.setTipo(unpsjb.labprog.backend.model.hce.TipoReceta.COMUN);
        receta.setCreadaEn(LocalDateTime.now());
        receta.setDiagnosticoCode("R51");
        receta.setDiagnosticoDisplay("Cefalea");
        receta.setDiagnosticoSystem(CodigoCie10.SISTEMA_CIE10);
        receta.setPacienteNombre("María");
        receta.setPacienteApellido("González");
        receta.setPacienteDni(30111222L);
        receta.setPacienteSexo("F");
        receta.setPacienteNacimiento(LocalDate.of(1990, 5, 12));
        receta.setPrescriptorNombre("Juan");
        receta.setPrescriptorApellido("Pérez");
        receta.setMatricula("MP-12345");
        receta.setCentroNombre("Clínica Santa María");
        RecetaItem item = new RecetaItem();
        item.setReceta(receta);
        item.setOrden(1);
        item.setNombreGenerico("ibuprofeno");
        item.setConcentracion("400 mg");
        item.setFormaFarmaceutica("comprimidos");
        item.setCantidad(2);
        item.setCantidadEnLetras("dos");
        item.setDosis("1 comprimido");
        item.setFrecuencia("cada 8 horas");
        item.setVia("oral");
        item.setLista(unpsjb.labprog.backend.model.hce.ListaControlada.NINGUNA);
        receta.setItems(new ArrayList<>(List.of(item)));
        return receta;
    }

    private Consulta consulta() {
        Paciente paciente = new Paciente();
        paciente.setId(10);
        paciente.setNombre("María");
        paciente.setApellido("González");
        paciente.setDni(30111222L);
        paciente.setSexo("F");
        paciente.setFechaNacimiento(java.sql.Date.valueOf(LocalDate.of(1990, 5, 12)));
        paciente.setEmail("paciente1.santamaria@turnero.com");
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setNombre("Clínica Santa María");
        Especialidad especialidad = new Especialidad();
        especialidad.setId(1);
        especialidad.setNombre("Clínica médica");
        Consulta creada = new Consulta();
        creada.setId(8L);
        creada.setPaciente(paciente);
        creada.setMedico(medico);
        creada.setCentroAtencion(centro);
        creada.setEspecialidad(especialidad);
        return creada;
    }

    private Medico medico() {
        Medico profesional = new Medico();
        profesional.setId(1);
        profesional.setNombre("Juan");
        profesional.setApellido("Pérez");
        profesional.setMatricula("MP-12345");
        profesional.setMatriculaProvincial("MP-12345");
        profesional.setEmail("medico@turnero.com");
        return profesional;
    }

    private User usuario(Role rol) {
        return new User("Admin", "Centro", 20000000L, rol.name().toLowerCase() + "@turnero.com", "x", "+5492804000099",
                rol);
    }
}
