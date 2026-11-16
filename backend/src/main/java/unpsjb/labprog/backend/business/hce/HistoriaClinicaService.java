package unpsjb.labprog.backend.business.hce;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.AlergiaRepository;
import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.CodigoCie10Repository;
import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.DiagnosticoConsultaRepository;
import unpsjb.labprog.backend.business.repository.EnmiendaRepository;
import unpsjb.labprog.backend.business.repository.EspecialidadRepository;
import unpsjb.labprog.backend.business.repository.MedicacionHabitualRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.PerfilClinicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.repository.VacunaRepository;
import unpsjb.labprog.backend.dto.hce.AlergiaDTO;
import unpsjb.labprog.backend.dto.hce.AuditoriaHceDTO;
import unpsjb.labprog.backend.dto.hce.Cie10DTO;
import unpsjb.labprog.backend.dto.hce.ConsultaDTO;
import unpsjb.labprog.backend.dto.hce.ConsultaWriteDTO;
import unpsjb.labprog.backend.dto.hce.DiagnosticoDTO;
import unpsjb.labprog.backend.dto.hce.EnmiendaDTO;
import unpsjb.labprog.backend.dto.hce.EnmiendaWriteDTO;
import unpsjb.labprog.backend.dto.hce.MedicacionDTO;
import unpsjb.labprog.backend.dto.hce.MetadataHceDTO;
import unpsjb.labprog.backend.dto.hce.PacienteResumenHceDTO;
import unpsjb.labprog.backend.dto.hce.PerfilClinicoDTO;
import unpsjb.labprog.backend.dto.hce.TimelineItemDTO;
import unpsjb.labprog.backend.dto.hce.TurnoContextoHceDTO;
import unpsjb.labprog.backend.dto.hce.VacunaDTO;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.AccesoHistoriaClinica;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;
import unpsjb.labprog.backend.model.hce.Alergia;
import unpsjb.labprog.backend.model.hce.CodigoCie10;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.CriticidadAlergia;
import unpsjb.labprog.backend.model.hce.DiagnosticoConsulta;
import unpsjb.labprog.backend.model.hce.Enmienda;
import unpsjb.labprog.backend.model.hce.EstadoConsulta;
import unpsjb.labprog.backend.model.hce.GrupoSanguineo;
import unpsjb.labprog.backend.model.hce.MedicacionHabitual;
import unpsjb.labprog.backend.model.hce.PerfilClinico;
import unpsjb.labprog.backend.model.hce.TipoDiagnostico;
import unpsjb.labprog.backend.model.hce.TipoEventoHistoria;
import unpsjb.labprog.backend.model.hce.Vacuna;
import unpsjb.labprog.backend.business.repository.AccesoHistoriaClinicaRepository;
import unpsjb.labprog.backend.business.repository.AdjuntoClinicoRepository;

@Service
public class HistoriaClinicaService {

    private final HceAccessGuard guard;
    private final PacienteRepository pacienteRepository;
    private final TurnoRepository turnoRepository;
    private final ConsultaRepository consultaRepository;
    private final DiagnosticoConsultaRepository diagnosticoRepository;
    private final EnmiendaRepository enmiendaRepository;
    private final PerfilClinicoRepository perfilRepository;
    private final AlergiaRepository alergiaRepository;
    private final MedicacionHabitualRepository medicacionRepository;
    private final VacunaRepository vacunaRepository;
    private final CodigoCie10Repository cie10Repository;
    private final CentroAtencionRepository centroRepository;
    private final EspecialidadRepository especialidadRepository;
    private final AccesoHistoriaClinicaRepository accesoRepository;
    private final AdjuntoClinicoRepository adjuntoRepository;

    public HistoriaClinicaService(HceAccessGuard guard, PacienteRepository pacienteRepository,
            TurnoRepository turnoRepository, ConsultaRepository consultaRepository,
            DiagnosticoConsultaRepository diagnosticoRepository, EnmiendaRepository enmiendaRepository,
            PerfilClinicoRepository perfilRepository, AlergiaRepository alergiaRepository,
            MedicacionHabitualRepository medicacionRepository, VacunaRepository vacunaRepository,
            CodigoCie10Repository cie10Repository, CentroAtencionRepository centroRepository,
            EspecialidadRepository especialidadRepository, AccesoHistoriaClinicaRepository accesoRepository,
            AdjuntoClinicoRepository adjuntoRepository) {
        this.guard = guard;
        this.pacienteRepository = pacienteRepository;
        this.turnoRepository = turnoRepository;
        this.consultaRepository = consultaRepository;
        this.diagnosticoRepository = diagnosticoRepository;
        this.enmiendaRepository = enmiendaRepository;
        this.perfilRepository = perfilRepository;
        this.alergiaRepository = alergiaRepository;
        this.medicacionRepository = medicacionRepository;
        this.vacunaRepository = vacunaRepository;
        this.cie10Repository = cie10Repository;
        this.centroRepository = centroRepository;
        this.especialidadRepository = especialidadRepository;
        this.accesoRepository = accesoRepository;
        this.adjuntoRepository = adjuntoRepository;
    }

    @Transactional(readOnly = true)
    public List<Cie10DTO> buscarCie10(String q) {
        User user = guard.usuarioActual();
        if (user.getRole() != Role.MEDICO) {
            throw new HceAccessDeniedException("El catálogo CIE-10 se consulta desde la consulta médica");
        }
        String termino = q == null ? "" : q.trim();
        if (termino.length() < 2) {
            return List.of();
        }
        return cie10Repository.buscar(termino).stream().limit(30).map(this::aCie10).toList();
    }

