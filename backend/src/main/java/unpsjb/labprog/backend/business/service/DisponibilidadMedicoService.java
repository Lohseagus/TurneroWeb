package unpsjb.labprog.backend.business.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.EspecialidadRepository;
import unpsjb.labprog.backend.dto.DisponibilidadMedicoDTO;
import unpsjb.labprog.backend.model.DisponibilidadMedico;
import unpsjb.labprog.backend.model.Especialidad;
@Service
public class DisponibilidadMedicoService {

    @Autowired
    private CentroAccessGuard centroAccess;

    @Autowired
    private DisponibilidadMedicoRepository repository;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    @Autowired
    private unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository esquemaTurnoRepository;

    /**
     * Obtiene todas las disponibilidades con filtrado automático multi-tenencia.
     * - SUPERADMIN: Ve todas las disponibilidades globalmente
     * - ADMINISTRADOR/OPERADOR/MEDICO: Solo disponibilidades de su centro (via StaffMedico)
     * - PACIENTE: Ve todas las disponibilidades (acceso global)
     */
    public List<DisponibilidadMedicoDTO> findAll() {
        return acotar(repository.findAll()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<DisponibilidadMedicoDTO> findById(Integer id) {
        return repository.findById(id).map(this::toDTO);
    }

    /**
     * Obtiene página de disponibilidades con filtrado automático multi-tenencia.
     * - SUPERADMIN: Ve todas las disponibilidades globalmente
     * - ADMINISTRADOR/OPERADOR/MEDICO: Solo disponibilidades de su centro (via StaffMedico)
     * - PACIENTE: Ve todas las disponibilidades (acceso global)
     */
    public Page<DisponibilidadMedicoDTO> findByPage(int page, int size) {
        return paginar(acotar(repository.findAll()), PageRequest.of(page, size));
    }

    public Page<DisponibilidadMedicoDTO> findByPage(int page, int size, String staffMedico, String especialidad, String dia, String sortBy, String sortDir) {
        // Crear Sort basado en sortBy y sortDir
        Sort sort = Sort.unsorted();
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, sortBy);
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        // Si no hay filtros, igual se recorta al médico o al centro de quien pide.
        if ((staffMedico == null || staffMedico.trim().isEmpty()) &&
            (especialidad == null || especialidad.trim().isEmpty()) &&
            (dia == null || dia.trim().isEmpty())) {
            return paginar(acotar(repository.findAll()), pageable);
        }

        // Usar el método filtrado
        List<DisponibilidadMedico> filteredResults = acotar(repository.findFiltered(
            staffMedico != null && !staffMedico.trim().isEmpty() ? staffMedico.trim() : null,
            especialidad != null && !especialidad.trim().isEmpty() ? especialidad.trim() : null,
            dia != null && !dia.trim().isEmpty() ? dia.trim() : null
        ));

        return paginar(filteredResults, pageable);
    }

    @Transactional
    public DisponibilidadMedicoDTO saveOrUpdate(DisponibilidadMedicoDTO dto) {
        if (dto.getHorarios() == null || dto.getHorarios().isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar al menos un día y horario.");
        }

        for (DisponibilidadMedicoDTO.DiaHorarioDTO horario : dto.getHorarios()) {
            if (horario.getHoraInicio().isAfter(horario.getHoraFin())) {
                throw new IllegalArgumentException("La hora de inicio no puede ser mayor a la hora de fin.");
            }
        }

        DisponibilidadMedico disponibilidadMedico = toEntity(dto);

        if (disponibilidadMedico.getId() == null || disponibilidadMedico.getId() == 0) {
            // Validar duplicados para nueva disponibilidad
            for (DisponibilidadMedico.DiaHorario horario : disponibilidadMedico.getHorarios()) {
                if (repository.existsByStaffMedicoAndHorariosDiaAndHorariosHoraInicioAndHorariosHoraFin(
                        disponibilidadMedico.getStaffMedico(),
                        horario.getDia(),
                        horario.getHoraInicio(),
                        horario.getHoraFin())) {
                    throw new IllegalStateException(
                            "Ya existe una disponibilidad para este staff médico en el mismo día y horario.");
                }
            }
        } else {
            // Validar duplicados para actualización - simplemente verificar que existe
            repository.findById(disponibilidadMedico.getId())
                    .orElseThrow(() -> new IllegalStateException("No existe la disponibilidad que se intenta modificar."));

            for (DisponibilidadMedico.DiaHorario horario : disponibilidadMedico.getHorarios()) {
                if (repository.existsByStaffMedicoAndHorariosDiaAndHorariosHoraInicioAndHorariosHoraFinExcludingId(
                        disponibilidadMedico.getStaffMedico(),
                        horario.getDia(),
                        horario.getHoraInicio(),
                        horario.getHoraFin(),
                        disponibilidadMedico.getId())) {
                    throw new IllegalStateException(
                            "Ya existe una disponibilidad para este staff médico en el mismo día y horario.");
                }
            }
        }

        return toDTO(repository.save(disponibilidadMedico));
    }

    @Transactional
    public void deleteById(Integer id) {
        // Primero eliminar los esquemas de turno asociados a esta disponibilidad
        var esquemasAsociados = esquemaTurnoRepository.findByDisponibilidadMedicoId(id);
        if (!esquemasAsociados.isEmpty()) {
            esquemaTurnoRepository.deleteAll(esquemasAsociados);
        }

        // Luego eliminar la disponibilidad
        repository.deleteById(id);
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    private DisponibilidadMedicoDTO toDTO(DisponibilidadMedico disponibilidad) {
        DisponibilidadMedicoDTO dto = new DisponibilidadMedicoDTO();
        dto.setId(disponibilidad.getId());
        dto.setStaffMedicoId(disponibilidad.getStaffMedico().getId());
        
        // Incluir nombre del staff médico
        if (disponibilidad.getStaffMedico() != null && disponibilidad.getStaffMedico().getMedico() != null) {
            String nombre = disponibilidad.getStaffMedico().getMedico().getNombre();
            String apellido = disponibilidad.getStaffMedico().getMedico().getApellido();
            dto.setStaffMedicoName(nombre + " " + apellido);
        }
        
        // Incluir especialidadId si existe la relación
        if (disponibilidad.getEspecialidad() != null) {
            dto.setEspecialidadId(disponibilidad.getEspecialidad().getId());
            dto.setEspecialidadName(disponibilidad.getEspecialidad().getNombre());
        }
        
        dto.setHorarios(disponibilidad.getHorarios().stream().map(horario -> {
            DisponibilidadMedicoDTO.DiaHorarioDTO horarioDTO = new DisponibilidadMedicoDTO.DiaHorarioDTO();
            horarioDTO.setDia(horario.getDia());
            horarioDTO.setHoraInicio(horario.getHoraInicio());
            horarioDTO.setHoraFin(horario.getHoraFin());
            return horarioDTO;
        }).collect(Collectors.toList()));
        return dto;
    }

    private DisponibilidadMedico toEntity(DisponibilidadMedicoDTO dto) {
        DisponibilidadMedico disponibilidad = new DisponibilidadMedico();
        disponibilidad.setId(dto.getId());
        
        var staffMedico = staffMedicoRepository.findById(dto.getStaffMedicoId())
                .orElseThrow(() -> new IllegalArgumentException("StaffMedico no encontrado con ID: " + dto.getStaffMedicoId()));
        disponibilidad.setStaffMedico(staffMedico);
        
        // Validar y establecer especialidad
        if (dto.getEspecialidadId() != null) {
            Especialidad especialidad = especialidadRepository.findById(dto.getEspecialidadId())
                    .orElseThrow(() -> new IllegalArgumentException("Especialidad no encontrada con ID: " + dto.getEspecialidadId()));
            
            // VALIDACIÓN CRÍTICA: La especialidad de la disponibilidad DEBE coincidir con la del StaffMedico
            if (staffMedico.getEspecialidad() != null && 
                !staffMedico.getEspecialidad().getId().equals(especialidad.getId())) {
                throw new IllegalArgumentException(
                    String.format(
                        "Inconsistencia de datos: La especialidad de la disponibilidad (%s, ID: %d) no coincide con la especialidad del Staff Médico (%s, ID: %d). " +
                        "Para crear una disponibilidad en %s, debe usar el Staff Médico correspondiente a esa especialidad.",
                        especialidad.getNombre(), especialidad.getId(),
                        staffMedico.getEspecialidad().getNombre(), staffMedico.getEspecialidad().getId(),
                        especialidad.getNombre()
                    )
                );
            }
            
            disponibilidad.setEspecialidad(especialidad);
        } else {
            // Si no se proporciona especialidadId, usar automáticamente la del StaffMedico
            if (staffMedico.getEspecialidad() != null) {
                disponibilidad.setEspecialidad(staffMedico.getEspecialidad());
            }
        }
        
        disponibilidad.setHorarios(dto.getHorarios().stream().map(horarioDTO -> {
            DisponibilidadMedico.DiaHorario horario = new DisponibilidadMedico.DiaHorario();
            horario.setDia(horarioDTO.getDia());
            horario.setHoraInicio(horarioDTO.getHoraInicio());
            horario.setHoraFin(horarioDTO.getHoraFin());
            return horario;
        }).collect(Collectors.toList()));
        return disponibilidad;
    }

    /**
     * Un médico ve solo sus horarios. El personal del centro, los de sus médicos.
     * Sin sesión no recorta: lo usa el modo de desarrollo.
     */
    private List<DisponibilidadMedico> acotar(List<DisponibilidadMedico> lista) {
        User user = AuditContext.getCurrentUserObject();
        if (lista == null || user == null || user.getRole() == null || user.getRole() == Role.SUPERADMIN) {
            return lista == null ? List.of() : lista;
        }
        if (user.getRole() == Role.MEDICO) {
            java.util.Set<Integer> propios = centroAccess.staffPropioIds();
            return lista.stream()
                    .filter(disponibilidad -> disponibilidad.getStaffMedico() != null
                            && propios.contains(disponibilidad.getStaffMedico().getId()))
                    .toList();
        }
        return centroAccess.filtrar(lista, disponibilidad -> CentroAccessGuard.centroDe(disponibilidad.getStaffMedico()));
    }

    private Page<DisponibilidadMedicoDTO> paginar(List<DisponibilidadMedico> lista, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = lista.isEmpty() ? 0 : Math.min(start + pageable.getPageSize(), lista.size());
        List<DisponibilidadMedicoDTO> pagina = start >= lista.size()
                ? List.of()
                : lista.subList(start, end).stream().map(this::toDTO).collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(pagina, pageable, lista.size());
    }

    public List<DisponibilidadMedicoDTO> findByStaffMedicoId(Integer staffMedicoId) {
        return repository.findByStaffMedicoId(staffMedicoId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<DisponibilidadMedicoDTO> findByMedicoId(Integer medicoId) {
        return centroAccess.filtrar(repository.findByStaffMedicoMedicoId(medicoId),
                        disponibilidad -> CentroAccessGuard.centroDe(disponibilidad.getStaffMedico())).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}