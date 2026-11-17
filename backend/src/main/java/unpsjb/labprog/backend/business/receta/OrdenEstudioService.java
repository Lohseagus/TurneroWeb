package unpsjb.labprog.backend.business.receta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.hce.HceAccessGuard;
import unpsjb.labprog.backend.business.hce.HceAuditoriaService;
import unpsjb.labprog.backend.business.hce.HceImmutableException;
import unpsjb.labprog.backend.business.hce.HceNotFoundException;
import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.OrdenEstudioRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.dto.receta.ItemPublicoDTO;
import unpsjb.labprog.backend.dto.receta.OrdenEstudioDTO;
import unpsjb.labprog.backend.dto.receta.OrdenEstudioItemDTO;
import unpsjb.labprog.backend.dto.receta.OrdenEstudioItemWriteDTO;
import unpsjb.labprog.backend.dto.receta.OrdenEstudioWriteDTO;
import unpsjb.labprog.backend.dto.receta.VerificacionPublicaDTO;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.DocumentoAsistencial;
import unpsjb.labprog.backend.model.hce.EstadoPrescripcion;
import unpsjb.labprog.backend.model.hce.OrdenEstudio;
import unpsjb.labprog.backend.model.hce.OrdenEstudioItem;
import unpsjb.labprog.backend.model.hce.UrgenciaEstudio;

@Service
public class OrdenEstudioService {

    private final OrdenEstudioRepository ordenRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final HceAccessGuard guard;
    private final HceAuditoriaService auditoria;
    private final DatosDocumento datos;
    private final RecetaProperties properties;
    private final PlataformaRecetaElectronica plataforma;
    private final LimiteVerificacion limite;

    public OrdenEstudioService(OrdenEstudioRepository ordenRepository, ConsultaRepository consultaRepository,
            PacienteRepository pacienteRepository, HceAccessGuard guard, HceAuditoriaService auditoria,
            DatosDocumento datos, RecetaProperties properties, PlataformaRecetaElectronica plataforma,
            LimiteVerificacion limite) {
        this.ordenRepository = ordenRepository;
        this.consultaRepository = consultaRepository;
        this.pacienteRepository = pacienteRepository;
        this.guard = guard;
        this.auditoria = auditoria;
        this.datos = datos;
        this.properties = properties;
        this.plataforma = plataforma;
        this.limite = limite;
    }

