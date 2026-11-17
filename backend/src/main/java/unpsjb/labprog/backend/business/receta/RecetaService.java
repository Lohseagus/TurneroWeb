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
import unpsjb.labprog.backend.business.repository.CatalogoMedicamentoRepository;
import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.RecetaRepository;
import unpsjb.labprog.backend.dto.receta.CatalogoMedicamentoDTO;
import unpsjb.labprog.backend.dto.receta.ItemPublicoDTO;
import unpsjb.labprog.backend.dto.receta.RecetaDTO;
import unpsjb.labprog.backend.dto.receta.RecetaItemDTO;
import unpsjb.labprog.backend.dto.receta.RecetaItemWriteDTO;
import unpsjb.labprog.backend.dto.receta.RecetaWriteDTO;
import unpsjb.labprog.backend.dto.receta.VerificacionPublicaDTO;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;
import unpsjb.labprog.backend.model.hce.CatalogoMedicamento;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.DocumentoAsistencial;
import unpsjb.labprog.backend.model.hce.EstadoPrescripcion;
import unpsjb.labprog.backend.model.hce.ListaControlada;
import unpsjb.labprog.backend.model.hce.Receta;
import unpsjb.labprog.backend.model.hce.RecetaItem;
import unpsjb.labprog.backend.model.hce.TipoReceta;

@Service
public class RecetaService {

    private final RecetaRepository recetaRepository;
    private final ConsultaRepository consultaRepository;
    private final CatalogoMedicamentoRepository catalogoRepository;
    private final PacienteRepository pacienteRepository;
    private final HceAccessGuard guard;
    private final HceAuditoriaService auditoria;
    private final DatosDocumento datos;
    private final RecetaProperties properties;
    private final PlataformaRecetaElectronica plataforma;
    private final LimiteVerificacion limite;

    public RecetaService(RecetaRepository recetaRepository, ConsultaRepository consultaRepository,
            CatalogoMedicamentoRepository catalogoRepository, PacienteRepository pacienteRepository,
            HceAccessGuard guard, HceAuditoriaService auditoria, DatosDocumento datos, RecetaProperties properties,
            PlataformaRecetaElectronica plataforma, LimiteVerificacion limite) {
        this.recetaRepository = recetaRepository;
        this.consultaRepository = consultaRepository;
        this.catalogoRepository = catalogoRepository;
        this.pacienteRepository = pacienteRepository;
        this.guard = guard;
        this.auditoria = auditoria;
        this.datos = datos;
        this.properties = properties;
        this.plataforma = plataforma;
        this.limite = limite;
    }