    @Transactional(readOnly = true)
    public List<PacienteResumenHceDTO> misPacientes() {
        Medico medico = guard.exigirMedico(guard.usuarioActual());
        Map<Integer, Paciente> pacientes = new LinkedHashMap<>();
        for (Paciente paciente : turnoRepository.findPacientesAtendidos(medico.getId(), EstadoTurno.CANCELADO)) {
            pacientes.put(paciente.getId(), paciente);
        }
        for (Paciente paciente : consultaRepository.findPacientesDelMedico(medico.getId())) {
            pacientes.put(paciente.getId(), paciente);
        }
        return pacientes.values().stream()
                .sorted(Comparator.comparing(Paciente::getApellido, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public TurnoContextoHceDTO contextoTurno(Integer turnoId) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new HceNotFoundException("Turno no encontrado"));
        guard.assertPuedeCrear(turno.getPaciente(), turno.getCentroAtencion().getId(), turno);
        TurnoContextoHceDTO dto = new TurnoContextoHceDTO();
        dto.setTurnoId(turno.getId());
        dto.setPacienteId(turno.getPaciente().getId());
        dto.setPacienteNombre(turno.getPaciente().getNombre());
        dto.setPacienteApellido(turno.getPaciente().getApellido());
        dto.setDni(turno.getPaciente().getDni());
        dto.setCentroId(turno.getCentroAtencion().getId());
        dto.setCentroNombre(turno.getCentroAtencion().getNombre());
        dto.setMedicoId(turno.getMedico().getId());
        if (turno.getEspecialidad() != null) {
            dto.setEspecialidadId(turno.getEspecialidad().getId());
            dto.setEspecialidadNombre(turno.getEspecialidad().getNombre());
        }
        consultaRepository.findByPaciente_IdOrderByCreadaEnDesc(turno.getPaciente().getId()).stream()
                .filter(consulta -> consulta.getTurno() != null && turno.getId().equals(consulta.getTurno().getId()))
                .findFirst()
                .ifPresent(consulta -> dto.setConsultaId(consulta.getId()));
        return dto;
    }

    @Transactional(readOnly = true)
    public Paciente pacienteActual() {
        User user = guard.usuarioActual();
        if (user.getRole() != Role.PACIENTE) {
            throw new HceAccessDeniedException("Este recurso es del portal del paciente");
        }
        return pacienteRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new HceNotFoundException("No hay un paciente asociado a la sesión"));
    }

    @Transactional
    public PerfilClinicoDTO obtenerPerfil(Integer pacienteId) {
        Paciente paciente = cargarPaciente(pacienteId);
        guard.assertLeePerfil(paciente);
        guard.auditar(paciente, AccionAuditoriaHce.VER_PERFIL, "PERFIL", String.valueOf(pacienteId), null,
                "Lectura del perfil clínico");
        return aPerfil(paciente, asegurarPerfil(paciente));
    }

    @Transactional
    public PerfilClinicoDTO actualizarPerfil(Integer pacienteId, PerfilClinicoDTO dto) {
        Paciente paciente = cargarPaciente(pacienteId);
        guard.assertEscribePerfil(paciente);
        PerfilClinico perfil = asegurarPerfil(paciente);
        perfil.setAntecedentesPersonales(limpiar(dto.getAntecedentesPersonales()));
        perfil.setAntecedentesFamiliares(limpiar(dto.getAntecedentesFamiliares()));
        perfil.setAntecedentesQuirurgicos(limpiar(dto.getAntecedentesQuirurgicos()));
        perfil.setHabitos(limpiar(dto.getHabitos()));
        perfil.setGrupoSanguineo(parseGrupo(dto.getGrupoSanguineo()));
        perfil.setComparteEntreCentros(dto.isComparteEntreCentros());
        perfil.setActualizadoEn(LocalDateTime.now());
        perfil.setActualizadoPor(guard.usuarioActual().getEmail());
        perfilRepository.save(perfil);
        if (dto.getIdentificadorFederado() != null) {
            String federado = dto.getIdentificadorFederado().trim();
            paciente.setIdentificadorFederado(federado.isEmpty() ? null : federado);
            pacienteRepository.save(paciente);
        }
        sincronizarAlergias(perfil, dto.getAlergias());
        sincronizarMedicacion(perfil, dto.getMedicaciones());
        agregarVacunasNuevas(perfil, dto.getVacunas());
        guard.auditar(paciente, AccionAuditoriaHce.ACTUALIZAR_PERFIL, "PERFIL", String.valueOf(pacienteId), null,
                "Actualización del perfil clínico");
        return aPerfil(paciente, perfil);
    }

    @Transactional
    public PerfilClinicoDTO actualizarConsentimiento(boolean comparte) {
        Paciente paciente = pacienteActual();
        PerfilClinico perfil = asegurarPerfil(paciente);
        perfil.setComparteEntreCentros(comparte);
        perfil.setActualizadoEn(LocalDateTime.now());
        perfil.setActualizadoPor(paciente.getEmail());
        perfilRepository.save(perfil);
        guard.auditar(paciente, AccionAuditoriaHce.CONSENTIMIENTO, "PERFIL", String.valueOf(paciente.getId()), null,
                comparte ? "Consentimiento de cruce de centros otorgado" : "Consentimiento de cruce de centros revocado");
        return aPerfil(paciente, perfil);
    }

