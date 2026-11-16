package unpsjb.labprog.backend.business.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.dto.EsquemaTurnoDTO;
import unpsjb.labprog.backend.model.DisponibilidadMedico;
import unpsjb.labprog.backend.model.EsquemaTurno;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.config.TenantContext;
import unpsjb.labprog.backend.saas.RecursoSaas;
import unpsjb.labprog.backend.saas.SuscripcionAccessService;

@Service
public class EsquemaTurnoService {

    @Autowired
    private EsquemaTurnoRepository esquemaTurnoRepository;

    @Autowired
    private DisponibilidadMedicoRepository disponibilidadMedicoRepository;

    @Autowired
    private CentroAtencionRepository centroAtencionRepository;

    @Autowired
    private ConsultorioRepository consultorioRepository;

    @Autowired
    private SuscripcionAccessService suscripcionAccessService;
    
    @Autowired
    private ConsultorioDistribucionService consultorioDistribucionService;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;

    private final ThreadLocal<String> advertenciaSolapamiento = new ThreadLocal<>();

    /**
     * Aviso de solapamiento calculado en el último save de este hilo.
     * Lo consume el presenter para armar el mensaje sin rechazar el alta.
     */
    public String consumirAdvertenciaSolapamiento() {
        String aviso = advertenciaSolapamiento.get();
        advertenciaSolapamiento.remove();
        return aviso;
    }

    private void agregarAdvertencia(String aviso) {
        if (aviso == null || aviso.isBlank()) {
            return;
        }
        String previo = advertenciaSolapamiento.get();
        advertenciaSolapamiento.set(previo == null || previo.isBlank() ? aviso : previo + "; " + aviso);
    }