    @Transactional
    public OrdenEstudioDTO crear(OrdenEstudioWriteDTO dto) {
        datos.exigirMedico("Solo un médico puede crear una orden de estudio");
        if (dto == null || dto.getConsultaId() == null) {
            throw new IllegalArgumentException("La consulta es obligatoria");
        }
        Consulta consulta = consulta(dto.getConsultaId());
        Medico medico = guard.assertPuedeCrear(consulta.getPaciente(), consulta.getCentroAtencion().getId(),
                consulta.getTurno());
        OrdenEstudio orden = new OrdenEstudio();
        orden.setConsulta(consulta);
        orden.setPaciente(consulta.getPaciente());
        orden.setMedico(medico);
        orden.setCentroAtencion(consulta.getCentroAtencion());
        orden.setEstado(EstadoPrescripcion.BORRADOR);
        orden.setCreadaEn(LocalDateTime.now());
        orden.setPlataforma(properties.getPlataforma());
        aplicar(orden, dto);
        orden = ordenRepository.save(orden);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.CREAR_ORDEN, "ORDEN", String.valueOf(orden.getId()),
                consulta.getCentroAtencion().getId(), "Alta de borrador de orden de estudio");
        return aDto(orden);
    }

    @Transactional
    public OrdenEstudioDTO actualizar(Long id, OrdenEstudioWriteDTO dto) {
        OrdenEstudio orden = cargar(id);
        datos.exigirAutor(orden);
        datos.assertBorrador(orden.getEstado());
        aplicar(orden, dto);
        orden.setActualizadaEn(LocalDateTime.now());
        ordenRepository.save(orden);
        guard.auditar(orden.getPaciente(), AccionAuditoriaHce.CREAR_ORDEN, "ORDEN", String.valueOf(id),
                orden.getCentroAtencion().getId(), "Actualización de borrador de orden");
        return aDto(orden);
    }

    @Transactional
    public OrdenEstudioDTO emitir(Long id, LocalDate vencimiento) {
        OrdenEstudio orden = cargar(id);
        datos.exigirAutor(orden);
        if (orden.getEstado() != EstadoPrescripcion.BORRADOR) {
            throw new HceImmutableException("La orden ya fue emitida");
        }
        if (orden.getItems().isEmpty()) {
            throw new IllegalArgumentException("La orden necesita al menos una práctica");
        }
        if (datos.limpiar(orden.getIndicacionClinica()) == null) {
            throw new IllegalArgumentException("La indicación clínica es obligatoria");
        }
        datos.diagnostico(orden, orden.getDiagnosticoCode(), true);
        datos.congelar(orden, null, null, null, null);
        datos.exigirDatosPaciente(orden);
        LocalDate emision = LocalDate.now();
        orden.setFechaEmision(emision);
        orden.setFechaVencimiento(datos.vencimiento(vencimiento, emision));
        orden.setIdentificador(CodigosDocumento.identificador("O", emision));
        orden.setCodigoVerificacion(CodigosDocumento.token());
        orden.setTipoFirma(DocumentoAsistencial.FIRMA_ELECTRONICA_LOCAL);
        orden.setFirmadoEn(LocalDateTime.now());
        orden.setPlataforma(properties.getPlataforma());
        String hash = CodigosDocumento.sha256(ContenidoCanonico.orden(orden));
        orden.setHashContenido(hash);
        PlataformaRecetaElectronica.ResultadoEmision resultado = plataforma.emitir(
                new PlataformaRecetaElectronica.SolicitudEmision("ORDEN", orden.getIdentificador(), hash));
        if (resultado.cuir() != null && !resultado.cuir().isBlank()) {
            orden.setCuir(resultado.cuir());
        }
        orden.setEstado(EstadoPrescripcion.EMITIDA);
        orden.setActualizadaEn(orden.getFirmadoEn());
        ordenRepository.save(orden);
        guard.auditar(orden.getPaciente(), AccionAuditoriaHce.EMITIR_ORDEN, "ORDEN", String.valueOf(id),
                orden.getCentroAtencion().getId(), "Emisión de orden de estudio");
        return aDto(orden);
    }

    @Transactional
    public OrdenEstudioDTO anular(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("La anulación necesita un motivo");
        }
        OrdenEstudio orden = cargar(id);
        datos.exigirAutor(orden);
        if (orden.getEstado() == EstadoPrescripcion.ANULADA) {
            throw new IllegalStateException("La orden ya está anulada");
        }
        if (orden.getIdentificador() != null) {
            plataforma.anular(orden.getIdentificador(), motivo.trim());
        }
        orden.setEstado(EstadoPrescripcion.ANULADA);
        orden.setMotivoAnulacion(motivo.trim());
        orden.setAnuladoPor(guard.usuarioActual().getEmail());
        orden.setAnuladoEn(LocalDateTime.now());
        orden.setActualizadaEn(orden.getAnuladoEn());
        ordenRepository.save(orden);
        guard.auditar(orden.getPaciente(), AccionAuditoriaHce.ANULAR_ORDEN, "ORDEN", String.valueOf(id),
                orden.getCentroAtencion().getId(), "Anulación de orden de estudio");
        return aDto(orden);
    }

    @Transactional
    public OrdenEstudioDTO obtener(Long id) {
        return aDto(lectura(id, AccionAuditoriaHce.VER_ORDEN, "Lectura de orden de estudio"));
    }

    @Transactional
    public List<OrdenEstudioDTO> porConsulta(Long consultaId) {
        Consulta consulta = consulta(consultaId);
        guard.assertLeeConsulta(consulta);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.VER_ORDEN, "ORDEN", "consulta-" + consultaId,
                consulta.getCentroAtencion().getId(), "Listado de órdenes de la consulta");
        return ordenRepository.findByConsulta_IdOrderByCreadaEnDesc(consultaId).stream().map(orden -> {
            if (datos.vencerSiCorresponde(orden)) {
                ordenRepository.save(orden);
            }
            return aDto(orden);
        }).toList();
    }

    @Transactional
    public List<OrdenEstudioDTO> mias() {
        Paciente paciente = pacienteActual();
        guard.auditar(paciente, AccionAuditoriaHce.VER_ORDEN, "ORDEN", "mias", null, "Listado de órdenes propias");
        return ordenRepository.findByPaciente_IdOrderByCreadaEnDesc(paciente.getId()).stream().map(orden -> {
            if (datos.vencerSiCorresponde(orden)) {
                ordenRepository.save(orden);
            }
            return aDto(orden);
        }).toList();
    }

    @Transactional
    public VerificacionPublicaDTO verificarPublico(String codigo, String ip) {
        limite.consumir(ip, properties.getVerificacionPorMinuto());
        if (codigo == null || codigo.isBlank()) {
            throw new HceNotFoundException("No hay una orden con ese código");
        }
        OrdenEstudio orden = ordenRepository.findByCodigoVerificacion(codigo.trim())
                .orElseThrow(() -> new HceNotFoundException("No hay una orden con ese código"));
        if (datos.vencerSiCorresponde(orden)) {
            ordenRepository.save(orden);
        }
        auditoria.registrarSistema(orden.getPaciente().getId(), AccionAuditoriaHce.VERIFICAR_ORDEN, "ORDEN",
                String.valueOf(orden.getId()), orden.getCentroAtencion().getId(), "Verificación pública de orden",
                "verificacion-publica", "PUBLICO");
        return publica(orden);
    }

    @Transactional
    public OrdenEstudio paraPdf(Long id) {
        return lectura(id, AccionAuditoriaHce.DESCARGAR_ORDEN, "Descarga de PDF de orden");
    }

    public boolean integridad(OrdenEstudio orden) {
        return datos.integridad(orden.getHashContenido(), ContenidoCanonico.orden(orden));
    }

    private OrdenEstudio lectura(Long id, AccionAuditoriaHce accion, String detalle) {
        OrdenEstudio orden = cargar(id);
        guard.assertLeeConsulta(orden.getConsulta());
        if (datos.vencerSiCorresponde(orden)) {
            ordenRepository.save(orden);
        }
        guard.auditar(orden.getPaciente(), accion, "ORDEN", String.valueOf(id),
                orden.getCentroAtencion().getId(), detalle);
        return orden;
    }

    private void aplicar(OrdenEstudio orden, OrdenEstudioWriteDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("La orden es obligatoria");
        }
        List<OrdenEstudioItemWriteDTO> items = dto.getItems() == null ? List.of() : dto.getItems();
        if (items.isEmpty() || items.size() > datos.tope(properties.getOrdenMaxItems())) {
            throw new IllegalArgumentException("La orden admite entre 1 y " + datos.tope(properties.getOrdenMaxItems())
                    + " prácticas");
        }
        orden.setUrgencia(parseUrgencia(dto.getUrgencia()));
        orden.setIndicacionClinica(datos.limpiar(dto.getIndicacionClinica()));
        datos.diagnostico(orden, dto.getDiagnosticoCode(), false);
        datos.congelar(orden, dto.getSexo(), dto.getFechaNacimiento(), dto.getNumeroAfiliado(),
                dto.getObraSocialNombre());
        orden.getItems().clear();
        int n = 1;
        for (OrdenEstudioItemWriteDTO itemDto : items) {
            if (itemDto == null || datos.limpiar(itemDto.getNombrePractica()) == null) {
                throw new IllegalArgumentException("El nombre de la práctica es obligatorio");
            }
            OrdenEstudioItem item = new OrdenEstudioItem();
            item.setOrden(orden);
            item.setOrdenNumero(n++);
            item.setNombrePractica(datos.limpiar(itemDto.getNombrePractica()));
            item.setSystemUri(datos.limpiar(itemDto.getSystemUri()));
            item.setCode(datos.limpiar(itemDto.getCode()));
            orden.getItems().add(item);
        }
        orden.setResumen(orden.getItems().get(0).getNombrePractica()
                + (orden.getItems().size() == 1 ? "" : " y " + (orden.getItems().size() - 1) + " más"));
    }

    private UrgenciaEstudio parseUrgencia(String valor) {
        if (valor == null || valor.isBlank()) {
            return UrgenciaEstudio.NORMAL;
        }
        try {
            return UrgenciaEstudio.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Urgencia inválida");
        }
    }

    private Consulta consulta(Long id) {
        return consultaRepository.findById(id).orElseThrow(() -> new HceNotFoundException("Consulta no encontrada"));
    }

    private OrdenEstudio cargar(Long id) {
        return ordenRepository.findById(id).orElseThrow(() -> new HceNotFoundException("Orden no encontrada"));
    }

    private Paciente pacienteActual() {
        var user = guard.usuarioActual();
        if (user.getRole() != Role.PACIENTE) {
            throw new unpsjb.labprog.backend.business.hce.HceAccessDeniedException(
                    "Este recurso es del portal del paciente");
        }
        return pacienteRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new HceNotFoundException("No hay un paciente asociado a la sesión"));
    }

    private VerificacionPublicaDTO publica(OrdenEstudio orden) {
        boolean ok = orden.getEstado() != EstadoPrescripcion.BORRADOR && integridad(orden);
        VerificacionPublicaDTO dto = new VerificacionPublicaDTO();
        dto.setTipoDocumento("ORDEN");
        dto.setEstado(orden.getEstado().name());
        dto.setIntegridad(ok);
        dto.setVigente(ok && (orden.getEstado() == EstadoPrescripcion.EMITIDA));
        dto.setFechaEmision(orden.getFechaEmision());
        dto.setFechaVencimiento(orden.getFechaVencimiento());
        dto.setPrescriptor(orden.getPrescriptorApellido() + ", " + orden.getPrescriptorNombre());
        dto.setMatricula(datos.matriculaPrincipal(orden));
        dto.setMatriculaNacional(orden.getMatriculaNacional());
        dto.setMatriculaProvincial(orden.getMatriculaProvincial());
        dto.setPacienteIniciales(datos.iniciales(orden.getPacienteNombre(), orden.getPacienteApellido()));
        dto.setDniParcial(datos.dniParcial(orden.getPacienteDni()));
        dto.setMensaje(RecetaService.mensajePublico(orden.getEstado(), ok));
        for (OrdenEstudioItem item : orden.getItems()) {
            ItemPublicoDTO publico = new ItemPublicoDTO();
            publico.setNombre(item.getNombrePractica());
            publico.setCodigo(item.getCode());
            dto.getItems().add(publico);
        }
        return dto;
    }

    private OrdenEstudioDTO aDto(OrdenEstudio orden) {
        OrdenEstudioDTO dto = new OrdenEstudioDTO();
        dto.setId(orden.getId());
        dto.setConsultaId(orden.getConsulta().getId());
        dto.setPacienteId(orden.getPaciente().getId());
        dto.setMedicoId(orden.getMedico().getId());
        dto.setCentroId(orden.getCentroAtencion().getId());
        dto.setEstado(orden.getEstado().name());
        dto.setUrgencia(orden.getUrgencia().name());
        dto.setIndicacionClinica(orden.getIndicacionClinica());
        dto.setIdentificador(orden.getIdentificador());
        dto.setCuir(orden.getCuir());
        dto.setCodigoVerificacion(orden.getCodigoVerificacion());
        dto.setUrlVerificacion(datos.url("/verificar-orden/", orden.getCodigoVerificacion()));
        dto.setHashContenido(orden.getHashContenido());
        dto.setIntegridad(orden.getHashContenido() != null && integridad(orden));
        dto.setTipoFirma(orden.getTipoFirma());
        dto.setFechaEmision(orden.getFechaEmision());
        dto.setFechaVencimiento(orden.getFechaVencimiento());
        dto.setFirmadoEn(orden.getFirmadoEn());
        dto.setDiagnosticoSystem(orden.getDiagnosticoSystem());
        dto.setDiagnosticoCode(orden.getDiagnosticoCode());
        dto.setDiagnosticoDisplay(orden.getDiagnosticoDisplay());
        dto.setPacienteNombre(orden.getPacienteNombre());
        dto.setPacienteApellido(orden.getPacienteApellido());
        dto.setPacienteDni(orden.getPacienteDni());
        dto.setPacienteSexo(orden.getPacienteSexo());
        dto.setPacienteNacimiento(orden.getPacienteNacimiento());
        dto.setObraSocialNombre(orden.getObraSocialNombre());
        dto.setNumeroAfiliado(orden.getNumeroAfiliado());
        dto.setPrescriptorNombre(orden.getPrescriptorNombre());
        dto.setPrescriptorApellido(orden.getPrescriptorApellido());
        dto.setMatricula(orden.getMatricula());
        dto.setMatriculaNacional(orden.getMatriculaNacional());
        dto.setMatriculaProvincial(orden.getMatriculaProvincial());
        dto.setRefepsId(orden.getRefepsId());
        dto.setEspecialidadNombre(orden.getEspecialidadNombre());
        dto.setCentroNombre(orden.getCentroNombre());
        dto.setPlataforma(orden.getPlataforma());
        dto.setMotivoAnulacion(orden.getMotivoAnulacion());
        dto.setAnuladoPor(orden.getAnuladoPor());
        dto.setAnuladoEn(orden.getAnuladoEn());
        dto.setCreadaEn(orden.getCreadaEn());
        for (OrdenEstudioItem item : orden.getItems()) {
            OrdenEstudioItemDTO itemDto = new OrdenEstudioItemDTO();
            itemDto.setId(item.getId());
            itemDto.setOrden(item.getOrdenNumero());
            itemDto.setNombrePractica(item.getNombrePractica());
            itemDto.setSystemUri(item.getSystemUri());
            itemDto.setCode(item.getCode());
            dto.getItems().add(itemDto);
        }
        return dto;
    }
}