    @Transactional(readOnly = true)
    public List<TimelineItemDTO> timeline(Integer pacienteId, Integer especialidadId, Integer centroId,
            Integer medicoId, String tipo) {
        Paciente paciente = cargarPaciente(pacienteId);
        guard.assertLeePerfil(paciente);
        TipoEventoHistoria filtroTipo = parseTipo(tipo);
        List<TimelineItemDTO> items = new ArrayList<>();
        for (Consulta consulta : consultaRepository.findByPaciente_IdOrderByCreadaEnDesc(pacienteId)) {
            if (!visible(consulta)) {
                continue;
            }
            if (centroId != null && !centroId.equals(consulta.getCentroAtencion().getId())) {
                continue;
            }
            if (medicoId != null && !medicoId.equals(consulta.getMedico().getId())) {
                continue;
            }
            if (especialidadId != null && (consulta.getEspecialidad() == null
                    || !especialidadId.equals(consulta.getEspecialidad().getId()))) {
                continue;
            }
            if (filtroTipo == null || filtroTipo == TipoEventoHistoria.CONSULTA) {
                items.add(itemConsulta(consulta));
            }
        }
        if (filtroTipo == null || filtroTipo == TipoEventoHistoria.ENMIENDA) {
            for (Enmienda enmienda : enmiendaRepository.findByConsulta_Paciente_IdOrderByCreadaEnDesc(pacienteId)) {
                Consulta consulta = enmienda.getConsulta();
                if (!visible(consulta) || !pasaFiltroCentroMedicoEspecialidad(consulta, centroId, medicoId, especialidadId)) {
                    continue;
                }
                items.add(itemEnmienda(enmienda));
            }
        }
        if (filtroTipo == null || filtroTipo == TipoEventoHistoria.ADJUNTO) {
            adjuntoRepository.findByPaciente_IdOrderByCreadoEnDesc(pacienteId).forEach(adjunto -> {
                if (adjunto.getConsulta() != null && !visible(adjunto.getConsulta())) {
                    return;
                }
                if (adjunto.getConsulta() == null && centroId != null) {
                    return;
                }
                if (adjunto.getConsulta() != null
                        && !pasaFiltroCentroMedicoEspecialidad(adjunto.getConsulta(), centroId, medicoId, especialidadId)) {
                    return;
                }
                TimelineItemDTO item = new TimelineItemDTO();
                item.setTipo(TipoEventoHistoria.ADJUNTO.name());
                item.setRecursoId(String.valueOf(adjunto.getId()));
                item.setFecha(adjunto.getCreadoEn());
                item.setResumen(adjunto.getNombreOriginal());
                item.setEstado(adjunto.getTipo().name());
                if (adjunto.getConsulta() != null) {
                    item.setConsultaId(adjunto.getConsulta().getId());
                    item.setCentroId(adjunto.getConsulta().getCentroAtencion().getId());
                    item.setCentroNombre(adjunto.getConsulta().getCentroAtencion().getNombre());
                } else if (adjunto.getCentroAtencion() != null) {
                    item.setCentroId(adjunto.getCentroAtencion().getId());
                    item.setCentroNombre(adjunto.getCentroAtencion().getNombre());
                }
                items.add(item);
            });
        }
        items.sort(Comparator.comparing(TimelineItemDTO::getFecha, Comparator.nullsLast(Comparator.reverseOrder())));
        guard.auditar(paciente, AccionAuditoriaHce.VER_TIMELINE, "TIMELINE", String.valueOf(pacienteId), centroId,
                "Lectura de la línea de tiempo");
        return items;
    }

    @Transactional(readOnly = true)
    public MetadataHceDTO metadata(Integer pacienteId) {
        Paciente paciente = cargarPaciente(pacienteId);
        guard.assertLeeMetadata(paciente);
        List<Consulta> consultas = consultaRepository.findByPaciente_IdOrderByCreadaEnDesc(pacienteId);
        MetadataHceDTO dto = new MetadataHceDTO();
        dto.setPacienteId(pacienteId);
        dto.setTienePerfil(perfilRepository.findByPaciente_Id(pacienteId).isPresent());
        dto.setComparteEntreCentros(guard.consentimiento(paciente));
        dto.setCantidadConsultas(consultas.size());
        dto.setCantidadBorradores((int) consultas.stream().filter(c -> c.getEstado() == EstadoConsulta.BORRADOR).count());
        dto.setCantidadCerradas((int) consultas.stream().filter(c -> c.getEstado() == EstadoConsulta.CERRADA).count());
        dto.setCantidadAdjuntos(adjuntoRepository.findByPaciente_IdOrderByCreadoEnDesc(pacienteId).size());
        dto.setCantidadEnmiendas(enmiendaRepository.findByConsulta_Paciente_IdOrderByCreadaEnDesc(pacienteId).size());
        dto.setUltimaActividad(consultas.stream().map(Consulta::getCreadaEn).filter(Objects::nonNull).findFirst().orElse(null));
        guard.auditar(paciente, AccionAuditoriaHce.VER_METADATA, "METADATA", String.valueOf(pacienteId), null,
                "Lectura de metadatos");
        return dto;
    }