    /**
     * Obtiene todos los esquemas de turno con filtrado automático multi-tenencia.
     * - SUPERADMIN: Ve todos los esquemas globalmente
     * - ADMINISTRADOR/OPERADOR/MEDICO: Solo esquemas de su centro
     * - PACIENTE: Ve todos los esquemas (acceso global)
     */
    public List<EsquemaTurnoDTO> findAll() {
        Integer centroId = TenantContext.getFilteredCentroId();
        
        if (centroId != null) {
            // Usuario limitado por centro - filtrar por esquemas del centro
            return esquemaTurnoRepository.findByCentroAtencionId(centroId).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } else {
            // SUPERADMIN o PACIENTE - acceso global
            return esquemaTurnoRepository.findAll().stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
    }

    public Optional<EsquemaTurnoDTO> findById(Integer id) {
        return esquemaTurnoRepository.findById(id).map(this::toDTO);
    }

    public List<EsquemaTurnoDTO> findByStaffMedico(Integer staffMedicoId) {
        return esquemaTurnoRepository.findByStaffMedicoId(staffMedicoId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteById(Integer id) {
        esquemaTurnoRepository.deleteById(id);
    }

    public List<EsquemaTurnoDTO> findByCentroAtencion(Integer centroId) {
        return esquemaTurnoRepository.findByCentroAtencionId(centroId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EsquemaTurnoDTO> findDisponiblesByCentroAtencion(Integer centroId) {
        // Por ahora retornamos todos los esquemas del centro
        // Se puede agregar lógica adicional para filtrar por disponibilidad
        return findByCentroAtencion(centroId);
    }

    public List<EsquemaTurnoDTO> search(String term) {
        // Implementar búsqueda simple por nombre de médico o centro
        return esquemaTurnoRepository.findAll().stream()
                .filter(esquema -> {
                    String medicoNombre = esquema.getStaffMedico().getMedico().getNombre() + " " +
                                        esquema.getStaffMedico().getMedico().getApellido();
                    String centroNombre = esquema.getCentroAtencion().getNombre();
                    String consultorioNombre = esquema.getConsultorio() != null ? 
                                               esquema.getConsultorio().getNombre() : "";
                    
                    return medicoNombre.toLowerCase().contains(term.toLowerCase()) ||
                           centroNombre.toLowerCase().contains(term.toLowerCase()) ||
                           consultorioNombre.toLowerCase().contains(term.toLowerCase());
                })
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene página de esquemas con filtrado automático multi-tenencia.
     * - SUPERADMIN: Ve todos los esquemas globalmente
     * - ADMINISTRADOR/OPERADOR/MEDICO: Solo esquemas de su centro
     * - PACIENTE: Ve todos los esquemas (acceso global)
     */
    public Page<EsquemaTurnoDTO> findByPage(int page, int size) {
        Integer centroId = TenantContext.getFilteredCentroId();
        
        PageRequest pageRequest = PageRequest.of(page, size);
        
        if (centroId != null) {
            // Usuario limitado por centro - filtrar por esquemas del centro
            // Nota: EsquemaTurnoRepository tiene findByCentroAtencionId pero retorna List, no Page
            List<EsquemaTurno> esquemas = esquemaTurnoRepository.findByCentroAtencionId(centroId);
            // Convertir a Page manualmente
            int start = (int) pageRequest.getOffset();
            int end = Math.min((start + pageRequest.getPageSize()), esquemas.size());
            return new org.springframework.data.domain.PageImpl<>(
                esquemas.subList(start, end).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList()),
                pageRequest,
                esquemas.size()
            );
        } else {
            // SUPERADMIN o PACIENTE - acceso global
            Page<EsquemaTurno> esquemaPage = esquemaTurnoRepository.findAll(pageRequest);
            return esquemaPage.map(this::toDTO);
        }
    }

    /**
     * Búsqueda paginada avanzada con filtros combinados y ordenamiento dinámico
     * @param page Número de página (0-indexed)
     * @param size Cantidad de elementos por página
     * @param staffMedico Filtro por nombre del staff médico (opcional)
     * @param consultorio Filtro por nombre del consultorio (opcional)
     * @param centro Filtro por nombre del centro de atención (opcional)
     * @param sortBy Campo por el cual ordenar (por defecto: id)
     * @param sortDir Dirección del ordenamiento: asc|desc (por defecto: asc)
     * @return Página de esquemas de turno filtrados y ordenados como DTOs
     */
    public Page<EsquemaTurnoDTO> findByPage(int page, int size, String staffMedico, String consultorio, 
                                           String centro, String sortBy, String sortDir) {
        // Configurar ordenamiento
        String field = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "id";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? 
                                   Sort.Direction.DESC : Sort.Direction.ASC;
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, field));
        
        // Normalizar filtros
        String staffMedicoFilter = (staffMedico != null && !staffMedico.trim().isEmpty()) ? staffMedico.trim() : null;
        String consultorioFilter = (consultorio != null && !consultorio.trim().isEmpty()) ? consultorio.trim() : null;
        String centroFilter = (centro != null && !centro.trim().isEmpty()) ? centro.trim() : null;
        
        // Aplicar filtro multi-tenencia automáticamente
        Integer centroId = TenantContext.getFilteredCentroId();
        
        // Si el usuario tiene centro asignado (ADMIN/OPERADOR), buscar y filtrar por nombre del centro
        if (centroId != null) {
            // Buscar todos los esquemas y filtrar manualmente por centro
            Page<EsquemaTurno> result = esquemaTurnoRepository.findByFiltros(staffMedicoFilter, consultorioFilter, centroFilter, pageRequest);
            
            // Filtrar por centro del usuario
            List<EsquemaTurno> esquemasDelCentro = result.getContent().stream()
                .filter(esquema -> esquema.getConsultorio() != null && 
                                  esquema.getConsultorio().getCentroAtencion() != null &&
                                  esquema.getConsultorio().getCentroAtencion().getId().equals(centroId))
                .collect(java.util.stream.Collectors.toList());
            
            return new org.springframework.data.domain.PageImpl<>(
                esquemasDelCentro.stream().map(this::toDTO).collect(java.util.stream.Collectors.toList()),
                pageRequest,
                esquemasDelCentro.size()
            );
        }
        
        // SUPERADMIN: acceso global sin filtro de centro
        return esquemaTurnoRepository.findByFiltros(staffMedicoFilter, consultorioFilter, centroFilter, pageRequest)
                .map(this::toDTO);
    }

    /**
     * Valida conflictos de un esquema sin guardarlo
     * Útil para validación en tiempo real en el frontend
     */
    public Map<String, Object> validarConflictos(EsquemaTurnoDTO dto) {
        Map<String, Object> resultado = new HashMap<>();
        List<String> conflictos = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        
        try {
            // Validación básica
            if (dto.getStaffMedicoId() == null) {
                conflictos.add("El campo staffMedicoId es obligatorio.");
            }
            
            if (dto.getConsultorioId() == null) {
                conflictos.add("El consultorio es obligatorio.");
            }
            
            if (dto.getHorarios() == null || dto.getHorarios().isEmpty()) {
                conflictos.add("Los horarios son obligatorios.");
            }
            
            // Si hay errores básicos, no continuar
            if (!conflictos.isEmpty()) {
                resultado.put("valido", false);
                resultado.put("conflictos", conflictos);
                resultado.put("advertencias", advertencias);
                return resultado;
            }
            
            // Crear esquema temporal para validaciones
            EsquemaTurno esquemaTemporal = toEntity(dto);
            
            // Validar conflictos de horarios para el mismo médico en diferentes consultorios
            List<EsquemaTurno> esquemasDelMedico = esquemaTurnoRepository.findByStaffMedicoId(dto.getStaffMedicoId());
            List<String> conflictosDetallados = validarConflictosMedicoEnConsultorios(esquemasDelMedico, esquemaTemporal, dto.getHorarios());
            conflictos.addAll(conflictosDetallados);
            
            // Validar disponibilidad del médico
            List<DisponibilidadMedico> disponibilidades = disponibilidadMedicoRepository.findByStaffMedicoId(dto.getStaffMedicoId());
            for (EsquemaTurnoDTO.DiaHorarioDTO horario : dto.getHorarios()) {
                boolean disponible = disponibilidades.stream().anyMatch(disponibilidad -> 
                    disponibilidad.getHorarios().stream().anyMatch(diaHorario -> 
                        normalizarDia(diaHorario.getDia()).equalsIgnoreCase(normalizarDia(horario.getDia())) &&
                        (horario.getHoraInicio().equals(diaHorario.getHoraInicio()) || !horario.getHoraInicio().isBefore(diaHorario.getHoraInicio())) &&
                        (horario.getHoraFin().equals(diaHorario.getHoraFin()) || !horario.getHoraFin().isAfter(diaHorario.getHoraFin()))
                    )
                );

                if (!disponible) {
                    conflictos.add("El médico no tiene disponibilidad para el día " + horario.getDia() +
                            " entre " + horario.getHoraInicio() + " y " + horario.getHoraFin() + ".");
                }
            }
            
            // Validar conflictos en el mismo consultorio
            if (dto.getConsultorioId() != null) {
                List<EsquemaTurno> esquemasEnConsultorio = esquemaTurnoRepository.findByConsultorioId(dto.getConsultorioId());
                boolean hayConflictoConsultorio = esquemasEnConsultorio.stream().anyMatch(existente ->
                        !existente.getId().equals(dto.getId()) &&
                        hayConflictoDeHorarios(existente.getHorarios(), dto.getHorarios()));
                
                if (hayConflictoConsultorio) {
                    // Intentar encontrar consultorio alternativo
                    Integer consultorioAlternativo = resolverConflictoConsultorioAutomaticamente(esquemaTemporal);
                    if (consultorioAlternativo != null) {
                        advertencias.add("Conflicto en consultorio " + dto.getConsultorioId() + 
                                       ". Se sugiere consultorio alternativo: " + consultorioAlternativo);
                    } else {
                        conflictos.add("Conflicto de horarios en el consultorio " + dto.getConsultorioId() + 
                                     ". No hay consultorios alternativos disponibles.");
                    }
                }
            }
            
        } catch (Exception e) {
            conflictos.add("Error en validación: " + e.getMessage());
        }
        
        resultado.put("valido", conflictos.isEmpty());
        resultado.put("conflictos", conflictos);
        resultado.put("advertencias", advertencias);
        
        return resultado;
    }

    @Transactional
    public EsquemaTurnoDTO saveOrUpdate(EsquemaTurnoDTO dto) {
        completarActualizacionParcial(dto);
        if (dto.getId() == null || dto.getId() == 0) {
            if (dto.getConsultorioId() != null) {
                consultorioRepository.findById(dto.getConsultorioId()).ifPresent(consultorio -> {
                    if (consultorio.getCentroAtencion() != null) {
                        suscripcionAccessService.assertOperacion(
                                consultorio.getCentroAtencion().getId(), RecursoSaas.ESQUEMA);
                    }
                });
            }
        }

        // Validación: Staff Médico
        if (dto.getStaffMedicoId() == null) {
            throw new IllegalArgumentException("El campo staffMedicoId es obligatorio.");
        }

        // Validación: Consultorio (ahora es obligatorio)
        if (dto.getConsultorioId() == null) {
            throw new IllegalArgumentException("El consultorio es obligatorio. Debe seleccionar un consultorio específico.");
        }
        if (!consultorioRepository.existsById(dto.getConsultorioId())) {
            throw new IllegalArgumentException("El consultorio especificado no existe.");
        }

        // Validación: Intervalo
        if (dto.getIntervalo() <= 0) {
            throw new IllegalArgumentException("El intervalo debe ser positivo.");
        }       

        // Validación: Horarios específicos del esquema de turno
        if (dto.getHorarios() == null || dto.getHorarios().isEmpty()) {
            throw new IllegalArgumentException("Los horarios son obligatorios.");
        }
        for (EsquemaTurnoDTO.DiaHorarioDTO horario : dto.getHorarios()) {
            if (horario.getHoraInicio().isAfter(horario.getHoraFin())) {
                throw new IllegalArgumentException("La hora de inicio no puede ser mayor a la hora de fin.");
            }
        }

        EsquemaTurno esquemaTurno = toEntity(dto);
        
        // NUEVO: Si ya existe un esquema con la misma disponibilidad, actualizar en lugar de crear uno nuevo
        List<EsquemaTurno> existentes = esquemaTurnoRepository.findByStaffMedicoId(esquemaTurno.getStaffMedico().getId());
        for (EsquemaTurno existente : existentes) {
            Integer nuevoId = esquemaTurno.getId();
            Integer existenteId = existente.getId();

            // Si ambos IDs son null, consideramos que son distintos (nuevo registro)
            boolean mismoId = (nuevoId != null && nuevoId.equals(existenteId));
            if (!mismoId && esquemaTurno.getDisponibilidadMedico().getId()
                    .equals(existente.getDisponibilidadMedico().getId())) {
                // En lugar de lanzar error, combinar horarios con el esquema existente
                System.out.println("🔄 Esquema ya existe para esta disponibilidad, combinando horarios...");
                String aviso = describirSolapamientoConOtrosCentros(esquemaTurno, dto.getHorarios());
                if (aviso != null) {
                    agregarAdvertencia(aviso);
                }
                return combinarHorariosConEsquemaExistente(existente, dto);
            }
        }

        // Validación: Disponibilidad del médico
        List<DisponibilidadMedico> disponibilidades = disponibilidadMedicoRepository.findByStaffMedicoId(dto.getStaffMedicoId());
        for (EsquemaTurnoDTO.DiaHorarioDTO horario : dto.getHorarios()) {
            System.out.println("Validando horario: " + horario.getDia() + " " + horario.getHoraInicio() + " - " + horario.getHoraFin());
            for (DisponibilidadMedico disponibilidad : disponibilidades) {
                System.out.println("Disponibilidad: ");
                disponibilidad.getHorarios().forEach(diaHorario -> 
                    System.out.println("Día: " + diaHorario.getDia() + ", Inicio: " + diaHorario.getHoraInicio() + ", Fin: " + diaHorario.getHoraFin())
                );
            }

            boolean disponible = disponibilidades.stream().anyMatch(disponibilidad -> 
                disponibilidad.getHorarios().stream().anyMatch(diaHorario -> 
                    normalizarDia(diaHorario.getDia()).equalsIgnoreCase(normalizarDia(horario.getDia())) &&
                    (horario.getHoraInicio().equals(diaHorario.getHoraInicio()) || !horario.getHoraInicio().isBefore(diaHorario.getHoraInicio())) &&
                    (horario.getHoraFin().equals(diaHorario.getHoraFin()) || !horario.getHoraFin().isAfter(diaHorario.getHoraFin()))
                )
            );

            if (!disponible) {
                throw new IllegalArgumentException("El médico no tiene disponibilidad para el día " + horario.getDia() +
                        " entre " + horario.getHoraInicio() + " y " + horario.getHoraFin() + ".");
            }
        }

        // NUEVO: Ajustar automáticamente los horarios del esquema para que encajen en el consultorio
        // El consultorio ahora es obligatorio, por lo que siempre debería estar presente
        if (esquemaTurno.getConsultorio() != null) {
            try {
                System.out.println("🔧 APLICANDO AJUSTE AUTOMÁTICO DE HORARIOS");
                List<String> advertencias = consultorioDistribucionService.ajustarHorariosEsquemaAConsultorio(
                    esquemaTurno.getHorarios(), esquemaTurno.getConsultorio().getId());
                
                if (!advertencias.isEmpty()) {
                    System.out.println("⚠️ ADVERTENCIAS DE AJUSTE:");
                    advertencias.forEach(System.out::println);
                }
            } catch (Exception e) {
                System.err.println("Error al ajustar horarios automáticamente: " + e.getMessage());
                // Continuar con las validaciones normales
            }
        }

        // Validación: Conflictos de horarios en el mismo consultorio
        // Si hay conflicto, intentar usar el algoritmo de distribución automáticamente
        if (esquemaTurno.getConsultorio() != null) {
            List<EsquemaTurno> esquemasEnConsultorio = esquemaTurnoRepository.findByConsultorioId(dto.getConsultorioId());
            boolean hayConflictoConsultorio = esquemasEnConsultorio.stream().anyMatch(existente ->
                    !existente.getId().equals(esquemaTurno.getId()) &&
                    hayConflictoDeHorarios(existente.getHorarios(), dto.getHorarios()));
            
            if (hayConflictoConsultorio) {
                boolean soloElMismoMedico = esquemasEnConsultorio.stream()
                    .filter(existente -> (esquemaTurno.getId() == null || !existente.getId().equals(esquemaTurno.getId()))
                            && hayConflictoDeHorarios(existente.getHorarios(), dto.getHorarios()))
                    .allMatch(existente -> mismoMedico(existente, esquemaTurno));

                // Otro bloque del mismo médico no bloquea el alta: el aviso queda en el mensaje.
                if (!soloElMismoMedico) {
                    EsquemaTurno esquemaConflictivo = esquemasEnConsultorio.stream()
                        .filter(existente -> (esquemaTurno.getId() == null || !existente.getId().equals(esquemaTurno.getId()))
                                && hayConflictoDeHorarios(existente.getHorarios(), dto.getHorarios()))
                        .findFirst()
                        .orElse(null);

                    String nombreConsultorio = esquemaTurno.getConsultorio().getNombre();
                    String detalleConflicto = "";

                    if (esquemaConflictivo != null && esquemaConflictivo.getStaffMedico() != null &&
                        esquemaConflictivo.getStaffMedico().getMedico() != null) {
                        String nombreMedicoConflictivo = esquemaConflictivo.getStaffMedico().getMedico().getNombre() +
                                                        " " + esquemaConflictivo.getStaffMedico().getMedico().getApellido();
                        detalleConflicto = " Ya existe un esquema de turno para el Dr. " + nombreMedicoConflictivo +
                                          " en el mismo horario.";
                    }

                    Integer consultorioAlternativo = resolverConflictoConsultorioAutomaticamente(esquemaTurno);

                    if (consultorioAlternativo != null) {
                        dto.setConsultorioId(consultorioAlternativo);
                        esquemaTurno.setConsultorio(consultorioRepository.findById(consultorioAlternativo)
                            .orElseThrow(() -> new IllegalArgumentException("Consultorio alternativo no encontrado")));
                    } else if (esquemaTurno.getId() != null) {
                        EsquemaTurno previo = esquemaTurnoRepository.findById(esquemaTurno.getId()).orElse(null);
                        if (previo != null && previo.getConsultorio() != null) {
                            esquemaTurno.setConsultorio(previo.getConsultorio());
                            dto.setConsultorioId(previo.getConsultorio().getId());
                        } else {
                            throw new IllegalStateException("El consultorio '" + nombreConsultorio +
                                "' ya está ocupado en el horario seleccionado." + detalleConflicto +
                                " Por favor, seleccione otro consultorio o un horario diferente.");
                        }
                    } else {
                        throw new IllegalStateException("El consultorio '" + nombreConsultorio +
                            "' ya está ocupado en el horario seleccionado." + detalleConflicto +
                            " Por favor, seleccione otro consultorio o un horario diferente.");
                    }
                }
            }
        }

        // Validación: Conflictos de horarios para el mismo médico en diferentes consultorios
        List<EsquemaTurno> esquemasDelMedico = esquemaTurnoRepository.findByStaffMedicoId(dto.getStaffMedicoId());
        List<String> conflictosDetallados = validarConflictosMedicoEnConsultorios(esquemasDelMedico, esquemaTurno, dto.getHorarios());
        
        if (!conflictosDetallados.isEmpty()) {
            agregarAdvertencia("solapamiento de horarios del médico: " + String.join("; ", conflictosDetallados));
        }

        String avisoOtrosCentros = describirSolapamientoConOtrosCentros(esquemaTurno, dto.getHorarios());
        if (avisoOtrosCentros != null) {
            agregarAdvertencia(avisoOtrosCentros);
        }

        // NUEVA VALIDACIÓN: Verificar disponibilidad del consultorio y conflictos
        if (esquemaTurno.getConsultorio() != null) {
            validarDisponibilidadConsultorio(esquemaTurno);
        }

        return toDTO(esquemaTurnoRepository.save(esquemaTurno));
    }

    /**
     * Redistribuir esquemas de turno de un centro según porcentajes configurados
     * También crea esquemas automáticamente para disponibilidades sin esquemas
     */
    @Transactional
    public int redistribuirEsquemasPorCentro(Integer centroId) {
        // Validar que el centro existe
        if (!centroAtencionRepository.existsById(centroId)) {
            throw new IllegalArgumentException("Centro de atención no encontrado con ID: " + centroId);
        }

        int procesados = 0;

        // PASO 1: Redistribuir esquemas existentes
        List<EsquemaTurno> esquemas = esquemaTurnoRepository.findByCentroAtencionId(centroId);
        
        for (EsquemaTurno esquema : esquemas) {
            try {
                Integer nuevoConsultorioId = consultorioDistribucionService
                    .asignarConsultorioSegunPorcentajes(esquema.getStaffMedico().getId(), centroId);
                
                if (nuevoConsultorioId != null) {
                    esquema.setConsultorio(consultorioRepository.findById(nuevoConsultorioId)
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Consultorio no encontrado con ID: " + nuevoConsultorioId)));
                    
                    // AJUSTE AUTOMÁTICO: Ajustar horarios del esquema al consultorio asignado
                    try {
                        List<String> advertencias = consultorioDistribucionService
                            .ajustarHorariosEsquemaAConsultorio(esquema.getHorarios(), nuevoConsultorioId);
                        
                        if (!advertencias.isEmpty()) {
                            System.out.println("🔧 AJUSTES APLICADOS al esquema ID " + esquema.getId() + ":");
                            advertencias.forEach(adv -> System.out.println("  - " + adv));
                        }
                    } catch (Exception adjustError) {
                        System.err.println("Error al ajustar horarios del esquema ID " + esquema.getId() + ": " + adjustError.getMessage());
                        // Continuar sin ajustar horarios
                    }
                    
                    esquemaTurnoRepository.save(esquema);
                    procesados++;
                }
            } catch (Exception e) {
                System.err.println("Error al redistribuir esquema ID " + esquema.getId() + ": " + e.getMessage());
                // Continuar con el siguiente esquema en caso de error
            }
        }

        // PASO 2: Crear esquemas automáticamente para disponibilidades sin esquemas
        try {
            int esquemasCreadosAutomaticamente = crearEsquemasDesdeDisponibilidades(centroId);
            procesados += esquemasCreadosAutomaticamente;
            
            if (esquemasCreadosAutomaticamente > 0) {
                System.out.println("✓ Se crearon automáticamente " + esquemasCreadosAutomaticamente + 
                                 " esquemas desde disponibilidades médicas");
            }
        } catch (Exception e) {
            System.err.println("Error al crear esquemas automáticos: " + e.getMessage());
        }

        return procesados;
    }

    /**
     * Crea esquemas de turno automáticamente desde las disponibilidades médicas que no tienen esquemas asociados
     */
    @Transactional
    public int crearEsquemasDesdeDisponibilidades(Integer centroId) {
        // Obtener todas las disponibilidades médicas del centro
        List<DisponibilidadMedico> disponibilidades = disponibilidadMedicoRepository
            .findByStaffMedico_CentroAtencionId(centroId);
        
        // Obtener IDs de disponibilidades que ya tienen esquemas asociados
        List<EsquemaTurno> esquemas = esquemaTurnoRepository.findByCentroAtencionId(centroId);
        Set<Integer> disponibilidadesConEsquema = esquemas.stream()
            .map(e -> e.getDisponibilidadMedico().getId())
            .collect(Collectors.toSet());
        
        // Filtrar disponibilidades sin esquemas
        List<DisponibilidadMedico> disponibilidadesSinEsquema = disponibilidades.stream()
            .filter(d -> !disponibilidadesConEsquema.contains(d.getId()))
            .collect(Collectors.toList());
        
        int esquemasCreadps = 0;
        
        for (DisponibilidadMedico disponibilidad : disponibilidadesSinEsquema) {
            try {
                // Crear esquema automático con intervalo por defecto de 30 minutos
                EsquemaTurno nuevoEsquema = new EsquemaTurno();
                nuevoEsquema.setDisponibilidadMedico(disponibilidad);
                nuevoEsquema.setStaffMedico(disponibilidad.getStaffMedico());
                nuevoEsquema.setCentroAtencion(centroAtencionRepository.findById(centroId)
                    .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado")));
                nuevoEsquema.setIntervalo(20); // Intervalo por defecto de 20 minutos
                
                // Copiar horarios desde la disponibilidad
                List<EsquemaTurno.Horario> horariosEsquema = disponibilidad.getHorarios().stream()
                    .map(horario -> {
                        EsquemaTurno.Horario nuevoHorario = new EsquemaTurno.Horario();
                        nuevoHorario.setDia(horario.getDia());
                        nuevoHorario.setHoraInicio(horario.getHoraInicio());
                        nuevoHorario.setHoraFin(horario.getHoraFin());
                        return nuevoHorario;
                    })
                    .collect(Collectors.toList());
                
                nuevoEsquema.setHorarios(horariosEsquema);
                
                // Asignar consultorio según porcentajes
                Integer consultorioId = consultorioDistribucionService
                    .asignarConsultorioSegunPorcentajes(disponibilidad.getStaffMedico().getId(), centroId);
                
                if (consultorioId != null) {
                    nuevoEsquema.setConsultorio(consultorioRepository.findById(consultorioId)
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Consultorio no encontrado con ID: " + consultorioId)));
                    
                    // AJUSTE AUTOMÁTICO: Ajustar horarios del esquema al consultorio asignado
                    try {
                        List<String> advertencias = consultorioDistribucionService
                            .ajustarHorariosEsquemaAConsultorio(horariosEsquema, consultorioId);
                        
                        if (!advertencias.isEmpty()) {
                            System.out.println("🔧 AJUSTES APLICADOS al nuevo esquema de " + 
                                             disponibilidad.getStaffMedico().getMedico().getNombre() + ":");
                            advertencias.forEach(adv -> System.out.println("  - " + adv));
                        }
                    } catch (Exception adjustError) {
                        System.err.println("Error al ajustar horarios del nuevo esquema: " + adjustError.getMessage());
                        // Continuar sin ajustar horarios
                    }
                }
                
                // Guardar el nuevo esquema
                esquemaTurnoRepository.save(nuevoEsquema);
                esquemasCreadps++;
                
                System.out.println("✓ Esquema creado automáticamente para " + 
                                 disponibilidad.getStaffMedico().getMedico().getNombre() + " " +
                                 disponibilidad.getStaffMedico().getMedico().getApellido());
                
            } catch (Exception e) {
                System.err.println("Error al crear esquema automático para disponibilidad ID " + 
                                 disponibilidad.getId() + ": " + e.getMessage());
                // Continuar con la siguiente disponibilidad
            }
        }
        
        return esquemasCreadps;
    }

    /**
     * Redistribuir esquemas de turno de un médico específico
     */
    @Transactional
    public int redistribuirEsquemasPorMedico(Integer medicoId) {
        // Obtener todos los esquemas del médico
        List<EsquemaTurno> esquemas = esquemaTurnoRepository.findByStaffMedicoId(medicoId);
        
        if (esquemas.isEmpty()) {
            return 0;
        }

        int procesados = 0;
        Integer centroId = null;
        
        // Obtener el centro del primer esquema (todos deberían ser del mismo centro)
        if (!esquemas.isEmpty()) {
            centroId = esquemas.get(0).getCentroAtencion().getId();
        }

        for (EsquemaTurno esquema : esquemas) {
            try {
                Integer nuevoConsultorioId = consultorioDistribucionService
                    .asignarConsultorioSegunPorcentajes(medicoId, centroId);
                
                if (nuevoConsultorioId != null) {
                    esquema.setConsultorio(consultorioRepository.findById(nuevoConsultorioId)
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Consultorio no encontrado con ID: " + nuevoConsultorioId)));
                    esquemaTurnoRepository.save(esquema);
                    procesados++;
                }
            } catch (Exception e) {
                System.err.println("Error al redistribuir esquema ID " + esquema.getId() + 
                    " del médico " + medicoId + ": " + e.getMessage());
                // Continuar con el siguiente esquema en caso de error
            }
        }

        return procesados;
    }

    private EsquemaTurnoDTO toDTO(EsquemaTurno esquema) {
        EsquemaTurnoDTO dto = new EsquemaTurnoDTO();
        dto.setId(esquema.getId());
        dto.setIntervalo(esquema.getIntervalo());
        dto.setDisponibilidadMedicoId(esquema.getDisponibilidadMedico().getId());

        dto.setHorarios(esquema.getHorarios().stream().map(horario -> {
            EsquemaTurnoDTO.DiaHorarioDTO diaHorarioDTO = new EsquemaTurnoDTO.DiaHorarioDTO();
            diaHorarioDTO.setDia(horario.getDia());
            diaHorarioDTO.setHoraInicio(horario.getHoraInicio());
            diaHorarioDTO.setHoraFin(horario.getHoraFin());
            return diaHorarioDTO;
        }).collect(Collectors.toList()));

        // Mapear nombres
        dto.setStaffMedicoId(esquema.getStaffMedico().getId());
        dto.setNombreStaffMedico(esquema.getStaffMedico().getMedico().getNombre() + " " +
                esquema.getStaffMedico().getMedico().getApellido());

        dto.setCentroId(esquema.getCentroAtencion().getId());
        dto.setNombreCentro(esquema.getCentroAtencion().getNombre());

        // Manejar consultorio opcional
        if (esquema.getConsultorio() != null) {
            dto.setConsultorioId(esquema.getConsultorio().getId());
            dto.setNombreConsultorio(esquema.getConsultorio().getNombre());
        } else {
            dto.setConsultorioId(null);
            dto.setNombreConsultorio("Pendiente de asignación");
        }

        return dto;
    }

    private EsquemaTurno toEntity(EsquemaTurnoDTO dto) {
        EsquemaTurno esquema = new EsquemaTurno();
        esquema.setId(dto.getId());
        esquema.setIntervalo(dto.getIntervalo());

        DisponibilidadMedico disponibilidad = disponibilidadMedicoRepository.findById(dto.getDisponibilidadMedicoId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "DisponibilidadMedico no encontrada con ID: " + dto.getDisponibilidadMedicoId()));
        esquema.setDisponibilidadMedico(disponibilidad);

        esquema.setStaffMedico(disponibilidad.getStaffMedico());
        esquema.setCentroAtencion(centroAtencionRepository.findById(dto.getCentroId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "CentroAtencion no encontrado con ID: " + dto.getCentroId())));
        
        // Manejar consultorio opcional
        if (dto.getConsultorioId() != null) {
            esquema.setConsultorio(consultorioRepository.findById(dto.getConsultorioId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Consultorio no encontrado con ID: " + dto.getConsultorioId())));
        } else {
            esquema.setConsultorio(null);
        }

        esquema.setHorarios(dto.getHorarios().stream().map(horarioDTO -> {
            EsquemaTurno.Horario horario = new EsquemaTurno.Horario();
            horario.setDia(horarioDTO.getDia());
            horario.setHoraInicio(horarioDTO.getHoraInicio());
            horario.setHoraFin(horarioDTO.getHoraFin());
            return horario;
        }).collect(Collectors.toList()));

        return esquema;
    }

    /**
     * Valida conflictos específicos para un médico en diferentes consultorios
     * y devuelve una lista detallada de los conflictos encontrados
     */
    private List<String> validarConflictosMedicoEnConsultorios(List<EsquemaTurno> esquemasDelMedico, 
                                                               EsquemaTurno esquemaNuevo, 
                                                               List<EsquemaTurnoDTO.DiaHorarioDTO> nuevosHorarios) {
        List<String> conflictos = new ArrayList<>();
        
        for (EsquemaTurno existente : esquemasDelMedico) {
            // Omitir si es el mismo esquema (caso de actualización)
            if (existente.getId() != null && existente.getId().equals(esquemaNuevo.getId())) {
                continue;
            }
            
            // Verificar conflictos horario por horario
            for (EsquemaTurnoDTO.DiaHorarioDTO nuevoHorario : nuevosHorarios) {
                for (EsquemaTurno.Horario horarioExistente : existente.getHorarios()) {
                    if (hayConflictoHorarioDetallado(nuevoHorario, horarioExistente)) {
                        String consultorioExistente = existente.getConsultorio() != null ? 
                            existente.getConsultorio().getNombre() : "Sin consultorio asignado";
                        String consultorioNuevo = esquemaNuevo.getConsultorio() != null ? 
                            esquemaNuevo.getConsultorio().getNombre() : "Sin consultorio asignado";
                            
                        conflictos.add(String.format(
                            "El médico ya está asignado en '%s' el %s de %s a %s. " +
                            "Conflicto con el horario propuesto para '%s' el %s de %s a %s.",
                            consultorioExistente,
                            horarioExistente.getDia(),
                            horarioExistente.getHoraInicio(),
                            horarioExistente.getHoraFin(),
                            consultorioNuevo,
                            nuevoHorario.getDia(),
                            nuevoHorario.getHoraInicio(),
                            nuevoHorario.getHoraFin()
                        ));
                    }
                }
            }
        }
        
        return conflictos;
    }
    
    /**
     * Verifica si hay conflicto entre un horario nuevo y uno existente
     */
    private boolean hayConflictoHorarioDetallado(EsquemaTurnoDTO.DiaHorarioDTO nuevoHorario, 
                                                EsquemaTurno.Horario horarioExistente) {
        // Deben ser el mismo día
        if (!nuevoHorario.getDia().equalsIgnoreCase(horarioExistente.getDia())) {
            return false;
        }
        
        return intervalosSeSolapan(
            nuevoHorario.getHoraInicio(), nuevoHorario.getHoraFin(),
            horarioExistente.getHoraInicio(), horarioExistente.getHoraFin());
    }

    private boolean hayConflictoDeHorarios(List<EsquemaTurno.Horario> horariosExistentes,
            List<EsquemaTurnoDTO.DiaHorarioDTO> nuevosHorarios) {
        return nuevosHorarios.stream()
                .anyMatch(nuevoHorario -> horariosExistentes.stream()
                        .anyMatch(horarioExistente -> nuevoHorario.getDia().equals(horarioExistente.getDia()) &&
                                intervalosSeSolapan(
                                    nuevoHorario.getHoraInicio(), nuevoHorario.getHoraFin(),
                                    horarioExistente.getHoraInicio(), horarioExistente.getHoraFin())));
    }

    /**
     * Valida que el consultorio esté disponible para el esquema de turno.
     * Primero ajusta automáticamente los horarios para que encajen en el consultorio.
     * Luego verifica conflictos de horarios con otros médicos usando el mismo consultorio.
     */
    private void validarDisponibilidadConsultorio(EsquemaTurno esquemaTurno) {
        if (esquemaTurno.getConsultorio() == null) {
            return; // Ya validado en saveOrUpdate
        }
        
        // PASO 1: Ajustar automáticamente los horarios del esquema al consultorio
        List<String> advertencias = consultorioDistribucionService.ajustarHorariosEsquemaAConsultorio(
            esquemaTurno.getHorarios(), 
            esquemaTurno.getConsultorio().getId()
        );
        
        // Mostrar advertencias sobre los ajustes realizados
        if (!advertencias.isEmpty()) {
            System.out.println("🔧 AJUSTES AUTOMÁTICOS REALIZADOS:");
            for (String advertencia : advertencias) {
                System.out.println("  - " + advertencia);
            }
        }
        
        // Verificar que después del ajuste aún queden horarios válidos
        if (esquemaTurno.getHorarios().isEmpty()) {
            throw new IllegalStateException(String.format(
                "Después del ajuste automático no quedaron horarios válidos para el consultorio %s. " +
                "Los horarios del médico no se superponen con los horarios de atención del consultorio.",
                esquemaTurno.getConsultorio().getNombre()
            ));
        }
        
        // PASO 2: Obtener esquemas existentes que usan el mismo consultorio
        List<EsquemaTurno> esquemasExistentes = esquemaTurnoRepository.findByConsultorioId(esquemaTurno.getConsultorio().getId());
        
        // Filtrar el esquema actual si está siendo actualizado
        if (esquemaTurno.getId() != null) {
            esquemasExistentes = esquemasExistentes.stream()
                .filter(e -> !e.getId().equals(esquemaTurno.getId()))
                .collect(Collectors.toList());
        }
        
        // PASO 3: Otro médico en el mismo consultorio sigue siendo un rechazo.
        // Un bloque del mismo médico no corta el alta: queda como advertencia.
        for (EsquemaTurno.Horario nuevoHorario : esquemaTurno.getHorarios()) {
            for (EsquemaTurno esquemaExistente : esquemasExistentes) {
                for (EsquemaTurno.Horario horarioExistente : esquemaExistente.getHorarios()) {
                    if (!hayConflictoHorario(nuevoHorario, horarioExistente)) {
                        continue;
                    }
                    if (mismoMedico(esquemaExistente, esquemaTurno)) {
                        agregarAdvertencia(String.format(
                            "solapamiento con otro horario del médico el %s de %s a %s",
                            horarioExistente.getDia(),
                            horarioExistente.getHoraInicio(),
                            horarioExistente.getHoraFin()));
                        continue;
                    }
                    throw new IllegalStateException(String.format(
                        "Conflicto de horarios: El médico %s ya tiene asignado el consultorio %d " +
                        "el %s de %s a %s. Conflicto con el horario propuesto para %s de %s a %s.",
                        esquemaExistente.getStaffMedico().getMedico().getNombre(),
                        esquemaTurno.getConsultorio().getId(),
                        horarioExistente.getDia(),
                        horarioExistente.getHoraInicio(),
                        horarioExistente.getHoraFin(),
                        nuevoHorario.getDia(),
                        nuevoHorario.getHoraInicio(),
                        nuevoHorario.getHoraFin()
                    ));
                }
            }
        }
    }
    
    /**
     * Verifica si dos horarios entran en conflicto.
     */
    private boolean hayConflictoHorario(EsquemaTurno.Horario horario1, EsquemaTurno.Horario horario2) {
        // Deben ser el mismo día
        if (!horario1.getDia().equalsIgnoreCase(horario2.getDia())) {
            return false;
        }
        
        return intervalosSeSolapan(
            horario1.getHoraInicio(), horario1.getHoraFin(),
            horario2.getHoraInicio(), horario2.getHoraFin());
    }

    /**
     * Dos intervalos [inicio, fin) se solapan solo si se pisan.
     * Si uno termina cuando el otro empieza, no hay conflicto.
     */
    private boolean intervalosSeSolapan(LocalTime inicio1, LocalTime fin1, LocalTime inicio2, LocalTime fin2) {
        if (inicio1 == null || fin1 == null || inicio2 == null || fin2 == null) {
            return false;
        }
        return inicio1.isBefore(fin2) && inicio2.isBefore(fin1);
    }

    /**
     * Un médico no puede tener el mismo horario en dos centros.
     * El límite exacto (fin == inicio) no se considera solapamiento.
     */
    private boolean mismoMedico(EsquemaTurno a, EsquemaTurno b) {
        if (a.getStaffMedico() == null || b.getStaffMedico() == null) {
            return false;
        }
        if (a.getStaffMedico().getId() != null && a.getStaffMedico().getId().equals(b.getStaffMedico().getId())) {
            return true;
        }
        if (a.getStaffMedico().getMedico() == null || b.getStaffMedico().getMedico() == null
                || a.getStaffMedico().getMedico().getId() == null) {
            return false;
        }
        return a.getStaffMedico().getMedico().getId().equals(b.getStaffMedico().getMedico().getId());
    }

    private String describirSolapamientoConOtrosCentros(EsquemaTurno esquemaNuevo,
            List<EsquemaTurnoDTO.DiaHorarioDTO> nuevosHorarios) {
        if (esquemaNuevo.getStaffMedico() == null || esquemaNuevo.getStaffMedico().getMedico() == null
                || nuevosHorarios == null) {
            return null;
        }
        Integer medicoId = esquemaNuevo.getStaffMedico().getMedico().getId();
        Integer staffActualId = esquemaNuevo.getStaffMedico().getId();
        List<StaffMedico> asociaciones = staffMedicoRepository.findByMedicoId(medicoId);
        List<String> avisos = new ArrayList<>();

        for (StaffMedico staff : asociaciones) {
            if (staff.getId() != null && staff.getId().equals(staffActualId)) {
                continue;
            }
            for (EsquemaTurno otro : esquemaTurnoRepository.findByStaffMedicoId(staff.getId())) {
                if (otro.getId() != null && otro.getId().equals(esquemaNuevo.getId())) {
                    continue;
                }
                if (otro.getHorarios() == null) {
                    continue;
                }
                for (EsquemaTurnoDTO.DiaHorarioDTO nuevo : nuevosHorarios) {
                    for (EsquemaTurno.Horario existente : otro.getHorarios()) {
                        if (nuevo.getDia() == null || existente.getDia() == null
                                || !nuevo.getDia().equalsIgnoreCase(existente.getDia())) {
                            continue;
                        }
                        if (intervalosSeSolapan(nuevo.getHoraInicio(), nuevo.getHoraFin(),
                                existente.getHoraInicio(), existente.getHoraFin())) {
                            String centro = otro.getCentroAtencion() != null
                                    ? otro.getCentroAtencion().getNombre()
                                    : "otro centro";
                            avisos.add(String.format(
                                    "solapamiento con %s el %s de %s a %s",
                                    centro, existente.getDia(), existente.getHoraInicio(), existente.getHoraFin()));
                        }
                    }
                }
            }
        }

        if (avisos.isEmpty()) {
            return null;
        }
        return "Advertencia de solapamiento con otro centro: " + String.join("; ", avisos);
    }

    /**
     * PUT con un solo campo: completa el resto desde el esquema guardado.
     */
    private void completarActualizacionParcial(EsquemaTurnoDTO dto) {
        if (dto.getId() == null || dto.getId() == 0) {
            return;
        }
        Optional<EsquemaTurno> existenteOpt = esquemaTurnoRepository.findById(dto.getId());
        if (existenteOpt.isEmpty()) {
            return;
        }
        EsquemaTurno existente = existenteOpt.get();
        if (dto.getStaffMedicoId() == null && existente.getStaffMedico() != null) {
            dto.setStaffMedicoId(existente.getStaffMedico().getId());
        }
        if (dto.getConsultorioId() == null && existente.getConsultorio() != null) {
            dto.setConsultorioId(existente.getConsultorio().getId());
        }
        if (dto.getCentroId() == null && existente.getCentroAtencion() != null) {
            dto.setCentroId(existente.getCentroAtencion().getId());
        }
        if (dto.getDisponibilidadMedicoId() == null && existente.getDisponibilidadMedico() != null) {
            dto.setDisponibilidadMedicoId(existente.getDisponibilidadMedico().getId());
        }
        if (dto.getIntervalo() <= 0 && existente.getIntervalo() > 0) {
            dto.setIntervalo(existente.getIntervalo());
        }
        if ((dto.getHorarios() == null || dto.getHorarios().isEmpty()) && existente.getHorarios() != null) {
            dto.setHorarios(existente.getHorarios().stream().map(horario -> {
                EsquemaTurnoDTO.DiaHorarioDTO copia = new EsquemaTurnoDTO.DiaHorarioDTO();
                copia.setDia(horario.getDia());
                copia.setHoraInicio(horario.getHoraInicio());
                copia.setHoraFin(horario.getHoraFin());
                return copia;
            }).collect(Collectors.toList()));
        }
    }

    /**
     * Intenta resolver un conflicto de consultorio automáticamente usando el algoritmo de distribución
     */
    private Integer resolverConflictoConsultorioAutomaticamente(EsquemaTurno esquemaTurno) {
        try {
            return consultorioDistribucionService.asignarConsultorioSegunPorcentajes(
                esquemaTurno.getStaffMedico().getId(),
                esquemaTurno.getCentroAtencion().getId()
            );
        } catch (Exception e) {
            System.err.println("Error al resolver conflicto automáticamente: " + e.getMessage());
            return null;
        }
    }

    /**
     * Combina horarios nuevos con un esquema existente, evitando duplicados
     */
    private EsquemaTurnoDTO combinarHorariosConEsquemaExistente(EsquemaTurno esquemaExistente, EsquemaTurnoDTO nuevoDto) {
        List<EsquemaTurno.Horario> horariosExistentes = esquemaExistente.getHorarios();
        List<EsquemaTurnoDTO.DiaHorarioDTO> horariosNuevos = nuevoDto.getHorarios();

        int horariosAgregados = 0;

        // Agregar solo horarios que no existan ya
        for (EsquemaTurnoDTO.DiaHorarioDTO nuevoHorario : horariosNuevos) {
            boolean yaExiste = horariosExistentes.stream().anyMatch(existente ->
                normalizarDia(existente.getDia()).equals(normalizarDia(nuevoHorario.getDia())) &&
                existente.getHoraInicio().equals(nuevoHorario.getHoraInicio()) &&
                existente.getHoraFin().equals(nuevoHorario.getHoraFin())
            );

            if (!yaExiste) {
                EsquemaTurno.Horario horario = new EsquemaTurno.Horario();
                horario.setDia(nuevoHorario.getDia());
                horario.setHoraInicio(nuevoHorario.getHoraInicio());
                horario.setHoraFin(nuevoHorario.getHoraFin());
                horariosExistentes.add(horario);
                horariosAgregados++;
            }
        }

        if (horariosAgregados == 0) {
            throw new IllegalStateException("Todos los horarios propuestos ya existen en el esquema.");
        }

        // Actualizar el intervalo si es válido y diferente
        if (nuevoDto.getIntervalo() > 0) {
            esquemaExistente.setIntervalo(nuevoDto.getIntervalo());
        }

        // Actualizar el consultorio si se especificó uno diferente
        Integer consultorioExistenteId = esquemaExistente.getConsultorio() != null ?
            esquemaExistente.getConsultorio().getId() : null;

        if (nuevoDto.getConsultorioId() != null &&
            !nuevoDto.getConsultorioId().equals(consultorioExistenteId)) {
            esquemaExistente.setConsultorio(consultorioRepository.findById(nuevoDto.getConsultorioId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Consultorio no encontrado con ID: " + nuevoDto.getConsultorioId())));
        }

        // Guardar el esquema actualizado
        EsquemaTurno actualizado = esquemaTurnoRepository.save(esquemaExistente);

        System.out.println("✅ Esquema actualizado exitosamente. Se agregaron " + horariosAgregados + " horario(s) nuevo(s).");

        return toDTO(actualizado);
    }

    /**
     * Normaliza el nombre de un día eliminando tildes y convirtiendo a mayúsculas
     */
    private String normalizarDia(String dia) {
        if (dia == null) return "";
        return java.text.Normalizer.normalize(dia, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toUpperCase();
    }
}