    @Transactional
    public RecetaDTO crear(RecetaWriteDTO dto) {
        datos.exigirMedico("Solo un médico puede crear una receta");
        if (dto == null || dto.getConsultaId() == null) {
            throw new IllegalArgumentException("La consulta es obligatoria");
        }
        Consulta consulta = consulta(dto.getConsultaId());
        Medico medico = guard.assertPuedeCrear(consulta.getPaciente(), consulta.getCentroAtencion().getId(),
                consulta.getTurno());
        Receta receta = new Receta();
        receta.setConsulta(consulta);
        receta.setPaciente(consulta.getPaciente());
        receta.setMedico(medico);
        receta.setCentroAtencion(consulta.getCentroAtencion());
        receta.setEstado(EstadoPrescripcion.BORRADOR);
        receta.setCreadaEn(LocalDateTime.now());
        receta.setPlataforma(properties.getPlataforma());
        aplicar(receta, dto);
        receta = recetaRepository.save(receta);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.CREAR_RECETA, "RECETA", String.valueOf(receta.getId()),
                consulta.getCentroAtencion().getId(), "Alta de borrador de receta");
        return aDto(receta);
    }

    @Transactional
    public RecetaDTO actualizar(Long id, RecetaWriteDTO dto) {
        Receta receta = cargar(id);
        datos.exigirAutor(receta);
        datos.assertBorrador(receta.getEstado());
        aplicar(receta, dto);
        receta.setActualizadaEn(LocalDateTime.now());
        recetaRepository.save(receta);
        guard.auditar(receta.getPaciente(), AccionAuditoriaHce.CREAR_RECETA, "RECETA", String.valueOf(id),
                receta.getCentroAtencion().getId(), "Actualización de borrador de receta");
        return aDto(receta);
    }

    @Transactional
    public RecetaDTO emitir(Long id, LocalDate vencimiento) {
        Receta receta = cargar(id);
        datos.exigirAutor(receta);
        if (receta.getEstado() != EstadoPrescripcion.BORRADOR) {
            throw new HceImmutableException("La receta ya fue emitida");
        }
        if (receta.getTipo() != TipoReceta.COMUN) {
            throw new IllegalArgumentException(SustanciasControladas.MENSAJE);
        }
        if (receta.getItems().isEmpty()) {
            throw new IllegalArgumentException("La receta necesita al menos un medicamento");
        }
        for (RecetaItem item : receta.getItems()) {
            if (SustanciasControladas.esControlada(item.getNombreGenerico(), item.getLista(), receta.getTipo())) {
                throw new IllegalArgumentException(SustanciasControladas.MENSAJE);
            }
            exigir(item.getConcentracion(), "La concentración es obligatoria");
            exigir(item.getFormaFarmaceutica(), "La forma farmacéutica es obligatoria");
            if (item.getCantidad() == null || item.getCantidad() < 1) {
                throw new IllegalArgumentException("La cantidad de unidades es obligatoria");
            }
            exigir(item.getDosis(), "La dosis es obligatoria");
            exigir(item.getFrecuencia(), "La frecuencia es obligatoria");
            if (item.getVia() == null || item.getVia().isBlank()) {
                item.setVia("oral");
            }
        }
        datos.diagnostico(receta, receta.getDiagnosticoCode(), true);
        datos.congelar(receta, null, null, null, null);
        datos.exigirDatosPaciente(receta);
        LocalDate emision = LocalDate.now();
        receta.setFechaEmision(emision);
        receta.setFechaVencimiento(datos.vencimiento(vencimiento, emision));
        receta.setIdentificador(CodigosDocumento.identificador("R", emision));
        receta.setCodigoVerificacion(CodigosDocumento.token());
        receta.setTipoFirma(DocumentoAsistencial.FIRMA_ELECTRONICA_LOCAL);
        receta.setFirmadoEn(LocalDateTime.now());
        receta.setPlataforma(properties.getPlataforma());
        String hash = CodigosDocumento.sha256(ContenidoCanonico.receta(receta));
        receta.setHashContenido(hash);
        PlataformaRecetaElectronica.ResultadoEmision resultado = plataforma.emitir(
                new PlataformaRecetaElectronica.SolicitudEmision("RECETA", receta.getIdentificador(), hash));
        if (resultado.cuir() != null && !resultado.cuir().isBlank()) {
            receta.setCuir(resultado.cuir());
        }
        receta.setEstado(EstadoPrescripcion.EMITIDA);
        receta.setActualizadaEn(receta.getFirmadoEn());
        recetaRepository.save(receta);
        guard.auditar(receta.getPaciente(), AccionAuditoriaHce.EMITIR_RECETA, "RECETA", String.valueOf(id),
                receta.getCentroAtencion().getId(), "Emisión de receta");
        return aDto(receta);
    }

    @Transactional
    public RecetaDTO anular(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("La anulación necesita un motivo");
        }
        Receta receta = cargar(id);
        datos.exigirAutor(receta);
        if (receta.getEstado() == EstadoPrescripcion.ANULADA) {
            throw new IllegalStateException("La receta ya está anulada");
        }
        if (receta.getEstado() == EstadoPrescripcion.DISPENSADA_TOTAL) {
            throw new IllegalStateException("Una receta dispensada en su totalidad no se anula");
        }
        if (receta.getIdentificador() != null) {
            plataforma.anular(receta.getIdentificador(), motivo.trim());
        }
        receta.setEstado(EstadoPrescripcion.ANULADA);
        receta.setMotivoAnulacion(motivo.trim());
        receta.setAnuladoPor(guard.usuarioActual().getEmail());
        receta.setAnuladoEn(LocalDateTime.now());
        receta.setActualizadaEn(receta.getAnuladoEn());
        recetaRepository.save(receta);
        guard.auditar(receta.getPaciente(), AccionAuditoriaHce.ANULAR_RECETA, "RECETA", String.valueOf(id),
                receta.getCentroAtencion().getId(), "Anulación de receta");
        return aDto(receta);
    }

    @Transactional
    public RecetaDTO obtener(Long id) {
        Receta receta = lectura(id, AccionAuditoriaHce.VER_RECETA, "Lectura de receta");
        return aDto(receta);
    }

    @Transactional
    public List<RecetaDTO> porConsulta(Long consultaId) {
        Consulta consulta = consulta(consultaId);
        guard.assertLeeConsulta(consulta);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.VER_RECETA, "RECETA", "consulta-" + consultaId,
                consulta.getCentroAtencion().getId(), "Listado de recetas de la consulta");
        return recetaRepository.findByConsulta_IdOrderByCreadaEnDesc(consultaId).stream().map(receta -> {
            if (datos.vencerSiCorresponde(receta)) {
                recetaRepository.save(receta);
            }
            return aDto(receta);
        }).toList();
    }

    @Transactional
    public List<RecetaDTO> mias() {
        Paciente paciente = pacienteActual();
        guard.auditar(paciente, AccionAuditoriaHce.VER_RECETA, "RECETA", "mias", null, "Listado de recetas propias");
        return recetaRepository.findByPaciente_IdOrderByCreadaEnDesc(paciente.getId()).stream().map(receta -> {
            if (datos.vencerSiCorresponde(receta)) {
                recetaRepository.save(receta);
            }
            return aDto(receta);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoMedicamentoDTO> catalogo(String q) {
        datos.exigirMedico("El vademécum se consulta desde la consulta médica");
        String termino = q == null ? "" : q.trim();
        if (termino.length() < 2) {
            return List.of();
        }
        return catalogoRepository.findTop20ByNombreGenericoContainingIgnoreCaseOrderByNombreGenericoAsc(termino)
                .stream().map(this::aCatalogo).toList();
    }

    @Transactional
    public VerificacionPublicaDTO verificarPublico(String codigo, String ip) {
        limite.consumir(ip, properties.getVerificacionPorMinuto());
        if (codigo == null || codigo.isBlank()) {
            throw new HceNotFoundException("No hay una receta con ese código");
        }
        Receta receta = recetaRepository.findByCodigoVerificacion(codigo.trim())
                .orElseThrow(() -> new HceNotFoundException("No hay una receta con ese código"));
        if (datos.vencerSiCorresponde(receta)) {
            recetaRepository.save(receta);
        }
        auditoria.registrarSistema(receta.getPaciente().getId(), AccionAuditoriaHce.VERIFICAR_RECETA, "RECETA",
                String.valueOf(receta.getId()), receta.getCentroAtencion().getId(), "Verificación pública de receta",
                "verificacion-publica", "PUBLICO");
        return publica(receta, "RECETA");
    }

    /**
     * La dispensa no tiene endpoint público: la va a llamar la plataforma cuando exista
     * una farmacia autenticada. El estado queda modelado para ese momento.
     */
    @Transactional
    public RecetaDTO registrarDispensa(String identificador, boolean total) {
        Receta receta = recetaRepository.findByIdentificador(identificador)
                .orElseThrow(() -> new HceNotFoundException("Receta no encontrada"));
        if (datos.vencerSiCorresponde(receta)) {
            recetaRepository.save(receta);
        }
        if (receta.getEstado() != EstadoPrescripcion.EMITIDA
                && receta.getEstado() != EstadoPrescripcion.DISPENSADA_PARCIAL) {
            throw new IllegalStateException("Esta receta no se puede dispensar");
        }
        plataforma.registrarDispensa(identificador, total, null);
        receta.setEstado(total ? EstadoPrescripcion.DISPENSADA_TOTAL : EstadoPrescripcion.DISPENSADA_PARCIAL);
        receta.setActualizadaEn(LocalDateTime.now());
        recetaRepository.save(receta);
        auditoria.registrarSistema(receta.getPaciente().getId(), AccionAuditoriaHce.REGISTRAR_DISPENSA, "RECETA",
                String.valueOf(receta.getId()), receta.getCentroAtencion().getId(),
                total ? "Dispensa total" : "Dispensa parcial", "plataforma-local", "PLATAFORMA");
        return aDto(receta);
    }

    @Transactional
    public Receta paraPdf(Long id) {
        return lectura(id, AccionAuditoriaHce.DESCARGAR_RECETA, "Descarga de PDF de receta");
    }

    public boolean integridad(Receta receta) {
        return datos.integridad(receta.getHashContenido(), ContenidoCanonico.receta(receta));
    }

    private Receta lectura(Long id, AccionAuditoriaHce accion, String detalle) {
        Receta receta = cargar(id);
        guard.assertLeeConsulta(receta.getConsulta());
        if (datos.vencerSiCorresponde(receta)) {
            recetaRepository.save(receta);
        }
        guard.auditar(receta.getPaciente(), accion, "RECETA", String.valueOf(id),
                receta.getCentroAtencion().getId(), detalle);
        return receta;
    }

    private void aplicar(Receta receta, RecetaWriteDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("La receta es obligatoria");
        }
        TipoReceta tipo = parseTipo(dto.getTipo() == null ? receta.getTipo().name() : dto.getTipo());
        if (tipo != TipoReceta.COMUN) {
            throw new IllegalArgumentException(SustanciasControladas.MENSAJE);
        }
        receta.setTipo(tipo);
        List<RecetaItemWriteDTO> items = dto.getItems() == null ? List.of() : dto.getItems();
        if (items.isEmpty() || items.size() > datos.tope(properties.getMaxItems())) {
            throw new IllegalArgumentException("La receta admite entre 1 y " + datos.tope(properties.getMaxItems())
                    + " medicamentos");
        }
        datos.diagnostico(receta, dto.getDiagnosticoCode(), false);
        datos.congelar(receta, dto.getSexo(), dto.getFechaNacimiento(), dto.getNumeroAfiliado(),
                dto.getObraSocialNombre());
        receta.getItems().clear();
        int orden = 1;
        for (RecetaItemWriteDTO itemDto : items) {
            receta.getItems().add(item(receta, itemDto, orden++));
        }
        receta.setResumen(resumen(receta));
    }

    private RecetaItem item(Receta receta, RecetaItemWriteDTO dto, int orden) {
        if (dto == null || datos.limpiar(dto.getNombreGenerico()) == null) {
            throw new IllegalArgumentException("El nombre genérico es obligatorio");
        }
        CatalogoMedicamento catalogo = null;
        if (dto.getCatalogoId() != null) {
            catalogo = catalogoRepository.findById(dto.getCatalogoId())
                    .orElseThrow(() -> new HceNotFoundException("El medicamento no está en el catálogo"));
        }
        ListaControlada lista = lista(dto.getLista(), catalogo);
        String nombre = datos.limpiar(dto.getNombreGenerico());
        if (SustanciasControladas.esControlada(nombre, lista, receta.getTipo())) {
            throw new IllegalArgumentException(SustanciasControladas.MENSAJE);
        }
        if (dto.isNoSustituir() && datos.limpiar(dto.getJustificacionNoSustituir()) == null) {
            throw new IllegalArgumentException("Si no se puede sustituir, hace falta una justificación");
        }
        if (dto.getCantidad() != null && (dto.getCantidad() < 1 || dto.getCantidad() > 999)) {
            throw new IllegalArgumentException("La cantidad tiene que estar entre 1 y 999");
        }
        RecetaItem item = new RecetaItem();
        item.setReceta(receta);
        item.setOrden(orden);
        item.setNombreGenerico(nombre);
        item.setMarcaComercial(datos.limpiar(dto.getMarcaComercial()));
        item.setConcentracion(datos.limpiar(dto.getConcentracion() != null ? dto.getConcentracion()
                : catalogo == null ? null : catalogo.getConcentracion()));
        item.setFormaFarmaceutica(datos.limpiar(dto.getFormaFarmaceutica() != null ? dto.getFormaFarmaceutica()
                : catalogo == null ? null : catalogo.getFormaFarmaceutica()));
        item.setPresentacion(datos.limpiar(dto.getPresentacion()));
        item.setCantidad(dto.getCantidad());
        item.setCantidadEnLetras(dto.getCantidad() == null ? datos.limpiar(dto.getCantidadEnLetras())
                : letras(dto));
        item.setDosis(datos.limpiar(dto.getDosis()));
        item.setFrecuencia(datos.limpiar(dto.getFrecuencia()));
        item.setDuracion(datos.limpiar(dto.getDuracion()));
        item.setVia(datos.limpiar(dto.getVia() != null ? dto.getVia() : catalogo == null ? null : catalogo.getVia()));
        item.setIndicaciones(datos.limpiar(dto.getIndicaciones()));
        item.setNoSustituir(dto.isNoSustituir());
        item.setJustificacionNoSustituir(datos.limpiar(dto.getJustificacionNoSustituir()));
        item.setLista(ListaControlada.NINGUNA);
        return item;
    }

    private String letras(RecetaItemWriteDTO dto) {
        String escritas = datos.limpiar(dto.getCantidadEnLetras());
        if (escritas != null) {
            return escritas;
        }
        return CantidadEnLetras.de(dto.getCantidad());
    }

    private ListaControlada lista(String valor, CatalogoMedicamento catalogo) {
        ListaControlada lista = catalogo == null || catalogo.getLista() == null
                ? ListaControlada.NINGUNA
                : catalogo.getLista();
        if (valor != null && !valor.isBlank()) {
            try {
                ListaControlada pedida = ListaControlada.valueOf(valor.trim().toUpperCase());
                if (pedida != ListaControlada.NINGUNA) {
                    lista = pedida;
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Lista de control inválida");
            }
        }
        if (catalogo != null && catalogo.getTipo() != null && catalogo.getTipo() != TipoReceta.COMUN
                && lista == ListaControlada.NINGUNA) {
            lista = ListaControlada.IV;
        }
        return lista;
    }

    private String resumen(Receta receta) {
        if (receta.getItems().isEmpty()) {
            return "Receta";
        }
        String primero = receta.getItems().get(0).getNombreGenerico();
        if (receta.getItems().size() == 1) {
            return primero;
        }
        return primero + " y " + (receta.getItems().size() - 1) + " más";
    }

    private TipoReceta parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return TipoReceta.COMUN;
        }
        try {
            return TipoReceta.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de receta inválido");
        }
    }

    private void exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private Consulta consulta(Long id) {
        return consultaRepository.findById(id).orElseThrow(() -> new HceNotFoundException("Consulta no encontrada"));
    }

    private Receta cargar(Long id) {
        return recetaRepository.findById(id).orElseThrow(() -> new HceNotFoundException("Receta no encontrada"));
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

    VerificacionPublicaDTO publica(Receta receta, String tipo) {
        boolean ok = receta.getEstado() != EstadoPrescripcion.BORRADOR && integridad(receta);
        VerificacionPublicaDTO dto = new VerificacionPublicaDTO();
        dto.setTipoDocumento(tipo);
        dto.setEstado(receta.getEstado().name());
        dto.setIntegridad(ok);
        dto.setFechaEmision(receta.getFechaEmision());
        dto.setFechaVencimiento(receta.getFechaVencimiento());
        dto.setPrescriptor(nombre(receta.getPrescriptorApellido(), receta.getPrescriptorNombre()));
        dto.setMatricula(datos.matriculaPrincipal(receta));
        dto.setMatriculaNacional(receta.getMatriculaNacional());
        dto.setMatriculaProvincial(receta.getMatriculaProvincial());
        dto.setPacienteIniciales(datos.iniciales(receta.getPacienteNombre(), receta.getPacienteApellido()));
        dto.setDniParcial(datos.dniParcial(receta.getPacienteDni()));
        boolean vigente = ok && (receta.getEstado() == EstadoPrescripcion.EMITIDA
                || receta.getEstado() == EstadoPrescripcion.DISPENSADA_PARCIAL);
        dto.setVigente(vigente);
        dto.setMensaje(mensajePublico(receta.getEstado(), ok));
        for (RecetaItem item : receta.getItems()) {
            ItemPublicoDTO publico = new ItemPublicoDTO();
            publico.setNombre(item.getNombreGenerico());
            publico.setConcentracion(item.getConcentracion());
            publico.setForma(item.getFormaFarmaceutica());
            publico.setCantidad(item.getCantidad());
            publico.setCantidadEnLetras(item.getCantidadEnLetras());
            publico.setNoSustituir(item.isNoSustituir());
            dto.getItems().add(publico);
        }
        return dto;
    }

    static String mensajePublico(EstadoPrescripcion estado, boolean integridad) {
        if (!integridad) {
            return "La huella del contenido no coincide con la guardada al emitir.";
        }
        return switch (estado) {
            case EMITIDA -> "Vigente";
            case VENCIDA -> "Vencida";
            case ANULADA -> "Anulada";
            case DISPENSADA_PARCIAL -> "Dispensada en forma parcial";
            case DISPENSADA_TOTAL -> "Dispensada";
            case BORRADOR -> "Sin validez";
        };
    }

    private RecetaDTO aDto(Receta receta) {
        RecetaDTO dto = new RecetaDTO();
        dto.setId(receta.getId());
        dto.setConsultaId(receta.getConsulta().getId());
        dto.setPacienteId(receta.getPaciente().getId());
        dto.setMedicoId(receta.getMedico().getId());
        dto.setCentroId(receta.getCentroAtencion().getId());
        dto.setTipo(receta.getTipo().name());
        dto.setEstado(receta.getEstado().name());
        dto.setIdentificador(receta.getIdentificador());
        dto.setCuir(receta.getCuir());
        dto.setCodigoVerificacion(receta.getCodigoVerificacion());
        dto.setUrlVerificacion(datos.url("/verificar-receta/", receta.getCodigoVerificacion()));
        dto.setHashContenido(receta.getHashContenido());
        dto.setIntegridad(receta.getHashContenido() != null && integridad(receta));
        dto.setTipoFirma(receta.getTipoFirma());
        dto.setFechaEmision(receta.getFechaEmision());
        dto.setFechaVencimiento(receta.getFechaVencimiento());
        dto.setFirmadoEn(receta.getFirmadoEn());
        dto.setDiagnosticoSystem(receta.getDiagnosticoSystem());
        dto.setDiagnosticoCode(receta.getDiagnosticoCode());
        dto.setDiagnosticoDisplay(receta.getDiagnosticoDisplay());
        dto.setPacienteNombre(receta.getPacienteNombre());
        dto.setPacienteApellido(receta.getPacienteApellido());
        dto.setPacienteDni(receta.getPacienteDni());
        dto.setPacienteSexo(receta.getPacienteSexo());
        dto.setPacienteNacimiento(receta.getPacienteNacimiento());
        dto.setObraSocialNombre(receta.getObraSocialNombre());
        dto.setNumeroAfiliado(receta.getNumeroAfiliado());
        dto.setPrescriptorNombre(receta.getPrescriptorNombre());
        dto.setPrescriptorApellido(receta.getPrescriptorApellido());
        dto.setMatricula(receta.getMatricula());
        dto.setMatriculaNacional(receta.getMatriculaNacional());
        dto.setMatriculaProvincial(receta.getMatriculaProvincial());
        dto.setRefepsId(receta.getRefepsId());
        dto.setEspecialidadNombre(receta.getEspecialidadNombre());
        dto.setCentroNombre(receta.getCentroNombre());
        dto.setPlataforma(receta.getPlataforma());
        dto.setMotivoAnulacion(receta.getMotivoAnulacion());
        dto.setAnuladoPor(receta.getAnuladoPor());
        dto.setAnuladoEn(receta.getAnuladoEn());
        dto.setCreadaEn(receta.getCreadaEn());
        for (RecetaItem item : receta.getItems()) {
            dto.getItems().add(aItem(item));
        }
        return dto;
    }

    private RecetaItemDTO aItem(RecetaItem item) {
        RecetaItemDTO dto = new RecetaItemDTO();
        dto.setId(item.getId());
        dto.setOrden(item.getOrden());
        dto.setNombreGenerico(item.getNombreGenerico());
        dto.setMarcaComercial(item.getMarcaComercial());
        dto.setConcentracion(item.getConcentracion());
        dto.setFormaFarmaceutica(item.getFormaFarmaceutica());
        dto.setPresentacion(item.getPresentacion());
        dto.setCantidad(item.getCantidad());
        dto.setCantidadEnLetras(item.getCantidadEnLetras());
        dto.setDosis(item.getDosis());
        dto.setFrecuencia(item.getFrecuencia());
        dto.setDuracion(item.getDuracion());
        dto.setVia(item.getVia());
        dto.setIndicaciones(item.getIndicaciones());
        dto.setNoSustituir(item.isNoSustituir());
        dto.setJustificacionNoSustituir(item.getJustificacionNoSustituir());
        dto.setLista(item.getLista().name());
        return dto;
    }

    private CatalogoMedicamentoDTO aCatalogo(CatalogoMedicamento medicamento) {
        CatalogoMedicamentoDTO dto = new CatalogoMedicamentoDTO();
        dto.setId(medicamento.getId());
        dto.setNombreGenerico(medicamento.getNombreGenerico());
        dto.setConcentracion(medicamento.getConcentracion());
        dto.setFormaFarmaceutica(medicamento.getFormaFarmaceutica());
        dto.setVia(medicamento.getVia());
        dto.setTipo(medicamento.getTipo().name());
        dto.setLista(medicamento.getLista().name());
        return dto;
    }

    private String nombre(String apellido, String nombre) {
        if (apellido == null && nombre == null) {
            return "";
        }
        if (apellido == null) {
            return nombre;
        }
        if (nombre == null) {
            return apellido;
        }
        return apellido + ", " + nombre;
    }
}
