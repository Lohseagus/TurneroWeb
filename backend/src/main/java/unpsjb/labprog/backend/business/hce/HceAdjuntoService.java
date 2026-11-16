package unpsjb.labprog.backend.business.hce;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import unpsjb.labprog.backend.business.repository.AdjuntoClinicoRepository;
import unpsjb.labprog.backend.dto.hce.AdjuntoDTO;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;
import unpsjb.labprog.backend.model.hce.AdjuntoClinico;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.TipoAdjunto;

@Service
public class HceAdjuntoService {

    private final HceAccessGuard guard;
    private final HceStorageService storage;
    private final HistoriaClinicaService historia;
    private final AdjuntoClinicoRepository repository;

    public HceAdjuntoService(HceAccessGuard guard, HceStorageService storage, HistoriaClinicaService historia,
            AdjuntoClinicoRepository repository) {
        this.guard = guard;
        this.storage = storage;
        this.historia = historia;
        this.repository = repository;
    }

    @Transactional
    public AdjuntoDTO subirEnConsulta(Long consultaId, MultipartFile archivo, String tipo) {
        Consulta consulta = historia.cargar(consultaId);
        exigirMedicoQueLee(consulta);
        return persistir(consulta.getPaciente(), consulta, archivo, tipo);
    }

    @Transactional
    public AdjuntoDTO subirEnPaciente(Integer pacienteId, MultipartFile archivo, String tipo) {
        Paciente paciente = historia.cargarPaciente(pacienteId);
        guard.assertEscribePerfil(paciente);
        return persistir(paciente, null, archivo, tipo);
    }

    @Transactional(readOnly = true)
    public List<AdjuntoDTO> listar(Integer pacienteId) {
        Paciente paciente = historia.cargarPaciente(pacienteId);
        guard.assertLeePerfil(paciente);
        return repository.findByPaciente_IdOrderByCreadoEnDesc(pacienteId).stream()
                .filter(this::visible)
                .map(this::aDto)
                .toList();
    }

    @Transactional
    public ArchivoDescarga descargar(Long id) {
        AdjuntoClinico adjunto = repository.findById(id)
                .orElseThrow(() -> new HceNotFoundException("Archivo no encontrado"));
        if (adjunto.getConsulta() != null) {
            guard.assertLeeConsulta(adjunto.getConsulta());
        } else {
            guard.assertLeePerfil(adjunto.getPaciente());
        }
        guard.auditar(adjunto.getPaciente(), AccionAuditoriaHce.DESCARGAR_ADJUNTO, "ADJUNTO", String.valueOf(id),
                adjunto.getCentroAtencion() != null ? adjunto.getCentroAtencion().getId() : null,
                "Descarga de " + adjunto.getNombreOriginal());
        Path path = storage.resolver(adjunto.getStorageKey());
        return new ArchivoDescarga(path, adjunto.getContentType(), adjunto.getNombreOriginal());
    }

    private AdjuntoDTO persistir(Paciente paciente, Consulta consulta, MultipartFile archivo, String tipo) {
        String key = storage.guardar(paciente.getId(), archivo);
        AdjuntoClinico adjunto = new AdjuntoClinico();
        adjunto.setPaciente(paciente);
        adjunto.setConsulta(consulta);
        adjunto.setCentroAtencion(consulta != null ? consulta.getCentroAtencion() : null);
        adjunto.setNombreOriginal(archivo.getOriginalFilename() == null ? "archivo" : archivo.getOriginalFilename());
        adjunto.setContentType(archivo.getContentType());
        adjunto.setTamanioBytes(archivo.getSize());
        adjunto.setStorageKey(key);
        adjunto.setTipo(parseTipo(tipo));
        adjunto.setSubidoPor(guard.usuarioActual().getEmail());
        adjunto.setCreadoEn(LocalDateTime.now());
        adjunto = repository.save(adjunto);
        guard.auditar(paciente, AccionAuditoriaHce.SUBIR_ADJUNTO, "ADJUNTO", String.valueOf(adjunto.getId()),
                adjunto.getCentroAtencion() != null ? adjunto.getCentroAtencion().getId() : null,
                "Adjunto " + adjunto.getTipo().name());
        return aDto(adjunto);
    }

    private void exigirMedicoQueLee(Consulta consulta) {
        if (guard.usuarioActual().getRole() != Role.MEDICO) {
            throw new HceAccessDeniedException("Solo un médico puede adjuntar estudios");
        }
        guard.assertLeeConsulta(consulta);
    }

    private boolean visible(AdjuntoClinico adjunto) {
        if (adjunto.getConsulta() == null) {
            return true;
        }
        return guard.puedeLeerConsulta(adjunto.getConsulta());
    }

    private AdjuntoDTO aDto(AdjuntoClinico adjunto) {
        AdjuntoDTO dto = new AdjuntoDTO();
        dto.setId(adjunto.getId());
        dto.setPacienteId(adjunto.getPaciente().getId());
        dto.setConsultaId(adjunto.getConsulta() != null ? adjunto.getConsulta().getId() : null);
        dto.setCentroId(adjunto.getCentroAtencion() != null ? adjunto.getCentroAtencion().getId() : null);
        dto.setNombreOriginal(adjunto.getNombreOriginal());
        dto.setContentType(adjunto.getContentType());
        dto.setTamanioBytes(adjunto.getTamanioBytes());
        dto.setTipo(adjunto.getTipo().name());
        dto.setSubidoPor(adjunto.getSubidoPor());
        dto.setCreadoEn(adjunto.getCreadoEn());
        return dto;
    }

    private TipoAdjunto parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return TipoAdjunto.DOCUMENTO;
        }
        try {
            return TipoAdjunto.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de adjunto inválido");
        }
    }

    public record ArchivoDescarga(Path path, String contentType, String nombre) {
    }
}
