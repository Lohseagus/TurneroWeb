package unpsjb.labprog.backend.business.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.EspecialidadRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.dto.EspecialidadDTO;
import unpsjb.labprog.backend.dto.MedicoDTO;
import unpsjb.labprog.backend.exception.InputValidationException;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.AuditLog;
import unpsjb.labprog.backend.config.TenantContext;

@Service
public class MedicoService {

    private static final Logger logger = LoggerFactory.getLogger(MedicoService.class);

    @Autowired
    private MedicoRepository repository;

    @Autowired
    private EspecialidadRepository especialidadRepo;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;
    
    @Autowired
    private CentroAtencionRepository centroAtencionRepository;

     @Autowired
    private RegistrationService registrationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserService userService;

    /**
     * Obtiene todos los médicos con filtrado automático multi-tenencia.
     * - SUPERADMIN: Ve todos los médicos globalmente
     * - ADMINISTRADOR/OPERADOR: Solo médicos que trabajan en su centro (via StaffMedico)
     * - PACIENTE: Ve todos los médicos (acceso global)
     */
    @Transactional(readOnly = true)
    public List<MedicoDTO> findAll() {
        Integer centroId = TenantContext.getFilteredCentroId();
        
        if (centroId != null) {
            // Usuario limitado por centro - filtrar por médicos del centro
            return repository.findByCentroAtencionId(centroId).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } else {
            // SUPERADMIN o PACIENTE - acceso global
            return repository.findAll().stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Obtiene información básica de TODOS los médicos del sistema (sin filtrado por centro).
     * Retorna solo datos no sensibles: id, nombre, apellido, matricula, especialidades.
     * Útil para selectores al crear StaffMedico, permitiendo ver médicos disponibles
     * sin exponer información de contacto.
     * 
     * TODO: Implementar flujo de aprobación bidireccional donde el médico
     * debe aceptar la solicitud del centro antes de ser asociado.
     * 
     * @return Lista de mapas con información básica de todos los médicos
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> findAllMedicosBasicInfo() {
        return repository.findAll().stream()
                .map(medico -> {
                    java.util.Map<String, Object> info = new java.util.HashMap<>();
                    info.put("id", medico.getId());
                    info.put("nombre", medico.getNombre());
                    info.put("apellido", medico.getApellido());
                    info.put("matricula", medico.getMatricula());
                    
                    // Incluir especialidades
                    if (medico.getEspecialidades() != null && !medico.getEspecialidades().isEmpty()) {
                        List<java.util.Map<String, Object>> especialidades = medico.getEspecialidades().stream()
                                .map(esp -> {
                                    java.util.Map<String, Object> espInfo = new java.util.HashMap<>();
                                    espInfo.put("id", esp.getId());
                                    espInfo.put("nombre", esp.getNombre());
                                    return espInfo;
                                })
                                .collect(Collectors.toList());
                        info.put("especialidades", especialidades);
                    } else {
                        info.put("especialidades", List.of());
                    }
                    
                    return info;
                })
                .collect(Collectors.toList());
    }

    public Optional<MedicoDTO> findById(Integer id) {
        return repository.findById(id).map(this::toDTO);
    }

    public Optional<MedicoDTO> findByMatricula(String matricula) {
        return repository.findByMatricula(matricula).map(this::toDTO);
    }
    
    public Optional<MedicoDTO> findByEmail(String email) {
        return repository.findByEmail(email).map(this::toDTO);
    }
    
    /**
     * Crea un nuevo médico o reutiliza uno existente (por DNI) y lo vincula al centro del administrador.
     * Solo para uso de ADMINISTRADOR (crea médicos en su propio centro).
     * 
     * Lógica:
     * 1. Valida por DNI si el médico ya existe globalmente
     * 2. Si existe: Reutiliza el registro (no crea cuenta de usuario duplicada)
     * 3. Si NO existe: Crea médico + cuenta User
     * 4. Vincula mediante StaffMedico al centro del administrador
     * 
     * @param dto Datos del médico (debe incluir especialidadId)
     * @param performedByEmail Email del ADMINISTRADOR que realiza la acción
     * @return MedicoDTO del médico creado/vinculado
     * @throws IllegalArgumentException si hay errores de validación
     */
    @Transactional
    public MedicoDTO createMedico(MedicoDTO dto, String performedByEmail) {
        // Obtener el centro del administrador que está creando el médico.
        // SUPERADMIN no tiene centro: el médico queda global, sin StaffMedico.
        User performingAdmin = userService.findByEmail(performedByEmail)
            .orElseThrow(() -> new IllegalArgumentException("Administrador no encontrado: " + performedByEmail));

        if (performingAdmin.getCentroAtencion() == null) {
            return crearMedicoSinCentro(dto, performedByEmail);
        }

        // Validar que venga especialidad
        final Integer especialidadId;
        if (dto.getEspecialidadIds() != null && !dto.getEspecialidadIds().isEmpty()) {
            especialidadId = dto.getEspecialidadIds().iterator().next();
        } else if (dto.getEspecialidades() != null && !dto.getEspecialidades().isEmpty()
                && dto.getEspecialidades().iterator().next().getId() != null) {
            especialidadId = dto.getEspecialidades().iterator().next().getId();
        } else {
            throw new IllegalArgumentException("Debe proporcionar al menos una especialidad válida");
        }
        
        Integer centroId = performingAdmin.getCentroAtencion().getId();
        
        // Buscar especialidad
        Especialidad especialidad = especialidadRepo.findById(especialidadId)
            .orElseThrow(() -> new IllegalArgumentException("Especialidad no encontrada con ID: " + especialidadId));
        
        // Validar DNI
        if (dto.getDni() == null || dto.getDni().isBlank()) {
            throw new IllegalArgumentException("El DNI es obligatorio");
        }
        if (!dto.getDni().matches("\\d+")) {
            throw new IllegalArgumentException("DNI incorrecto, debe contener sólo números");
        }
        
        Long dniLong = Long.parseLong(dto.getDni());
        
        // PASO 1: Validar por DNI si el médico ya existe globalmente
        Optional<Medico> existingMedico = repository.findByDni(dniLong);
        
        Medico medico;
        boolean medicoCreado = false;
        
        if (existingMedico.isPresent()) {
            // REUTILIZAR médico existente
            medico = existingMedico.get();
            logger.info("Médico con DNI {} ya existe. Reutilizando registro.", dniLong);
            
            // Verificar que no esté ya vinculado a este centro
            if (staffMedicoRepository.existsByMedicoIdAndCentroId(medico.getId(), centroId)) {
                throw new IllegalArgumentException("Este médico ya está asignado a su centro");
            }
        } else {
            // CREAR nuevo médico
            // Validar matrícula y email únicos
            if (repository.existsByMatricula(dto.getMatricula())) {
                throw new IllegalArgumentException("La matrícula ya existe en el sistema");
            }
            if (repository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("Ya existe un médico con el email: " + dto.getEmail());
            }
            
            medico = toEntity(dto);
            medico.setDni(dniLong);
            validarMedico(medico);
            
            // Guardar médico
            medico = repository.save(medico);
            medicoCreado = true;
            
            // Crear cuenta User para que pueda acceder al sistema
            String temporaryPassword = java.util.UUID.randomUUID().toString().substring(0, 12);
            registrationService.registrarMedicoWithAudit(
                dto.getEmail(),
                temporaryPassword,
                dniLong,
                dto.getNombre(),
                dto.getApellido(),
                dto.getTelefono(),
                performedByEmail
            );
            
            // Enviar email con credenciales
            User medicoUser = userService.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalStateException("Error al crear usuario para médico"));
            emailService.sendMedicoWelcomeEmail(medicoUser, temporaryPassword);
            
            logger.info("Médico con DNI {} creado exitosamente.", dniLong);
        }
        
        // PASO 2: SIEMPRE crear StaffMedico vinculando al centro
        CentroAtencion centro = centroAtencionRepository.findById(centroId)
            .orElseThrow(() -> new IllegalStateException("Centro no encontrado"));
        
        StaffMedico staff = new StaffMedico();
        staff.setMedico(medico);
        staff.setCentroAtencion(centro);
        staff.setEspecialidad(especialidad);
        staffMedicoRepository.save(staff);
        
        // Auditar la vinculación
        String accion = medicoCreado ? "Médico creado y vinculado" : "Médico existente vinculado";
        auditLogService.logGenericAction(
            AuditLog.EntityTypes.STAFF_MEDICO,
            staff.getId().longValue(),
            AuditLog.Actions.CREATE,
            performedByEmail,
            null,
            String.format("%s - %s (%s)", medico.getNombre(), medico.getApellido(), especialidad.getNombre()),
            null,
            null,
            accion + " al centro " + centro.getNombre()
        );
        
        logger.info("StaffMedico creado: Médico {} vinculado al centro {} con especialidad {}",
            medico.getId(), centroId, especialidadId);
        
        return toDTO(medico);
    }

    /**
     * Alta global usada por SUPERADMIN. No exige centro ni especialidad:
     * el médico existe en el sistema y cada centro lo asocia después.
     */
    private MedicoDTO crearMedicoSinCentro(MedicoDTO dto, String performedByEmail) {
        if (dto.getDni() == null || dto.getDni().isBlank()) {
            throw new InputValidationException("El dni es obligatorio");
        }
        if (!dto.getDni().matches("\\d+")) {
            throw new InputValidationException("dni incorrecto, débe contener sólo números");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        if (dto.getTelefono() == null || dto.getTelefono().isBlank()) {
            String base = "549" + dto.getDni().replaceAll("\\D", "");
            dto.setTelefono(base.length() > 15 ? base.substring(0, 15) : base);
        }

        Long dniLong = Long.parseLong(dto.getDni());
        if (repository.existsByDni(dniLong)) {
            throw new IllegalArgumentException("El dni ya existe en el sistema");
        }
        if (dto.getMatricula() != null && repository.existsByMatricula(dto.getMatricula())) {
            throw new IllegalArgumentException("La matrícula ya existe en el sistema");
        }
        if (repository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un médico con el email: " + dto.getEmail());
        }

        Medico medico = toEntity(dto);
        medico.setDni(dniLong);
        validarMedico(medico);
        if (tieneEspecialidades(dto)) {
            medico.setEspecialidades(resolverEspecialidades(dto));
        }

        medico = repository.save(medico);

        String temporaryPassword = java.util.UUID.randomUUID().toString().substring(0, 12);
        registrationService.registrarMedicoWithAudit(
            dto.getEmail(),
            temporaryPassword,
            dniLong,
            dto.getNombre(),
            dto.getApellido(),
            dto.getTelefono(),
            performedByEmail
        );
        userService.findByEmail(dto.getEmail()).ifPresent(medicoUser -> {
            try {
                emailService.sendMedicoWelcomeEmail(medicoUser, temporaryPassword);
            } catch (Exception e) {
                logger.warn("No se pudo enviar el email de bienvenida a {}: {}", dto.getEmail(), e.getMessage());
            }
        });

        return toDTO(medico);
    }

    @Transactional
    public MedicoDTO saveOrUpdate(MedicoDTO dto, String performedBy) {
        // Validar DNI en el DTO antes de convertir a entidad
        if (dto.getDni() == null || dto.getDni().isBlank()) {
            throw new InputValidationException("El dni es obligatorio");
        }
        if (!dto.getDni().matches("\\d+")) {
            throw new InputValidationException("dni incorrecto, débe contener sólo números");
        }
        if (dto.getDni().length() < 7 || dto.getDni().length() > 9) {
            throw new IllegalArgumentException("El dni debe tener entre 7 y 9 dígitos");
        }

        // Para testing: si no hay usuario autenticado, usar valor por defecto
        if (performedBy == null) {
            performedBy = "SYSTEM_TEST";
        }

        Medico medico = toEntity(dto);
        validarMedico(medico);

        if (medico.getId() == null || medico.getId() == 0) {
            // CREACIÓN
            if (repository.existsByDni(medico.getDni())) {
                throw new IllegalArgumentException("El dni ya existe en el sistema");
            }
            if (repository.existsByMatricula(medico.getMatricula())) {
                throw new IllegalArgumentException("La Matrícula ya existe en el sistema");
            }
            if (repository.existsByEmail(medico.getEmail())) {
                throw new IllegalStateException("Ya existe un médico con el email: " + medico.getEmail());
            }

            Set<Especialidad> especialidades = resolverEspecialidades(dto);

            // Si es creado por ADMIN u OPERADOR (tiene performedBy), usar auditoría
            if (dto.getPerformedBy() != null && !dto.getPerformedBy().trim().isEmpty()) {
                // Generar contraseña automática
                String password = dto.getPassword();
                if (password == null || password.trim().isEmpty()) {
                    password = generarPasswordAutomatica();
                }

                // 1. Crear usuario en la tabla User con auditoría
                registrationService.registrarMedicoWithAudit(
                    medico.getEmail(),
                    password,
                    medico.getDni(),
                    medico.getNombre(),
                    medico.getApellido(),
                    medico.getTelefono(),
                    dto.getPerformedBy()
                );

                // 2. Crear entidad médico
                medico.setEspecialidades(especialidades);
                
                Medico medicoCreado = repository.save(medico);
                vincularAlCentroDeQuienCrea(medicoCreado, especialidades, dto.getPerformedBy());

                // 3. Enviar contraseña por mail
                enviarPasswordPorMail(medico.getEmail(), password);
                
                // Retornar el médico creado
                return toDTO(medicoCreado);
            } else {
                medico.setEspecialidades(especialidades);
            }
        } else {
            // MODIFICACIÓN
            Medico existente = repository.findById(medico.getId())
                    .orElseThrow(() -> new IllegalStateException("No existe el médico que se intenta modificar."));
            
            if (!existente.getDni().equals(medico.getDni()) && repository.existsByDni(medico.getDni())) {
                throw new IllegalArgumentException("El dni ya existe en el sistema");
            }
            if (!existente.getMatricula().equals(medico.getMatricula()) && repository.existsByMatricula(medico.getMatricula())) {
                throw new IllegalArgumentException("La Matrícula ya existe en el sistema");
            }
            if (!existente.getEmail().equals(medico.getEmail()) && repository.existsByEmail(medico.getEmail())) {
                throw new IllegalStateException("Ya existe un médico con el email: " + medico.getEmail());
            }

            // No manejamos contraseña en la entidad médico, solo en User

            // Actualizar también el User correspondiente si existe
            Optional<User> userOpt = userService.findByEmail(existente.getEmail());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                // Actualizar los datos personales del usuario
                user.setNombre(medico.getNombre());
                user.setApellido(medico.getApellido());
                user.setEmail(medico.getEmail());
                user.setTelefono(medico.getTelefono());
                user.setDni(medico.getDni());
                
                // Guardar el usuario actualizado
                userService.save(user);
            }

            // Actualizar campos editables
            existente.setNombre(medico.getNombre());
            existente.setApellido(medico.getApellido());
            existente.setDni(medico.getDni());
            existente.setEmail(medico.getEmail());
            existente.setTelefono(medico.getTelefono());
            existente.setEspecialidades(medico.getEspecialidades());
            existente.setMatricula(medico.getMatricula());
            if (dto.getRefepsId() != null) {
                existente.setRefepsId(medico.getRefepsId());
            }
            // No manejamos contraseña en entidad médico
            medico = existente;
        }

        Medico saved = repository.save(medico);

        // 🎯 AUDITORÍA
        if (medico.getId() == null || medico.getId() == 0) {
            auditLogService.logMedicoCreated(saved.getId().longValue(), 
                saved.getNombre(), saved.getApellido(), performedBy);
        } else {
            auditLogService.logGenericAction(AuditLog.EntityTypes.MEDICO, saved.getId().longValue(),
                                           AuditLog.Actions.UPDATE, performedBy, null, null,
                                           null, saved, "Médico actualizado");
        }

        return toDTO(saved);
    }

    /**
     * Genera una contraseña automática segura para el médico
     */
    private String generarPasswordAutomatica() {
        return java.util.UUID.randomUUID().toString().substring(0, 10);
    }

    /**
     * Envía la contraseña inicial por correo electrónico al médico
     */
    private void enviarPasswordPorMail(String email, String password) {
        try {
            // Obtener el nombre del médico desde el email o usar un nombre genérico
            String userName = email.split("@")[0];
            emailService.sendInitialCredentialsEmail(email, userName, password);
            logger.info("Credenciales iniciales enviadas por correo a médico: {}", email);
        } catch (Exception e) {
            logger.error("Error al enviar credenciales iniciales por correo a médico {}: {}", email, e.getMessage());
            // No lanzamos excepción para no interrumpir el flujo de creación del médico
        }
    }

    /**
     * Obtiene página de médicos con filtrado automático multi-tenencia.
     * - SUPERADMIN: Ve todos los médicos globalmente
     * - ADMINISTRADOR/OPERADOR: Solo médicos que trabajan en su centro (via StaffMedico)
     * - PACIENTE: Ve todos los médicos (acceso global)
     */
    public Page<MedicoDTO> findByPage(int page, int size) {
        Integer centroId = TenantContext.getFilteredCentroId();
        
        Pageable pageable = PageRequest.of(page, size);
        
        if (centroId != null) {
            // Usuario limitado por centro - filtrar por médicos del centro
            return repository.findByCentroAtencionId(centroId, pageable)
                    .map(this::toDTO);
        } else {
            // SUPERADMIN o PACIENTE - acceso global
            return repository.findAll(pageable)
                    .map(this::toDTO);
        }
    }

    /**
     * Método de búsqueda paginada con filtros y ordenamiento dinámico para médicos.
     * Aplica filtrado automático multi-tenencia:
     * - SUPERADMIN: Ve todos los médicos globalmente
     * - ADMINISTRADOR/OPERADOR: Solo médicos que trabajan en su centro (via StaffMedico)
     * - PACIENTE: Ve todos los médicos (acceso global)
     *
     * @param page Número de página (0-based)
     * @param size Tamaño de página
     * @param nombre Filtro por nombre/apellido (búsqueda parcial, opcional)
     * @param especialidad Filtro por especialidad (búsqueda parcial, opcional)
     * @param estado Filtro por estado (activo/inactivo, opcional)
     * @param sortBy Campo por el cual ordenar (opcional, default: nombre)
     * @param sortDir Dirección del ordenamiento (asc/desc, default: asc)
     * @return Página de médicos filtrados y ordenados
     */
    public Page<MedicoDTO> findByPage(int page, int size, String nombre, String especialidad, String estado, String sortBy, String sortDir) {
        // Validar y configurar ordenamiento por defecto
        if (sortBy == null || sortBy.trim().isEmpty()) {
            sortBy = "nombre";
        }

        // Validar dirección de ordenamiento
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        // Configurar ordenamiento
        Sort sort = Sort.by(direction, sortBy);

        // Crear Pageable con paginación y ordenamiento
        Pageable pageable = PageRequest.of(page, size, sort);

        // Aplicar filtro multi-tenencia
        Integer centroId = TenantContext.getFilteredCentroId();
        
        Page<Medico> result;
        if (centroId != null) {
            // Usuario limitado por centro - obtener médicos del centro y aplicar filtros manualmente
            Page<Medico> medicosCentro = repository.findByCentroAtencionId(centroId, pageable);
            
            // Aplicar filtros sobre los médicos del centro
            List<Medico> medicosFiltrados = medicosCentro.getContent().stream()
                .filter(m -> {
                    // Filtro por nombre
                    if (nombre != null && !nombre.trim().isEmpty()) {
                        String nombreLower = nombre.toLowerCase();
                        String nombreCompleto = (m.getNombre() + " " + m.getApellido()).toLowerCase();
                        if (!nombreCompleto.contains(nombreLower)) {
                            return false;
                        }
                    }
                    
                    // Filtro por especialidad
                    if (especialidad != null && !especialidad.trim().isEmpty()) {
                        String especialidadLower = especialidad.toLowerCase();
                        boolean tieneEspecialidad = m.getEspecialidades() != null && 
                            m.getEspecialidades().stream()
                                .anyMatch(e -> e.getNombre().toLowerCase().contains(especialidadLower));
                        if (!tieneEspecialidad) {
                            return false;
                        }
                    }
                    
                    // Filtro por estado (activo/inactivo no aplica a médicos por ahora)
                    // TODO: Implementar estado si es necesario
                    
                    return true;
                })
                .collect(Collectors.toList());
            
            result = new org.springframework.data.domain.PageImpl<>(
                medicosFiltrados, 
                pageable, 
                medicosCentro.getTotalElements()
            );
        } else {
            // SUPERADMIN o PACIENTE - acceso global con filtros
            result = repository.findByFiltros(nombre, especialidad, estado, pageable);
        }

        // Convertir a DTOs
        return result.map(this::toDTO);
    }

    @Transactional
    public void delete(Integer id, String performedBy) {
        Medico medico = repository.findById(id).orElse(null);
        if (medico == null) {
            throw new IllegalStateException("No existe un médico con el ID: " + id);
        }

        // Para testing: si no hay usuario autenticado, usar valor por defecto
        if (performedBy == null) {
            performedBy = "SYSTEM_TEST";
        }

        // 🎯 AUDITORÍA
        auditLogService.logGenericAction(AuditLog.EntityTypes.MEDICO, id.longValue(),
                                       AuditLog.Actions.DELETE, performedBy, "ACTIVO", "ELIMINADO",
                                       medico, null, "Médico eliminado");

        repository.deleteById(id);
    }

    private MedicoDTO toDTO(Medico medico) {
        MedicoDTO dto = new MedicoDTO();
        dto.setId(medico.getId());
        dto.setNombre(medico.getNombre());
        dto.setApellido(medico.getApellido());
        dto.setDni(medico.getDni() != null ? String.valueOf(medico.getDni()) : null);
        dto.setEmail(medico.getEmail());
        dto.setTelefono(medico.getTelefono());
        dto.setMatricula(medico.getMatricula());
        dto.setRefepsId(medico.getRefepsId());

        // Mapear Especialidades (múltiples)
        if (medico.getEspecialidades() != null && !medico.getEspecialidades().isEmpty()) {
            Set<EspecialidadDTO> especialidadesDTO = medico.getEspecialidades().stream()
                    .map(esp -> {
                        EspecialidadDTO espDTO = new EspecialidadDTO();
                        espDTO.setId(esp.getId());
                        espDTO.setNombre(esp.getNombre());
                        espDTO.setDescripcion(esp.getDescripcion());
                        return espDTO;
                    })
                    .collect(Collectors.toSet());
            dto.setEspecialidades(especialidadesDTO);
        }

        return dto;
    }

    private Medico toEntity(MedicoDTO dto) {
        Medico medico = new Medico();
        // Solo establecer el ID si es diferente de null y de 0
        // Para creaciones, el ID debe ser null para que JPA lo genere
        if (dto.getId() != null && dto.getId() != 0) {
            medico.setId(dto.getId());
        }
        medico.setNombre(dto.getNombre());
        medico.setApellido(dto.getApellido());
        if (dto.getDni() != null && dto.getDni().matches("\\d+")) {
            medico.setDni(Long.valueOf(dto.getDni()));
        } else {
            medico.setDni(null);
        }
        medico.setEmail(dto.getEmail());
        medico.setTelefono(dto.getTelefono());
        medico.setMatricula(dto.getMatricula());
        if (dto.getRefepsId() != null) {
            String refeps = dto.getRefepsId().trim();
            medico.setRefepsId(refeps.isEmpty() ? null : refeps);
        }

        // Asignar Especialidades (múltiples)
        if (dto.getEspecialidades() != null && !dto.getEspecialidades().isEmpty()) {
            Set<Especialidad> especialidades = dto.getEspecialidades().stream()
                    .map(espDto -> {
                        Especialidad esp = new Especialidad();
                        esp.setId(espDto.getId());
                        esp.setNombre(espDto.getNombre());
                        esp.setDescripcion(espDto.getDescripcion());
                        return esp;
                    })
                    .collect(Collectors.toSet());
            medico.setEspecialidades(especialidades);
        }

        return medico;
    }

    private void validarMedico(Medico medico) {
        if (medico.getNombre() == null || medico.getNombre().isBlank()) {
            throw new InputValidationException("El nombre es obligatorio");
        }
        if (medico.getNombre().length() > 50) {
            throw new InputValidationException("El nombre no puede superar los 50 caracteres");
        }
        if (medico.getApellido() == null || medico.getApellido().isBlank()) {
            throw new InputValidationException("El apellido es obligatorio");
        }
        if (medico.getApellido().length() > 50) {
            throw new InputValidationException("El apellido no puede superar los 50 caracteres");
        }
        if (medico.getMatricula() == null || medico.getMatricula().trim().isEmpty()) {
            throw new IllegalArgumentException("La matrícula es obligatoria");
        }
        if (medico.getMatricula().length() > 20) {
            throw new IllegalArgumentException("La matrícula no puede superar los 20 caracteres");
        }
    }

    private boolean tieneEspecialidades(MedicoDTO dto) {
        boolean porId = dto.getEspecialidadIds() != null && dto.getEspecialidadIds().stream().anyMatch(id -> id != null);
        boolean porNombre = dto.getEspecialidades() != null && dto.getEspecialidades().stream()
            .anyMatch(esp -> esp != null && ((esp.getId() != null) || (esp.getNombre() != null && !esp.getNombre().isBlank())));
        return porId || porNombre;
    }

    /**
     * Resuelve especialidades por id o, si el id no viene, por nombre.
     * Una especialidad inexistente es un error de entrada (400), no un 500.
     */
    private Set<Especialidad> resolverEspecialidades(MedicoDTO dto) {
        Set<Especialidad> resueltas = new HashSet<>();
        if (dto.getEspecialidadIds() != null) {
            for (Integer id : dto.getEspecialidadIds()) {
                if (id == null) {
                    continue;
                }
                resueltas.add(especialidadRepo.findById(id)
                    .orElseThrow(() -> new InputValidationException("La especialidad con ID " + id + " NO existe")));
            }
        }
        if (dto.getEspecialidades() != null) {
            for (EspecialidadDTO esp : dto.getEspecialidades()) {
                if (esp == null) {
                    continue;
                }
                if (esp.getId() != null) {
                    resueltas.add(especialidadRepo.findById(esp.getId())
                        .orElseThrow(() -> new InputValidationException("La especialidad con ID " + esp.getId() + " NO existe")));
                } else if (esp.getNombre() != null && !esp.getNombre().isBlank()) {
                    Especialidad encontrada = especialidadRepo.findByNombreIgnoreCase(esp.getNombre());
                    if (encontrada == null) {
                        throw new InputValidationException("La especialidad " + esp.getNombre() + " NO existe");
                    }
                    resueltas.add(encontrada);
                }
            }
        }
        if (resueltas.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar al menos una especialidad válida");
        }
        return resueltas;
    }

    /**
     * Si quien crea el médico pertenece a un centro, deja la asociación StaffMedico.
     * SUPERADMIN no tiene centro y no genera asociación.
     */
    private void vincularAlCentroDeQuienCrea(Medico medico, Set<Especialidad> especialidades, String performedBy) {
        if (performedBy == null || performedBy.isBlank() || especialidades == null || especialidades.isEmpty()) {
            return;
        }
        Optional<User> operador = userService.findByEmail(performedBy);
        if (operador.isEmpty() || operador.get().getCentroAtencion() == null) {
            return;
        }
        CentroAtencion centro = operador.get().getCentroAtencion();
        for (Especialidad especialidad : especialidades) {
            if (staffMedicoRepository.existsByMedicoAndCentroAtencionAndEspecialidad(medico, centro, especialidad)) {
                continue;
            }
            StaffMedico staff = new StaffMedico();
            staff.setMedico(medico);
            staff.setCentroAtencion(centro);
            staff.setEspecialidad(especialidad);
            staffMedicoRepository.save(staff);
        }
    }
}