    @Transactional(readOnly = true)
    public List<AuditoriaHceDTO> auditoria(Integer pacienteId) {
        Paciente paciente = cargarPaciente(pacienteId);
        guard.assertLeeMetadata(paciente);
        guard.auditar(paciente, AccionAuditoriaHce.VER_AUDITORIA, "AUDITORIA", String.valueOf(pacienteId), null,
                "Lectura del registro de accesos");
        return accesoRepository.findByPacienteIdOrderByOcurridoEnDesc(pacienteId).stream().map(this::aAuditoria).toList();
    }

    @Transactional
    public ConsultaDTO crear(ConsultaWriteDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("La consulta es obligatoria");
        }
        Turno turno = null;
        if (dto.getTurnoId() != null) {
            turno = turnoRepository.findById(dto.getTurnoId())
                    .orElseThrow(() -> new HceNotFoundException("Turno no encontrado"));
            if (consultaRepository.existsByTurno_Id(turno.getId())) {
                throw new IllegalStateException("Ya existe una consulta para este turno");
            }
        }
        Integer centroId = turno != null ? turno.getCentroAtencion().getId() : dto.getCentroId();
        if (centroId == null) {
            throw new IllegalArgumentException("El centro de atención es obligatorio");
        }
        Paciente paciente = turno != null ? turno.getPaciente() : cargarPaciente(dto.getPacienteId());
        Medico medico = guard.assertPuedeCrear(paciente, centroId, turno);
        Consulta consulta = new Consulta();
        consulta.setPaciente(paciente);
        consulta.setTurno(turno);
        consulta.setMedico(medico);
        consulta.setCentroAtencion(centroRepository.findById(centroId)
                .orElseThrow(() -> new HceNotFoundException("Centro de atención no encontrado")));
        consulta.setEstado(EstadoConsulta.BORRADOR);
        consulta.setCreadaEn(LocalDateTime.now());
        if (turno != null && turno.getEspecialidad() != null && dto.getEspecialidadId() == null) {
            consulta.setEspecialidad(turno.getEspecialidad());
        }
        aplicar(consulta, dto);
        consulta = consultaRepository.save(consulta);
        reemplazarDiagnosticos(consulta, dto.getDiagnosticos());
        guard.auditar(paciente, AccionAuditoriaHce.CREAR_CONSULTA, "CONSULTA", String.valueOf(consulta.getId()),
                centroId, "Alta de borrador");
        return aConsulta(consulta);
    }

    @Transactional
    public ConsultaDTO obtener(Long id) {
        Consulta consulta = cargar(id);
        guard.assertLeeConsulta(consulta);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.VER_CONSULTA, "CONSULTA", String.valueOf(id),
                consulta.getCentroAtencion().getId(), "Lectura de consulta");
        return aConsulta(consulta);
    }

    @Transactional
    public ConsultaDTO actualizar(Long id, ConsultaWriteDTO dto) {
        Consulta consulta = cargar(id);
        exigirAutor(consulta);
        ConsultaLifecycle.assertBorrador(consulta.getEstado());
        aplicar(consulta, dto);
        consulta.setActualizadaEn(LocalDateTime.now());
        consultaRepository.save(consulta);
        reemplazarDiagnosticos(consulta, dto.getDiagnosticos());
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.ACTUALIZAR_CONSULTA, "CONSULTA", String.valueOf(id),
                consulta.getCentroAtencion().getId(), "Actualización de borrador");
        return aConsulta(consulta);
    }

    @Transactional
    public ConsultaDTO cerrar(Long id) {
        Consulta consulta = cargar(id);
        exigirAutor(consulta);
        ConsultaLifecycle.assertBorrador(consulta.getEstado());
        if (consulta.getMotivoConsulta() == null || consulta.getMotivoConsulta().isBlank()) {
            throw new IllegalArgumentException("El motivo de consulta es obligatorio para cerrar");
        }
        consulta.setEstado(EstadoConsulta.CERRADA);
        consulta.setCerradaEn(LocalDateTime.now());
        consulta.setActualizadaEn(consulta.getCerradaEn());
        consultaRepository.save(consulta);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.CERRAR_CONSULTA, "CONSULTA", String.valueOf(id),
                consulta.getCentroAtencion().getId(), "Cierre de consulta");
        return aConsulta(consulta);
    }

    @Transactional
    public EnmiendaDTO enmendar(Long consultaId, EnmiendaWriteDTO dto) {
        Consulta consulta = cargar(consultaId);
        guard.assertPuedeEnmendar(consulta);
        ConsultaLifecycle.assertCerradaParaEnmienda(consulta.getEstado());
        if (dto == null || dto.getTexto() == null || dto.getTexto().isBlank()) {
            throw new IllegalArgumentException("El texto de la enmienda es obligatorio");
        }
        if (dto.getMotivo() == null || dto.getMotivo().isBlank()) {
            throw new IllegalArgumentException("El motivo de la enmienda es obligatorio");
        }
        Enmienda enmienda = new Enmienda();
        enmienda.setConsulta(consulta);
        enmienda.setAutor(guard.exigirMedico(guard.usuarioActual()));
        enmienda.setTexto(dto.getTexto().trim());
        enmienda.setMotivo(dto.getMotivo().trim());
        enmienda.setCreadaEn(LocalDateTime.now());
        enmienda = enmiendaRepository.save(enmienda);
        guard.auditar(consulta.getPaciente(), AccionAuditoriaHce.ENMIENDA, "ENMIENDA", String.valueOf(enmienda.getId()),
                consulta.getCentroAtencion().getId(), "Enmienda por: " + recorte(dto.getMotivo()));
        return aEnmienda(enmienda);
    }

    @Transactional(readOnly = true)
    public List<ConsultaDTO> consultasVisibles(Integer pacienteId) {
        Paciente paciente = cargarPaciente(pacienteId);
        guard.assertLeePerfil(paciente);
        return consultaRepository.findByPaciente_IdOrderByCreadaEnDesc(pacienteId).stream()
                .filter(this::visible)
                .map(this::aConsulta)
                .toList();
    }

    @Transactional(readOnly = true)
    public Consulta cargar(Long id) {
        return consultaRepository.findById(id).orElseThrow(() -> new HceNotFoundException("Consulta no encontrada"));
    }

    @Transactional(readOnly = true)
    public Paciente cargarPaciente(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("El paciente es obligatorio");
        }
        return pacienteRepository.findById(id).orElseThrow(() -> new HceNotFoundException("Paciente no encontrado"));
    }

    private void exigirAutor(Consulta consulta) {
        guard.assertLeeConsulta(consulta);
        User user = guard.usuarioActual();
        if (user.getRole() != Role.MEDICO) {
            throw new HceAccessDeniedException("Solo el médico autor puede modificar la consulta");
        }
        Medico medico = guard.exigirMedico(user);
        if (!consulta.getMedico().getId().equals(medico.getId())) {
            throw new HceAccessDeniedException("Solo el médico autor puede modificar el borrador");
        }
    }

    private void aplicar(Consulta consulta, ConsultaWriteDTO dto) {
        if (dto == null) {
            return;
        }
        consulta.setMotivoConsulta(limpiar(dto.getMotivoConsulta()));
        consulta.setAnamnesis(limpiar(dto.getAnamnesis()));
        consulta.setExamenFisico(limpiar(dto.getExamenFisico()));
        consulta.setIndicaciones(limpiar(dto.getIndicaciones()));
        consulta.setPlan(limpiar(dto.getPlan()));
        consulta.setProximoControl(dto.getProximoControl());
        validarSignos(dto);
        consulta.setTensionSistolica(dto.getTensionSistolica());
        consulta.setTensionDiastolica(dto.getTensionDiastolica());
        consulta.setFrecuenciaCardiaca(dto.getFrecuenciaCardiaca());
        consulta.setTemperatura(dto.getTemperatura());
        consulta.setPesoKg(dto.getPesoKg());
        consulta.setTallaCm(dto.getTallaCm());
        consulta.setImc(ConsultaLifecycle.calcularImc(dto.getPesoKg(), dto.getTallaCm()));
        if (dto.getEspecialidadId() != null) {
            Especialidad especialidad = especialidadRepository.findById(dto.getEspecialidadId())
                    .orElseThrow(() -> new HceNotFoundException("Especialidad no encontrada"));
            consulta.setEspecialidad(especialidad);
        }
    }

    private void validarSignos(ConsultaWriteDTO dto) {
        rango(dto.getTensionSistolica(), 40, 300, "tensión sistólica");
        rango(dto.getTensionDiastolica(), 20, 200, "tensión diastólica");
        rango(dto.getFrecuenciaCardiaca(), 20, 250, "frecuencia cardíaca");
        if (dto.getTemperatura() != null && (dto.getTemperatura() < 30 || dto.getTemperatura() > 45)) {
            throw new IllegalArgumentException("La temperatura está fuera de rango");
        }
        if (dto.getPesoKg() != null && (dto.getPesoKg() <= 0 || dto.getPesoKg() > 400)) {
            throw new IllegalArgumentException("El peso está fuera de rango");
        }
        if (dto.getTallaCm() != null && (dto.getTallaCm() < 30 || dto.getTallaCm() > 250)) {
            throw new IllegalArgumentException("La talla está fuera de rango");
        }
    }

    private void rango(Integer valor, int min, int max, String nombre) {
        if (valor != null && (valor < min || valor > max)) {
            throw new IllegalArgumentException("La " + nombre + " está fuera de rango");
        }
    }

    private void reemplazarDiagnosticos(Consulta consulta, List<DiagnosticoDTO> diagnosticos) {
        diagnosticoRepository.deleteByConsultaId(consulta.getId());
        if (diagnosticos == null) {
            return;
        }
        for (DiagnosticoDTO dto : diagnosticos) {
            if (dto == null || dto.getCode() == null || dto.getCode().isBlank()) {
                continue;
            }
            String sistema = dto.getSystemUri() == null || dto.getSystemUri().isBlank()
                    ? CodigoCie10.SISTEMA_CIE10
                    : dto.getSystemUri().trim();
            String codigo = dto.getCode().trim().toUpperCase(Locale.ROOT);
            CodigoCie10 cie = cie10Repository.findBySystemUriAndCode(sistema, codigo)
                    .orElseThrow(() -> new IllegalArgumentException("El código " + codigo + " no está en el catálogo CIE-10"));
            DiagnosticoConsulta diagnostico = new DiagnosticoConsulta();
            diagnostico.setConsulta(consulta);
            diagnostico.setSystemUri(cie.getSystemUri());
            diagnostico.setCode(cie.getCode());
            diagnostico.setDisplay(cie.getDisplay());
            diagnostico.setTipo(parseTipoDiagnostico(dto.getTipo()));
            diagnosticoRepository.save(diagnostico);
        }
    }

    private PerfilClinico asegurarPerfil(Paciente paciente) {
        return perfilRepository.findByPaciente_Id(paciente.getId()).orElseGet(() -> {
            PerfilClinico perfil = new PerfilClinico();
            perfil.setPaciente(paciente);
            perfil.setGrupoSanguineo(GrupoSanguineo.DESCONOCIDO);
            perfil.setActualizadoEn(LocalDateTime.now());
            return perfilRepository.save(perfil);
        });
    }

    private void sincronizarAlergias(PerfilClinico perfil, List<AlergiaDTO> dtos) {
        List<Alergia> actuales = alergiaRepository.findByPerfil_IdOrderByIdAsc(perfil.getId());
        List<Long> conservados = new ArrayList<>();
        if (dtos != null) {
            for (AlergiaDTO dto : dtos) {
                if (dto == null || dto.getSustancia() == null || dto.getSustancia().isBlank()) {
                    continue;
                }
                Alergia alergia = actuales.stream()
                        .filter(item -> dto.getId() != null && dto.getId().equals(item.getId()))
                        .findFirst()
                        .orElseGet(Alergia::new);
                alergia.setPerfil(perfil);
                alergia.setSustancia(dto.getSustancia().trim());
                alergia.setReaccion(limpiar(dto.getReaccion()));
                alergia.setCriticidad(parseCriticidad(dto.getCriticidad()));
                alergia.setSystemUri(limpiar(dto.getSystemUri()));
                alergia.setCode(limpiar(dto.getCode()));
                alergia.setActiva(dto.isActiva());
                alergiaRepository.save(alergia);
                if (alergia.getId() != null) {
                    conservados.add(alergia.getId());
                }
            }
        }
        for (Alergia actual : actuales) {
            if (!conservados.contains(actual.getId())) {
                actual.setActiva(false);
                alergiaRepository.save(actual);
            }
        }
    }

    private void sincronizarMedicacion(PerfilClinico perfil, List<MedicacionDTO> dtos) {
        List<MedicacionHabitual> actuales = medicacionRepository.findByPerfil_IdOrderByIdAsc(perfil.getId());
        List<Long> conservados = new ArrayList<>();
        if (dtos != null) {
            for (MedicacionDTO dto : dtos) {
                if (dto == null || dto.getFarmaco() == null || dto.getFarmaco().isBlank()) {
                    continue;
                }
                MedicacionHabitual medicacion = actuales.stream()
                        .filter(item -> dto.getId() != null && dto.getId().equals(item.getId()))
                        .findFirst()
                        .orElseGet(MedicacionHabitual::new);
                medicacion.setPerfil(perfil);
                medicacion.setFarmaco(dto.getFarmaco().trim());
                medicacion.setDosis(limpiar(dto.getDosis()));
                medicacion.setFrecuencia(limpiar(dto.getFrecuencia()));
                medicacion.setSystemUri(limpiar(dto.getSystemUri()));
                medicacion.setCode(limpiar(dto.getCode()));
                medicacion.setActiva(dto.isActiva());
                medicacionRepository.save(medicacion);
                if (medicacion.getId() != null) {
                    conservados.add(medicacion.getId());
                }
            }
        }
        for (MedicacionHabitual actual : actuales) {
            if (!conservados.contains(actual.getId())) {
                actual.setActiva(false);
                medicacionRepository.save(actual);
            }
        }
    }

    private void agregarVacunasNuevas(PerfilClinico perfil, List<VacunaDTO> dtos) {
        if (dtos == null) {
            return;
        }
        for (VacunaDTO dto : dtos) {
            if (dto == null || dto.getId() != null || dto.getNombre() == null || dto.getNombre().isBlank()) {
                continue;
            }
            Vacuna vacuna = new Vacuna();
            vacuna.setPerfil(perfil);
            vacuna.setNombre(dto.getNombre().trim());
            vacuna.setFechaAplicacion(dto.getFechaAplicacion());
            vacuna.setDosis(limpiar(dto.getDosis()));
            vacuna.setLote(limpiar(dto.getLote()));
            vacunaRepository.save(vacuna);
        }
    }

    private boolean visible(Consulta consulta) {
        User user = guard.usuarioActual();
        if (user.getRole() == Role.PACIENTE) {
            return true;
        }
        if (guard.consentimiento(consulta.getPaciente())) {
            return true;
        }
        Medico medico = guard.exigirMedico(user);
        return guard.centrosDe(medico).contains(consulta.getCentroAtencion().getId())
                || consulta.getMedico().getId().equals(medico.getId());
    }

    private boolean pasaFiltroCentroMedicoEspecialidad(Consulta consulta, Integer centroId, Integer medicoId,
            Integer especialidadId) {
        if (centroId != null && !centroId.equals(consulta.getCentroAtencion().getId())) {
            return false;
        }
        if (medicoId != null && !medicoId.equals(consulta.getMedico().getId())) {
            return false;
        }
        return especialidadId == null || (consulta.getEspecialidad() != null
                && especialidadId.equals(consulta.getEspecialidad().getId()));
    }

    private TimelineItemDTO itemConsulta(Consulta consulta) {
        TimelineItemDTO item = new TimelineItemDTO();
        item.setTipo(TipoEventoHistoria.CONSULTA.name());
        item.setRecursoId(String.valueOf(consulta.getId()));
        item.setConsultaId(consulta.getId());
        item.setFecha(consulta.getCerradaEn() != null ? consulta.getCerradaEn() : consulta.getCreadaEn());
        item.setCentroId(consulta.getCentroAtencion().getId());
        item.setCentroNombre(consulta.getCentroAtencion().getNombre());
        item.setMedicoId(consulta.getMedico().getId());
        item.setMedicoNombre(nombreMedico(consulta.getMedico()));
        if (consulta.getEspecialidad() != null) {
            item.setEspecialidadId(consulta.getEspecialidad().getId());
            item.setEspecialidadNombre(consulta.getEspecialidad().getNombre());
        }
        item.setEstado(consulta.getEstado().name());
        item.setResumen(consulta.getMotivoConsulta());
        return item;
    }

    private TimelineItemDTO itemEnmienda(Enmienda enmienda) {
        TimelineItemDTO item = new TimelineItemDTO();
        item.setTipo(TipoEventoHistoria.ENMIENDA.name());
        item.setRecursoId(String.valueOf(enmienda.getId()));
        item.setConsultaId(enmienda.getConsulta().getId());
        item.setFecha(enmienda.getCreadaEn());
        item.setCentroId(enmienda.getConsulta().getCentroAtencion().getId());
        item.setCentroNombre(enmienda.getConsulta().getCentroAtencion().getNombre());
        item.setMedicoId(enmienda.getAutor().getId());
        item.setMedicoNombre(nombreMedico(enmienda.getAutor()));
        item.setResumen(enmienda.getMotivo());
        item.setEstado("ENMIENDA");
        return item;
    }

    public ConsultaDTO aConsulta(Consulta consulta) {
        ConsultaDTO dto = new ConsultaDTO();
        dto.setId(consulta.getId());
        Paciente paciente = consulta.getPaciente();
        dto.setPacienteId(paciente.getId());
        dto.setPacienteNombre(paciente.getNombre());
        dto.setPacienteApellido(paciente.getApellido());
        dto.setDni(paciente.getDni());
        dto.setIdentificadorFederado(paciente.getIdentificadorFederado());
        if (consulta.getTurno() != null) {
            dto.setTurnoId(consulta.getTurno().getId());
        }
        dto.setMedicoId(consulta.getMedico().getId());
        dto.setMedicoNombre(nombreMedico(consulta.getMedico()));
        dto.setMatricula(consulta.getMedico().getMatricula());
        dto.setRefepsId(consulta.getMedico().getRefepsId());
        dto.setCentroId(consulta.getCentroAtencion().getId());
        dto.setCentroNombre(consulta.getCentroAtencion().getNombre());
        if (consulta.getEspecialidad() != null) {
            dto.setEspecialidadId(consulta.getEspecialidad().getId());
            dto.setEspecialidadNombre(consulta.getEspecialidad().getNombre());
        }
        dto.setEstado(consulta.getEstado().name());
        dto.setMotivoConsulta(consulta.getMotivoConsulta());
        dto.setAnamnesis(consulta.getAnamnesis());
        dto.setExamenFisico(consulta.getExamenFisico());
        dto.setIndicaciones(consulta.getIndicaciones());
        dto.setPlan(consulta.getPlan());
        dto.setProximoControl(consulta.getProximoControl());
        dto.setTensionSistolica(consulta.getTensionSistolica());
        dto.setTensionDiastolica(consulta.getTensionDiastolica());
        dto.setFrecuenciaCardiaca(consulta.getFrecuenciaCardiaca());
        dto.setTemperatura(consulta.getTemperatura());
        dto.setPesoKg(consulta.getPesoKg());
        dto.setTallaCm(consulta.getTallaCm());
        dto.setImc(consulta.getImc());
        dto.setCreadaEn(consulta.getCreadaEn());
        dto.setActualizadaEn(consulta.getActualizadaEn());
        dto.setCerradaEn(consulta.getCerradaEn());
        dto.setDiagnosticos(diagnosticoRepository.findByConsulta_IdOrderByIdAsc(consulta.getId()).stream()
                .map(this::aDiagnostico).toList());
        dto.setEnmiendas(enmiendaRepository.findByConsulta_IdOrderByCreadaEnAsc(consulta.getId()).stream()
                .map(this::aEnmienda).toList());
        return dto;
    }

    public PerfilClinicoDTO aPerfil(Paciente paciente, PerfilClinico perfil) {
        PerfilClinicoDTO dto = new PerfilClinicoDTO();
        dto.setPacienteId(paciente.getId());
        dto.setDni(paciente.getDni());
        dto.setIdentificadorFederado(paciente.getIdentificadorFederado());
        dto.setNombre(paciente.getNombre());
        dto.setApellido(paciente.getApellido());
        dto.setAntecedentesPersonales(perfil.getAntecedentesPersonales());
        dto.setAntecedentesFamiliares(perfil.getAntecedentesFamiliares());
        dto.setAntecedentesQuirurgicos(perfil.getAntecedentesQuirurgicos());
        dto.setHabitos(perfil.getHabitos());
        dto.setGrupoSanguineo(perfil.getGrupoSanguineo() != null ? perfil.getGrupoSanguineo().name() : null);
        dto.setComparteEntreCentros(perfil.isComparteEntreCentros());
        if (perfil.getId() != null) {
            dto.setAlergias(alergiaRepository.findByPerfil_IdOrderByIdAsc(perfil.getId()).stream().map(this::aAlergia).toList());
            dto.setMedicaciones(medicacionRepository.findByPerfil_IdOrderByIdAsc(perfil.getId()).stream()
                    .map(this::aMedicacion).toList());
            dto.setVacunas(vacunaRepository.findByPerfil_IdOrderByFechaAplicacionDescIdDesc(perfil.getId()).stream()
                    .map(this::aVacuna).toList());
        }
        return dto;
    }

    private EnmiendaDTO aEnmienda(Enmienda enmienda) {
        EnmiendaDTO dto = new EnmiendaDTO();
        dto.setId(enmienda.getId());
        dto.setConsultaId(enmienda.getConsulta().getId());
        dto.setTexto(enmienda.getTexto());
        dto.setMotivo(enmienda.getMotivo());
        dto.setMedicoId(enmienda.getAutor().getId());
        dto.setMedicoNombre(nombreMedico(enmienda.getAutor()));
        dto.setMatricula(enmienda.getAutor().getMatricula());
        dto.setCreadaEn(enmienda.getCreadaEn());
        return dto;
    }

    private DiagnosticoDTO aDiagnostico(DiagnosticoConsulta diagnostico) {
        DiagnosticoDTO dto = new DiagnosticoDTO();
        dto.setId(diagnostico.getId());
        dto.setSystemUri(diagnostico.getSystemUri());
        dto.setCode(diagnostico.getCode());
        dto.setDisplay(diagnostico.getDisplay());
        dto.setTipo(diagnostico.getTipo().name());
        return dto;
    }

    private AlergiaDTO aAlergia(Alergia alergia) {
        AlergiaDTO dto = new AlergiaDTO();
        dto.setId(alergia.getId());
        dto.setSustancia(alergia.getSustancia());
        dto.setReaccion(alergia.getReaccion());
        dto.setCriticidad(alergia.getCriticidad() != null ? alergia.getCriticidad().name() : null);
        dto.setSystemUri(alergia.getSystemUri());
        dto.setCode(alergia.getCode());
        dto.setActiva(alergia.isActiva());
        return dto;
    }

    private MedicacionDTO aMedicacion(MedicacionHabitual medicacion) {
        MedicacionDTO dto = new MedicacionDTO();
        dto.setId(medicacion.getId());
        dto.setFarmaco(medicacion.getFarmaco());
        dto.setDosis(medicacion.getDosis());
        dto.setFrecuencia(medicacion.getFrecuencia());
        dto.setSystemUri(medicacion.getSystemUri());
        dto.setCode(medicacion.getCode());
        dto.setActiva(medicacion.isActiva());
        return dto;
    }

    private VacunaDTO aVacuna(Vacuna vacuna) {
        VacunaDTO dto = new VacunaDTO();
        dto.setId(vacuna.getId());
        dto.setNombre(vacuna.getNombre());
        dto.setFechaAplicacion(vacuna.getFechaAplicacion());
        dto.setDosis(vacuna.getDosis());
        dto.setLote(vacuna.getLote());
        return dto;
    }

    private AuditoriaHceDTO aAuditoria(AccesoHistoriaClinica acceso) {
        AuditoriaHceDTO dto = new AuditoriaHceDTO();
        dto.setId(acceso.getId());
        dto.setActorEmail(acceso.getActorEmail());
        dto.setActorRol(acceso.getActorRol());
        dto.setAccion(acceso.getAccion().name());
        dto.setRecursoTipo(acceso.getRecursoTipo());
        dto.setRecursoId(acceso.getRecursoId());
        dto.setCentroId(acceso.getCentroId());
        dto.setDetalle(acceso.getDetalle());
        dto.setOcurridoEn(acceso.getOcurridoEn());
        return dto;
    }

    private Cie10DTO aCie10(CodigoCie10 codigo) {
        Cie10DTO dto = new Cie10DTO();
        dto.setSystemUri(codigo.getSystemUri());
        dto.setCode(codigo.getCode());
        dto.setDisplay(codigo.getDisplay());
        return dto;
    }

    private PacienteResumenHceDTO aResumen(Paciente paciente) {
        PacienteResumenHceDTO dto = new PacienteResumenHceDTO();
        dto.setId(paciente.getId());
        dto.setNombre(paciente.getNombre());
        dto.setApellido(paciente.getApellido());
        dto.setDni(paciente.getDni());
        return dto;
    }

    private String nombreMedico(Medico medico) {
        return ((medico.getNombre() == null ? "" : medico.getNombre()) + " "
                + (medico.getApellido() == null ? "" : medico.getApellido())).trim();
    }

    private String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private String recorte(String valor) {
        String limpio = limpiar(valor);
        if (limpio == null) {
            return "";
        }
        return limpio.length() <= 120 ? limpio : limpio.substring(0, 120);
    }

    private GrupoSanguineo parseGrupo(String valor) {
        if (valor == null || valor.isBlank()) {
            return GrupoSanguineo.DESCONOCIDO;
        }
        try {
            return GrupoSanguineo.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Grupo sanguíneo inválido");
        }
    }

    private CriticidadAlergia parseCriticidad(String valor) {
        if (valor == null || valor.isBlank()) {
            return CriticidadAlergia.NO_EVALUADA;
        }
        try {
            return CriticidadAlergia.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Criticidad de alergia inválida");
        }
    }

    private TipoDiagnostico parseTipoDiagnostico(String valor) {
        if (valor == null || valor.isBlank()) {
            return TipoDiagnostico.PRESUNTIVO;
        }
        try {
            return TipoDiagnostico.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de diagnóstico inválido");
        }
    }

    private TipoEventoHistoria parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return TipoEventoHistoria.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de evento inválido");
        }
    }
}
