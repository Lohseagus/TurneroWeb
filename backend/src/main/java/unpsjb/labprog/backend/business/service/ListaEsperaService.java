package unpsjb.labprog.backend.business.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unpsjb.labprog.backend.business.repository.*;
import unpsjb.labprog.backend.dto.ListaEsperaDTO;
import unpsjb.labprog.backend.dto.TurnoDTO;
import unpsjb.labprog.backend.exception.PacienteSinFichaException;
import unpsjb.labprog.backend.exception.TurnoTomadoException;
import unpsjb.labprog.backend.model.*;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.saas.RecursoSaas;
import unpsjb.labprog.backend.saas.SuscripcionAccessService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
public class ListaEsperaService {

    @Autowired
    private ListaEsperaRepository repository;

    @Autowired
    private CentroAccessGuard centroAccess;

    @Autowired
    private SuscripcionAccessService suscripcionAccessService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private PacienteAccessGuard pacienteAccess;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    @Autowired
    private CentroAtencionRepository centroAtencionRepository;

    @Autowired
    private MedicoRepository medicoRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;

    @Autowired
    private TurnoSlotGuard slotGuard;

    @Autowired
    private ReglasReservaTurno reglasReserva;

    /**
     * Obtiene todas las entradas de la lista de espera con filtrado automático multi-tenencia.
     * - SUPERADMIN: Ve todas las listas de espera globalmente
     * - ADMINISTRADOR/OPERADOR/MEDICO: Solo listas de espera de su centro
     * - PACIENTE: Ve todas las listas de espera (acceso global)
     * 
     * @return Lista completa de solicitudes en espera
     */
    public List<ListaEsperaDTO> findAll() {
        List<ListaEsperaDTO> lista = visibles().stream()
                .sorted(OrdenListaEspera.COMPARADOR)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        completarPosiciones(lista);
        return lista;
    }

    /** Solicitudes de los centros de la cuenta; todas si la cuenta no está limitada. */
    private List<ListaEspera> visibles() {
        return centroAccess.filtrar(repository.findAll(), ListaEsperaService::centroDe);
    }

    /** Solicitud por id; 403 si es de un centro que no es de la cuenta. */
    private Optional<ListaEspera> buscarPermitida(Long id) {
        Optional<ListaEspera> solicitud = repository.findById(id);
        solicitud.ifPresent(le -> centroAccess.exigirCentro(centroDe(le)));
        return solicitud;
    }

    private static Integer centroDe(ListaEspera solicitud) {
        return solicitud.getCentroAtencion() == null ? null : solicitud.getCentroAtencion().getId();
    }

    /**
     * Busca una entrada específica por ID
     * 
     * @param id Identificador de la entrada
     * @return Optional con la entrada si existe
     */
    // Obtener por ID
    public Optional<ListaEsperaDTO> findById(Long id) {
        return buscarPermitida(id).map(this::convertToDTO);
    }

    /**
     * Agrega una nueva solicitud a la lista de espera
     * 
     * @param listaEspera Objeto con los datos de la solicitud
     * @return La solicitud guardada con su ID asignado
     */
    @Transactional
    public ListaEsperaDTO save(ListaEsperaDTO dto) {
        centroAccess.exigirCentro(dto.getCentroAtencionId());
        ListaEspera listaEspera = convertToEntity(dto);
        User actor = AuditContext.getCurrentUserObject();
        boolean medicoHabilitado = medicoPuedeFijarUrgencia(actor, listaEspera);
        listaEspera.setUrgenciaMedica(ReglasListaEspera.urgenciaEfectiva(
                actor, ReglasListaEspera.parsearUrgencia(dto.getUrgenciaMedica()), medicoHabilitado));
        // La fecha de ingreso la pone el servidor. Lo que mande el cliente se descarta.
        listaEspera.setFechaSolicitud(LocalDateTime.now(ReglasReservaTurno.ZONA));
        if (listaEspera.getEstado() == null || listaEspera.getEstado().isEmpty()
                || (actor != null && actor.getRole() == Role.PACIENTE)) {
            listaEspera.setEstado("PENDIENTE");
        }

        validarSolicitud(listaEspera);
        if (dto.getId() == null || dto.getId() == 0) {
            suscripcionAccessService.assertOperacion(dto.getCentroAtencionId(), RecursoSaas.LISTA_ESPERA);
        }

        ListaEspera guardada = repository.save(listaEspera);
        if (ReglasListaEspera.puedeElegirUrgencia(actor, medicoHabilitado)) {
            auditarUrgencia(guardada, null, guardada.getUrgenciaMedica().name(), actor);
        }
        return convertToDTO(guardada);
    }

    /**
     * Actualiza una entrada existente en la lista de espera
     * 
     * @param id          Identificador de la entrada a actualizar
     * @param listaEspera Datos actualizados
     * @return La entrada actualizada
     * @throws RuntimeException si no se encuentra la entrada
     */
    @Transactional
    public ListaEsperaDTO update(ListaEsperaDTO dto) {
        centroAccess.exigirCentro(dto.getCentroAtencionId());
        // Verificar que existe
        Optional<ListaEspera> existing = buscarPermitida(dto.getId());
        if (existing.isEmpty()) {
            throw new RuntimeException("Entrada de lista de espera no encontrada con ID: " + dto.getId());
        }

        ListaEspera previa = existing.get();
        ListaEspera listaEspera = convertToEntity(dto);
        User actor = AuditContext.getCurrentUserObject();
        boolean medicoHabilitado = medicoPuedeFijarUrgencia(actor, listaEspera);
        ListaEspera.UrgenciaMedica anterior = previa.getUrgenciaMedica();
        if (ReglasListaEspera.puedeElegirUrgencia(actor, medicoHabilitado)) {
            listaEspera.setUrgenciaMedica(ReglasListaEspera.urgenciaEfectiva(
                    actor, ReglasListaEspera.parsearUrgencia(dto.getUrgenciaMedica()), medicoHabilitado));
        } else {
            listaEspera.setUrgenciaMedica(anterior);
        }
        listaEspera.setFechaSolicitud(previa.getFechaSolicitud());

        validarSolicitud(listaEspera);

        ListaEspera guardada = repository.save(listaEspera);
        if (guardada.getUrgenciaMedica() != anterior
                && ReglasListaEspera.puedeElegirUrgencia(actor, medicoHabilitado)) {
            auditarUrgencia(guardada, anterior == null ? null : anterior.name(),
                    guardada.getUrgenciaMedica().name(), actor);
        }
        return convertToDTO(guardada);
    }

    /**
     * Marca una entrada como resuelta
     * 
     * @param id Identificador de la entrada
     * @return La entrada actualizada como DTO
     */
    @Transactional
    public ListaEsperaDTO marcarComoResuelta(Long id) {
        Optional<ListaEspera> listaEsperaOpt = buscarPermitida(id);
        if (listaEsperaOpt.isEmpty()) {
            throw new RuntimeException("Entrada de lista de espera no encontrada con ID: " + id);
        }

        ListaEspera listaEspera = listaEsperaOpt.get();
        listaEspera.setEstado("RESUELTA");
        return convertToDTO(repository.save(listaEspera));
    }

    /**
     * Marca una entrada como cubierta
     * 
     * @param id Identificador de la entrada
     * @return La entrada actualizada como DTO
     */
    @Transactional
    public ListaEsperaDTO marcarComoCubierta(Long id) {
        Optional<ListaEspera> listaEsperaOpt = buscarPermitida(id);
        if (listaEsperaOpt.isEmpty()) {
            throw new RuntimeException("Entrada de lista de espera no encontrada con ID: " + id);
        }

        ListaEspera listaEspera = listaEsperaOpt.get();
        listaEspera.setEstado("CUBIERTA");
        return convertToDTO(repository.save(listaEspera));
    }

    /**
     * Elimina una entrada de la lista de espera
     * 
     * @param id Identificador de la entrada a eliminar
     */
    @Transactional
    public void delete(Long id) {
        buscarPermitida(id);
        if (!repository.existsById(id)) {
            throw new RuntimeException("Entrada de lista de espera no encontrada con ID: " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Solicitudes de la ficha de la cuenta autenticada. No pasa por el listado
     * del centro: el paciente no puede ver la cola de otras personas.
     */
    public List<ListaEsperaDTO> deLaCuenta() {
        Integer pacienteId = pacienteAccess.fichaPropia()
                .orElseThrow(PacienteSinFichaException::new);
        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(PacienteSinFichaException::new);
        List<ListaEsperaDTO> propias = repository.findByPaciente(paciente).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        completarPosiciones(propias);
        return propias;
    }

    /**
     * El titular cancela una solicitud propia que sigue pendiente.
     * No la borra: queda CANCELADA para no ofrecer ese lugar de nuevo.
     */
    @Transactional
    public ListaEsperaDTO cancelarPropia(Long id) {
        ListaEspera solicitud = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró la solicitud"));
        Integer pacienteId = solicitud.getPaciente() == null ? null : solicitud.getPaciente().getId();
        pacienteAccess.exigirTitular(pacienteId);
        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new IllegalStateException("Solo se puede cancelar una solicitud pendiente");
        }
        solicitud.setEstado("CANCELADA");
        return convertToDTO(repository.save(solicitud));
    }

    /**
     * Cuenta el total de solicitudes pendientes
     * 
     * @return Número de solicitudes con estado PENDIENTE
     */
    public long contarPendientes() {
        return visibles().stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .count();
    }

    /**
     * Obtiene todas las solicitudes pendientes
     * 
     * @return Lista de solicitudes con estado PENDIENTE
     */
    public List<ListaEsperaDTO> findPendientes() {
        List<ListaEsperaDTO> pendientes = visibles().stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .sorted(OrdenListaEspera.COMPARADOR)
                .map(this::convertToDTO)
                .toList();
        completarPosiciones(pendientes);
        return pendientes;
    }

    /**
     * Obtiene todas las solicitudes urgentes
     * 
     * @return Lista de solicitudes marcadas como urgencia médica
     */
    public List<ListaEsperaDTO> findUrgentes() {
        return visibles().stream()
                .filter(le -> le.getUrgenciaMedica() == ListaEspera.UrgenciaMedica.ALTA
                        || le.getUrgenciaMedica() == ListaEspera.UrgenciaMedica.URGENTE)
                .map(this::convertToDTO)
                .toList();
    }

    // Convertir DTO a entidad
    private ListaEspera convertToEntity(ListaEsperaDTO dto) {
        ListaEspera entity = new ListaEspera();

        // ID solo si existe
        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }

        // Referencias a otras entidades
        entity.setPaciente(pacienteRepository.findById(dto.getPacienteId())
                .orElseThrow(() -> new RuntimeException("Paciente no encontrado: " + dto.getPacienteId())));

        entity.setEspecialidad(especialidadRepository.findById(dto.getEspecialidadId())
                .orElseThrow(() -> new RuntimeException("Especialidad no encontrada: " + dto.getEspecialidadId())));

        entity.setCentroAtencion(centroAtencionRepository.findById(dto.getCentroAtencionId())
                .orElseThrow(
                        () -> new RuntimeException("Centro de atención no encontrado: " + dto.getCentroAtencionId())));

        if (dto.getMedicoPreferidoId() != null) {
            entity.setMedicoPreferido(medicoRepository.findById(dto.getMedicoPreferidoId())
                    .orElseThrow(() -> new RuntimeException("Médico no encontrado: " + dto.getMedicoPreferidoId())));
        }

        // Fechas. La de ingreso se pisa en save/update: acá queda un valor para no dejarla nula.
        entity.setFechaDeseadaDesde(dto.getFechaDeseadaDesde());
        entity.setFechaDeseadaHasta(dto.getFechaDeseadaHasta());
        entity.setFechaSolicitud(LocalDateTime.now(ReglasReservaTurno.ZONA));

        entity.setUrgenciaMedica(ReglasListaEspera.URGENCIA_POR_DEFECTO);
        entity.setEstado(dto.getEstado() != null ? dto.getEstado() : "PENDIENTE");

        return entity;
    }

    /**
     * Busca un paciente en la lista de espera para reasignar a un turno cancelado
     * 
     * @param turnoCancelado El turno que fue cancelado
     * @return true si se encontró y reasignó un paciente, false en caso contrario
     */
    /**
     * Ofrece un turno liberado a la lista de espera.
     *
     * <p>No se ofrece si el inicio es dentro de las próximas
     * {@link ReglasListaEspera#HORAS_MINIMAS_PARA_OFRECER} horas o ya pasó: no da
     * tiempo a avisar y a que la persona llegue. Una ausencia no llama a este
     * método, porque ese horario queda bloqueado.
     */
    @Transactional
    public boolean ofrecerHuecoLiberado(Turno turnoLiberado) {
        if (turnoLiberado == null || turnoLiberado.getFecha() == null || turnoLiberado.getHoraInicio() == null) {
            return false;
        }
        LocalDateTime inicio = LocalDateTime.of(turnoLiberado.getFecha(), turnoLiberado.getHoraInicio());
        LocalDateTime ahora = LocalDateTime.now(ReglasReservaTurno.ZONA);
        if (!ReglasListaEspera.convieneOfrecer(inicio, ahora)) {
            System.out.println("ℹ️ Hueco demasiado próximo, no se ofrece a la lista de espera: " + inicio);
            return false;
        }
        return buscarPacienteParaReasignar(turnoLiberado);
    }

    @Transactional
    public boolean buscarPacienteParaReasignar(Turno turnoCancelado) {
        System.out
                .println("🔍 Buscando paciente en lista de espera para turno cancelado ID: " + turnoCancelado.getId());

        try {
            // 1. Buscar solicitudes pendientes que coincidan con el turno cancelado
            List<ListaEspera> candidatos = buscarCandidatosParaTurno(turnoCancelado);

            if (candidatos.isEmpty()) {
                System.out.println("ℹ️ No se encontraron pacientes en lista de espera que coincidan con el turno");
                return false;
            }

            List<ListaEspera> candidatosOrdenados = candidatos.stream()
                    .sorted(OrdenListaEspera.COMPARADOR)
                    .toList();

            // 3. Intentar reasignar al primer candidato válido
            for (ListaEspera solicitud : candidatosOrdenados) {
                ListaEsperaDTO dto = convertToDTO(solicitud);
                if (intentarReasignarTurno(solicitud, turnoCancelado)) {
                    System.out.println(
                            "✅ Turno reasignado exitosamente al paciente Nombre/Apellido: " + dto.getPacienteNombre()
                                    + " " + dto.getPacienteApellido());
                    return true;
                }
            }

            System.out.println("⚠️ No se pudo reasignar el turno a ningún candidato");
            return false;

        } catch (Exception e) {
            System.err.println("❌ ERROR al buscar paciente para reasignar: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Busca candidatos en la lista de espera que coincidan con el turno disponible
     * ordenados por prioridad (urgencia médica y tiempo de espera)
     */
    private List<ListaEspera> buscarCandidatosParaTurno(Turno turnoDisponible) {
        List<ListaEspera> todas = repository.findAll();
        if (todas == null) {
            return List.of();
        }
        java.util.Map<Integer, List<Turno>> turnosPorPaciente = new java.util.HashMap<>();
        return todas.stream()
                .filter(le -> ReglasListaEspera.esCompatible(le, turnoDisponible, turnosDe(le, turnosPorPaciente)))
                .sorted(OrdenListaEspera.COMPARADOR)
                .toList();
    }

    private List<Turno> turnosDe(ListaEspera solicitud, java.util.Map<Integer, List<Turno>> cache) {
        if (solicitud.getPaciente() == null || solicitud.getPaciente().getId() == null || turnoRepository == null) {
            return List.of();
        }
        return cache.computeIfAbsent(solicitud.getPaciente().getId(), id -> {
            List<Turno> turnos = turnoRepository.findByPaciente_Id(id);
            return turnos == null ? List.of() : turnos;
        });
    }

    /**
     * Busca solicitudes por múltiples criterios con filtros avanzados
     */
    public List<ListaEsperaDTO> buscarConFiltros(
            Integer especialidadId,
            Integer centroAtencionId,
            Integer medicoId,
            String estado,
            String urgenciaMedica,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Integer tiempoEsperaMinimo,
            Integer tiempoEsperaMaximo,
            String ordenamiento) {

        Especialidad especialidad = especialidadId != null ? especialidadRepository.findById(especialidadId)
                .orElseThrow(() -> new RuntimeException("Especialidad no encontrada: " + especialidadId)) : null;

        CentroAtencion centroAtencion = centroAtencionId != null ? centroAtencionRepository.findById(centroAtencionId)
                .orElseThrow(() -> new RuntimeException("Centro de atención no encontrado: " + centroAtencionId))
                : null;

        Medico medico = medicoId != null ? medicoRepository.findById(medicoId)
                .orElseThrow(() -> new RuntimeException("Médico no encontrado: " + medicoId)) : null;

        LocalDate hoy = LocalDate.now();

        List<ListaEsperaDTO> filtradas = visibles().stream()
                .filter(le -> especialidad == null || especialidad.equals(le.getEspecialidad()))
                .filter(le -> centroAtencion == null || centroAtencion.equals(le.getCentroAtencion()))
                .filter(le -> medico == null || medico.equals(le.getMedicoPreferido()))
                .filter(le -> estado == null || estado.equals(le.getEstado()))
                .filter(le -> {
                    if (urgenciaMedica == null)
                        return true;
                    try {
                        ListaEspera.UrgenciaMedica urgenciaEnum = ListaEspera.UrgenciaMedica
                                .valueOf(urgenciaMedica.toUpperCase());
                        return urgenciaEnum.equals(le.getUrgenciaMedica());
                    } catch (IllegalArgumentException e) {
                        // Si el valor del string no coincide con ningún enum, ignorar filtro o
                        // manejarlo según tus reglas
                        System.err.println("⚠️ Valor de urgencia médica inválido: " + urgenciaMedica);
                        return true; // o false, si querés descartar en caso de valor inválido
                    }
                })
                .filter(le -> fechaDesde == null || !le.getFechaSolicitud().toLocalDate().isBefore(fechaDesde))
                .filter(le -> fechaHasta == null || !le.getFechaSolicitud().toLocalDate().isAfter(fechaHasta))
                .filter(le -> {
                    if (tiempoEsperaMinimo == null && tiempoEsperaMaximo == null)
                        return true;
                    long diasEspera = ChronoUnit.DAYS.between(le.getFechaSolicitud().toLocalDate(), hoy);
                    return (tiempoEsperaMinimo == null || diasEspera >= tiempoEsperaMinimo) &&
                            (tiempoEsperaMaximo == null || diasEspera <= tiempoEsperaMaximo);
                })
                .sorted((a, b) -> {
                    if ("TIEMPO_ESPERA".equals(ordenamiento)) {
                        return compararFecha(a, b);
                    }
                    if ("URGENCIA".equals(ordenamiento)) {
                        return Integer.compare(OrdenListaEspera.prioridad(b), OrdenListaEspera.prioridad(a));
                    }
                    return OrdenListaEspera.comparar(a, b);
                })
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        completarPosiciones(filtradas);
        return filtradas;
    }

    private static int compararFecha(ListaEspera a, ListaEspera b) {
        if (a.getFechaSolicitud() == null || b.getFechaSolicitud() == null) {
            return 0;
        }
        return a.getFechaSolicitud().compareTo(b.getFechaSolicitud());
    }

    /**
     * Obtiene el ranking de pacientes en espera por especialidad
     */
    public List<Map<String, Object>> obtenerRankingEspera(Integer especialidadId) {
        Especialidad especialidad = especialidadRepository.findById(especialidadId)
                .orElseThrow(() -> new RuntimeException("Especialidad no encontrada: " + especialidadId));

        List<ListaEspera> solicitudes = visibles().stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .filter(le -> especialidad.equals(le.getEspecialidad()))
                .sorted(OrdenListaEspera.COMPARADOR)
                .toList();

        List<Map<String, Object>> ranking = new ArrayList<>();
        int posicion = 1;

        for (ListaEspera solicitud : solicitudes) {
            Map<String, Object> info = new HashMap<>();
            info.put("posicion", posicion++);
            info.put("solicitudId", solicitud.getId());
            info.put("pacienteId", solicitud.getPaciente().getId());
            info.put("pacienteNombre", solicitud.getPaciente().getNombre() + " " +
                    solicitud.getPaciente().getApellido());
            info.put("urgenciaMedica", solicitud.getUrgenciaMedica().name());
            info.put("urgenciaDescripcion", solicitud.getUrgenciaMedica().getDescripcion());
            info.put("diasEspera",
                    ChronoUnit.DAYS.between(
                            solicitud.getFechaSolicitud().toLocalDate(),
                            LocalDate.now()));
            info.put("fechaSolicitud", solicitud.getFechaSolicitud());
            ranking.add(info);
        }

        return ranking;
    }

    /**
     * Intenta reasignar el turno al paciente de la solicitud en una única
     * transacción
     */
    // ========== MÉTODO: intentarReasignarTurno ==========
    @Transactional
    private boolean intentarReasignarTurno(ListaEspera solicitud, Turno turnoDisponible) {
        try {
            System.out.println("🔄 Intentando reasignar turno al paciente ID: " + solicitud.getPaciente().getId());

            // 1. Verificar que la solicitud sigue pendiente
            if (!"PENDIENTE".equals(solicitud.getEstado())) {
                System.out.println("⚠️ La solicitud ya no está pendiente");
                return false;
            }

            // 2. Crear nuevo turno para el paciente (ahora retorna TurnoDTO)
            TurnoDTO nuevoTurnoDTO = crearNuevoTurno(solicitud, turnoDisponible);
            if (nuevoTurnoDTO == null) {
                return false;
            }

            // 3. Marcar la solicitud como CUBIERTA
            solicitud.setEstado("CUBIERTA");
            solicitud = repository.save(solicitud);

            // 4. Registrar en auditoría (dentro de la misma transacción)
            registrarAuditoriaReasignacion(solicitud, nuevoTurnoDTO, turnoDisponible);

            // 5. Enviar notificación al paciente (fuera de la transacción para evitar
            // problemas)
            try {
                enviarNotificacionReasignacion(solicitud, nuevoTurnoDTO);
            } catch (Exception e) {
                // Log error pero no rollback la transacción
                System.err.println("⚠️ Error al enviar notificación: " + e.getMessage());
            }

            System.out.println("✅ Turno reasignado exitosamente");
            return true;

        } catch (TurnoTomadoException e) {
            System.out.println("ℹ️ El hueco ya fue tomado mientras se reasignaba");
            return false;
        } catch (Exception e) {
            System.err.println("❌ ERROR al intentar reasignar turno: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al reasignar turno: " + e.getMessage());
        }
    }

    /**
     * Crea un nuevo turno basado en el turno cancelado y lo persiste
     */
    // ========== MÉTODO: crearNuevoTurno (ACTUALIZADO) ==========
    private TurnoDTO crearNuevoTurno(ListaEspera solicitud, Turno turnoCancelado) {
        try {
            CentroAtencion centroDelTurno = turnoCancelado.getCentroAtencion();
            if (centroDelTurno == null && turnoCancelado.getStaffMedico() != null) {
                centroDelTurno = turnoCancelado.getStaffMedico().getCentroAtencion();
            }
            if (centroDelTurno != null) {
                suscripcionAccessService.assertOperacion(centroDelTurno.getId(), RecursoSaas.TURNO);
            }
            // Crear la entidad Turno directamente sin pasar por TurnoService
            Turno nuevoTurno = new Turno();

            // Asignar paciente
            nuevoTurno.setPaciente(solicitud.getPaciente());

            // Asignar médico y especialidad
            nuevoTurno.setStaffMedico(turnoCancelado.getStaffMedico());
            nuevoTurno.setMedico(turnoCancelado.getStaffMedico().getMedico());
            nuevoTurno.setEspecialidad(turnoCancelado.getStaffMedico().getEspecialidad());
            nuevoTurno.setCentroAtencion(turnoCancelado.getStaffMedico().getCentroAtencion());

            // Asignar fecha y hora
            nuevoTurno.setFecha(turnoCancelado.getFecha());
            nuevoTurno.setHoraInicio(turnoCancelado.getHoraInicio());
            nuevoTurno.setHoraFin(turnoCancelado.getHoraFin());

            // Asignar consultorio
            nuevoTurno.setConsultorio(turnoCancelado.getConsultorio());

            nuevoTurno.setEstado(EstadoTurno.PROGRAMADO);
            nuevoTurno.setSobreturno(false);
            nuevoTurno.setObservaciones("Reasignado desde lista de espera");

            if (reglasReserva != null) {
                LocalDateTime ahora = LocalDateTime.now(ReglasReservaTurno.ZONA);
                try {
                    reglasReserva.validarReserva(nuevoTurno, false, ahora);
                } catch (RuntimeException e) {
                    System.err.println("No se reasigna el turno: " + e.getMessage());
                    return null;
                }
                if (reglasReserva.naceConfirmado(nuevoTurno, ahora)) {
                    nuevoTurno.setEstado(EstadoTurno.CONFIRMADO);
                }
            }

            Turno turnoGuardado;
            try {
                turnoGuardado = slotGuard != null ? slotGuard.guardar(nuevoTurno) : turnoRepository.save(nuevoTurno);
            } catch (TurnoTomadoException tomado) {
                return null;
            }

            // Convertir a DTO usando el método privado de conversión
            return convertTurnoToDTO(turnoGuardado);

        } catch (Exception e) {
            System.err.println("Error al crear nuevo turno: " + e.getMessage());
            throw new RuntimeException("Error al crear nuevo turno: " + e.getMessage());
        }
    }

    // ========== MÉTODO AUXILIAR: Convertir Turno a TurnoDTO ==========
    private TurnoDTO convertTurnoToDTO(Turno turno) {
        TurnoDTO dto = new TurnoDTO();
        dto.setId(turno.getId());
        dto.setFecha(turno.getFecha());
        dto.setHoraInicio(turno.getHoraInicio());
        dto.setHoraFin(turno.getHoraFin());
        dto.setEstado(turno.getEstado().name());

        // Datos del paciente
        if (turno.getPaciente() != null) {
            dto.setPacienteId(turno.getPaciente().getId());
            dto.setNombrePaciente(turno.getPaciente().getNombre());
            dto.setApellidoPaciente(turno.getPaciente().getApellido());
        }

        // Datos del médico
        if (turno.getStaffMedico() != null) {
            dto.setStaffMedicoId(turno.getStaffMedico().getId());
            dto.setStaffMedicoNombre(turno.getStaffMedico().getMedico().getNombre());
            dto.setStaffMedicoApellido(turno.getStaffMedico().getMedico().getApellido());
            dto.setEspecialidadStaffMedico(turno.getStaffMedico().getEspecialidad().getNombre());
        }

        // Datos del consultorio y centro
        if (turno.getConsultorio() != null) {
            dto.setConsultorioId(turno.getConsultorio().getId());
            dto.setConsultorioNombre(turno.getConsultorio().getNombre());
            if (turno.getConsultorio().getCentroAtencion() != null) {
                dto.setCentroId(turno.getConsultorio().getCentroAtencion().getId());
                dto.setNombreCentro(turno.getConsultorio().getCentroAtencion().getNombre());
            }
        }

        dto.setObservaciones(turno.getObservaciones());

        return dto;
    }

    /**
     * Envía notificación al paciente sobre el nuevo turno asignado
     */
    private void enviarNotificacionReasignacion(ListaEspera solicitud, TurnoDTO nuevoTurnoDTO) {
        try {
            if (notificacionService != null) {
                String fechaTurno = nuevoTurnoDTO.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                String horaTurno = nuevoTurnoDTO.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
                String nombreMedico = nuevoTurnoDTO.getStaffMedicoNombre() + " " +
                        nuevoTurnoDTO.getStaffMedicoApellido();
                String especialidad = nuevoTurnoDTO.getEspecialidadStaffMedico();

                notificacionService.crearNotificacionNuevoTurno(
                        solicitud.getPaciente().getId(),
                        nuevoTurnoDTO.getId(),
                        fechaTurno + " " + horaTurno,
                        especialidad,
                        nombreMedico);

                System.out.println("📧 Notificación enviada al paciente ID: " + solicitud.getPaciente().getId());
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error al enviar notificación: " + e.getMessage());
        }
    }

    /**
     * Registra en auditoría la reasignación del turno con información detallada
     */
    private void registrarAuditoriaReasignacion(ListaEspera solicitud, TurnoDTO nuevoTurnoDTO, Turno turnoDisponible) {
        try {
            if (auditLogService != null) {
                Map<String, Object> detalles = new HashMap<>();
                // Información de la solicitud
                detalles.put("listaEsperaId", solicitud.getId());
                detalles.put("fechaSolicitud", solicitud.getFechaSolicitud());
                detalles.put("diasEspera",
                        ChronoUnit.DAYS.between(solicitud.getFechaSolicitud().toLocalDate(), LocalDate.now()));
                detalles.put("urgenciaMedica", solicitud.getUrgenciaMedica().name());
                detalles.put("urgenciaDescripcion", solicitud.getUrgenciaMedica().getDescripcion());

                // Información del paciente
                detalles.put("pacienteId", solicitud.getPaciente().getId());
                detalles.put("pacienteNombre",
                        solicitud.getPaciente().getNombre() + " " + solicitud.getPaciente().getApellido());

                // Información del turno original y nuevo (usando DTO)
                detalles.put("turnoOriginalId", turnoDisponible.getId());
                detalles.put("nuevoTurnoId", nuevoTurnoDTO.getId());
                detalles.put("fechaTurno", nuevoTurnoDTO.getFecha());
                detalles.put("horaTurno", nuevoTurnoDTO.getHoraInicio());

                // Información de la especialidad y centro (desde DTO)
                detalles.put("especialidad", nuevoTurnoDTO.getEspecialidadStaffMedico());
                detalles.put("centroAtencion", nuevoTurnoDTO.getNombreCentro());
                detalles.put("consultorio", nuevoTurnoDTO.getConsultorioNombre());
                detalles.put("medico", nuevoTurnoDTO.getStaffMedicoNombre() + " " +
                        nuevoTurnoDTO.getStaffMedicoApellido());

                String mensaje = String.format(
                        "Turno reasignado automáticamente. Paciente en espera desde %s (días: %d). %s",
                        solicitud.getFechaSolicitud().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        ChronoUnit.DAYS.between(solicitud.getFechaSolicitud().toLocalDate(), LocalDate.now()),
                        solicitud.getUrgenciaMedica() == ListaEspera.UrgenciaMedica.URGENTE ? "CASO URGENTE" : "");

                auditLogService.logGenericAction(
                        "LISTA_ESPERA",
                        ((long) solicitud.getId()),
                        "TURNO_REASIGNADO",
                        AuditContext.getCurrentUser() != null ? AuditContext.getCurrentUser() : "SISTEMA_AUTOMATICO",
                        "PENDIENTE",
                        "CUBIERTA",
                        null,
                        detalles,
                        mensaje);

                System.out.println("📝 Auditoría detallada registrada para reasignación");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error al registrar auditoría: " + e.getMessage());
            // No lanzar excepción para no afectar la transacción principal
        }
    }

    /**
     * Obtiene estadísticas generales de la lista de espera
     */
    public Map<String, Object> obtenerEstadisticasGenerales() {
        Map<String, Object> estadisticas = new HashMap<>();
        List<ListaEspera> todas = visibles();

        estadisticas.put("totalSolicitudes", todas.size());
        estadisticas.put("pendientes", contarPendientes());

        long urgentes = todas.stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .filter(le -> le.getUrgenciaMedica() == ListaEspera.UrgenciaMedica.ALTA
                        || le.getUrgenciaMedica() == ListaEspera.UrgenciaMedica.URGENTE)
                .count();
        estadisticas.put("urgentes", urgentes);

        // Agregar estadísticas por nivel de urgencia
        Map<String, Long> porNivel = todas.stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .collect(Collectors.groupingBy(
                        le -> le.getUrgenciaMedica().name(),
                        Collectors.counting()));
        estadisticas.put("porNivelUrgencia", porNivel);

        // Tiempo promedio de espera
        OptionalDouble tiempoPromedioEspera = todas.stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .mapToLong(le -> java.time.temporal.ChronoUnit.DAYS.between(
                        le.getFechaSolicitud().toLocalDate(),
                        LocalDate.now()))
                .average();

        estadisticas.put("tiempoPromedioEsperaDias",
                tiempoPromedioEspera.isPresent() ? tiempoPromedioEspera.getAsDouble() : 0);

        return estadisticas;
    }

    /**
     * Verifica si hay turnos disponibles que coincidan con solicitudes pendientes
     */
    public List<Map<String, Object>> verificarCoincidenciasDisponibles(LocalDate fechaInicio, LocalDate fechaFin) {
        List<Map<String, Object>> coincidencias = new ArrayList<>();
        List<ListaEsperaDTO> pendientes = findPendientes();

        pendientes.stream()
                .filter(dto -> esFechaCompatibleDTO(dto, fechaInicio) || esFechaCompatibleDTO(dto, fechaFin))
                .forEach(dto -> {
                    Map<String, Object> coincidencia = new HashMap<>();
                    coincidencia.put("solicitudId", dto.getId());
                    coincidencia.put("pacienteId", dto.getPacienteId());
                    coincidencia.put("pacienteNombre", dto.getPacienteNombre() + " " + dto.getPacienteApellido());
                    coincidencia.put("especialidad", dto.getEspecialidadNombre());
                    coincidencia.put("urgenciaMedica", dto.getUrgenciaMedica());
                    coincidencia.put("tiempoEsperaDias",
                            java.time.temporal.ChronoUnit.DAYS.between(
                                    dto.getFechaSolicitud().toLocalDate(),
                                    LocalDate.now()));
                    coincidencias.add(coincidencia);
                });

        return coincidencias;
    }

    /**
     * Verifica si la fecha coincide con las preferencias del paciente usando el DTO
     */
    private boolean esFechaCompatibleDTO(ListaEsperaDTO dto, LocalDate fecha) {
        if (dto.getFechaDeseadaDesde() == null && dto.getFechaDeseadaHasta() == null) {
            return true; // Sin preferencia de fecha
        }

        if (dto.getFechaDeseadaDesde() != null && fecha.isBefore(dto.getFechaDeseadaDesde())) {
            return false;
        }

        if (dto.getFechaDeseadaHasta() != null && fecha.isAfter(dto.getFechaDeseadaHasta())) {
            return false;
        }

        return true;
    }

    /**
     * Verifica si el médico es compatible con las preferencias del paciente usando
     * el DTO
     */
    private boolean esMedicoCompatibleDTO(ListaEsperaDTO dto, Medico medico) {
        if (dto.getMedicoPreferidoId() == null) {
            return true; // Sin preferencia de médico
        }

        return dto.getMedicoPreferidoId().equals(medico.getId());
    }

    /**
     * Cancela solicitudes que han expirado (fecha deseada hasta ha pasado)
     */
    @Transactional
    public int cancelarSolicitudesExpiradas() {
        LocalDate hoy = LocalDate.now();
        int canceladas = 0;

        List<ListaEsperaDTO> pendientes = visibles().stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .filter(le -> le.getFechaDeseadaHasta() != null)
                .filter(le -> le.getFechaDeseadaHasta().isBefore(hoy))
                .map(this::convertToDTO)
                .toList();

        for (ListaEsperaDTO dto : pendientes) {
            ListaEspera solicitud = repository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + dto.getId()));
            solicitud.setEstado("RESUELTA");
            repository.save(solicitud);

            // Notificar al paciente
            if (notificacionService != null) {
                try {
                    notificacionService.crearNotificacion(
                            dto.getPacienteId(),
                            "Solicitud en Lista de Espera Expirada",
                            "Su solicitud en lista de espera para " + dto.getEspecialidadNombre() +
                                    " ha expirado. Por favor, realice una nueva solicitud si aún requiere atención.",
                            TipoNotificacion.URGENTE,
                            null,
                            "SISTEMA");
                } catch (Exception e) {
                    System.err.println("Error al notificar expiración: " + e.getMessage());
                }
            }

            canceladas++;
        }

        System.out.println("✅ Se cancelaron " + canceladas + " solicitudes expiradas");
        return canceladas;
    }

    /**
     * Envía recordatorios a pacientes con solicitudes pendientes antiguas
     */
    @Transactional
    public int enviarRecordatoriosSolicitudesPendientes(int diasMinimos) {
        LocalDateTime fechaLimite = LocalDateTime.now().minusDays(diasMinimos);
        int recordatoriosEnviados = 0;

        List<ListaEsperaDTO> solicitudesAntiguas = visibles().stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .filter(le -> le.getFechaSolicitud().isBefore(fechaLimite))
                .map(this::convertToDTO)
                .toList();

        for (ListaEsperaDTO dto : solicitudesAntiguas) {
            if (notificacionService != null) {
                try {
                    long diasEspera = java.time.temporal.ChronoUnit.DAYS.between(
                            dto.getFechaSolicitud().toLocalDate(),
                            LocalDate.now());

                    notificacionService.crearNotificacion(
                            dto.getPacienteId(),
                            "Recordatorio: Solicitud en Lista de Espera",
                            "Su solicitud para " + dto.getEspecialidadNombre() +
                                    " lleva " + diasEspera + " días en lista de espera. " +
                                    "Le notificaremos cuando haya un turno disponible.",
                            TipoNotificacion.RECORDATORIO,
                            null,
                            "SISTEMA");

                    recordatoriosEnviados++;
                } catch (Exception e) {
                    System.err.println("Error al enviar recordatorio: " + e.getMessage());
                }
            }
        }

        System.out.println("📧 Se enviaron " + recordatoriosEnviados + " recordatorios");
        return recordatoriosEnviados;
    }

    /**
     * Marca una solicitud como resuelta por otro medio (ej. teléfono)
     * 
     * @param id ID de la solicitud a marcar
     * @return DTO de la solicitud actualizada
     */
    @Transactional
    public ListaEsperaDTO marcarComoResueltaPorOtroMedio(Long id) {
        Optional<ListaEspera> listaEsperaOpt = buscarPermitida(id);
        if (listaEsperaOpt.isEmpty()) {
            throw new RuntimeException("Entrada de lista de espera no encontrada con ID: " + id);
        }

        ListaEspera listaEspera = listaEsperaOpt.get();

        // Verificar que la solicitud esté pendiente
        if (!"PENDIENTE".equals(listaEspera.getEstado())) {
            throw new RuntimeException("La solicitud no está en estado PENDIENTE");
        }

        // Cambiar estado y registrar fecha de resolución
        listaEspera.setEstado("RESUELTA_POR_OTRO_MEDIO");

        // Registrar en auditoría
        try {
            if (auditLogService != null) {
                Map<String, Object> detalles = new HashMap<>();
                detalles.put("fechaResolucion", LocalDateTime.now());
                detalles.put("resolutorId", AuditContext.getCurrentUser());
                detalles.put("pacienteId", listaEspera.getPaciente().getId());
                detalles.put("especialidadId", listaEspera.getEspecialidad().getId());

                auditLogService.logGenericAction(
                        "LISTA_ESPERA",
                        ((long) listaEspera.getId()),
                        "RESOLUCION_MANUAL",
                        AuditContext.getCurrentUser(),
                        "PENDIENTE",
                        "RESUELTA_POR_OTRO_MEDIO",
                        null,
                        detalles,
                        "Solicitud resuelta manualmente por " + AuditContext.getCurrentUser());
            }
        } catch (Exception e) {
            System.err.println("Error al registrar auditoría: " + e.getMessage());
        }

        return convertToDTO(repository.save(listaEspera));
    }

    /**
     * Obtiene estadísticas de demanda insatisfecha agrupada por especialidad
     * 
     * @param periodo Período de tiempo a considerar ("mes_actual",
     *                "ultimo_trimestre")
     * @return Mapa con especialidad como clave y cantidad de solicitudes como valor
     */
    public Map<String, Long> obtenerEstadisticasDemanda(String periodo) {
        LocalDate fechaInicio;
        LocalDate fechaActual = LocalDate.now();

        // Determinar fecha de inicio según el período
        switch (periodo.toLowerCase()) {
            case "mes_actual":
                fechaInicio = fechaActual.withDayOfMonth(1);
                break;
            case "ultimo_trimestre":
                fechaInicio = fechaActual.minusMonths(3);
                break;
            default:
                throw new RuntimeException("Período no válido. Use 'mes_actual' o 'ultimo_trimestre'");
        }

        // Filtrar solicitudes por fecha y estado, y agrupar por especialidad
        return visibles().stream()
                .filter(le -> "PENDIENTE".equals(le.getEstado()))
                .filter(le -> !le.getFechaSolicitud().toLocalDate().isBefore(fechaInicio))
                .collect(Collectors.groupingBy(
                        le -> le.getEspecialidad().getNombre(),
                        Collectors.counting()));
    }

    /**
     * Valida si un paciente puede ser agregado a la lista de espera
     */
    public boolean validarSolicitud(ListaEspera solicitud) {
        // Verificar que el paciente no tenga ya una solicitud pendiente para la misma
        // especialidad, excluyendo la solicitud actual si es una edición
        List<ListaEsperaDTO> solicitudesPendientes = findPendientes();
        boolean tieneSolicitudDuplicada = solicitudesPendientes.stream()
                .filter(dto -> !dto.getId().equals(solicitud.getId())) // Excluir la solicitud actual si es edición
                .anyMatch(dto -> dto.getPacienteId().equals(solicitud.getPaciente().getId()) &&
                        dto.getEspecialidadId().equals(solicitud.getEspecialidad().getId()));

        if (tieneSolicitudDuplicada) {
            throw new RuntimeException("Ya tenés una solicitud pendiente para esta especialidad.");
        }

        // Validar fechas
        if (solicitud.getFechaDeseadaDesde() != null &&
                solicitud.getFechaDeseadaHasta() != null &&
                solicitud.getFechaDeseadaDesde().isAfter(solicitud.getFechaDeseadaHasta())) {
            throw new RuntimeException("La fecha desde no puede ser posterior a la fecha hasta");
        }

        return true;
    }

    private boolean medicoPuedeFijarUrgencia(User user, ListaEspera solicitud) {
        if (user == null || user.getRole() != Role.MEDICO || solicitud == null
                || solicitud.getCentroAtencion() == null || medicoRepository == null
                || staffMedicoRepository == null) {
            return false;
        }
        Medico medico = null;
        if (user.getEmail() != null) {
            medico = medicoRepository.findByEmailIgnoreCase(user.getEmail()).orElse(null);
        }
        if (medico == null && user.getDni() != null) {
            medico = medicoRepository.findByDni(user.getDni()).orElse(null);
        }
        if (medico == null || medico.getId() == null) {
            return false;
        }
        List<StaffMedico> lugares = staffMedicoRepository.findByMedicoId(medico.getId());
        if (lugares == null) {
            return false;
        }
        Integer centroId = solicitud.getCentroAtencion().getId();
        boolean atiendeAhi = lugares.stream().anyMatch(staff -> staff.getCentroAtencion() != null
                && centroId.equals(staff.getCentroAtencion().getId()));
        if (!atiendeAhi) {
            return false;
        }
        if (solicitud.getMedicoPreferido() != null && solicitud.getMedicoPreferido().getId() != null
                && !medico.getId().equals(solicitud.getMedicoPreferido().getId())) {
            return false;
        }
        if (solicitud.getMedicoPreferido() != null && medico.getId().equals(solicitud.getMedicoPreferido().getId())) {
            return true;
        }
        if (solicitud.getPaciente() == null || solicitud.getPaciente().getId() == null || turnoRepository == null) {
            return false;
        }
        List<Turno> turnos = turnoRepository.findByPaciente_Id(solicitud.getPaciente().getId());
        if (turnos == null) {
            return false;
        }
        for (Turno turno : turnos) {
            if (turno.getEstado() == EstadoTurno.CANCELADO) {
                continue;
            }
            Medico delTurno = turno.getMedico();
            if (delTurno == null && turno.getStaffMedico() != null) {
                delTurno = turno.getStaffMedico().getMedico();
            }
            if (delTurno != null && medico.getId().equals(delTurno.getId())) {
                return true;
            }
        }
        return false;
    }

    private void auditarUrgencia(ListaEspera solicitud, String anterior, String nueva, User actor) {
        if (auditLogService == null || solicitud == null || solicitud.getId() == null) {
            return;
        }
        String quien = actor != null && actor.getEmail() != null ? actor.getEmail() : "SISTEMA";
        Map<String, Object> detalles = new HashMap<>();
        detalles.put("urgencia", nueva);
        if (solicitud.getPaciente() != null) {
            detalles.put("pacienteId", solicitud.getPaciente().getId());
        }
        auditLogService.logGenericAction(
                "LISTA_ESPERA",
                solicitud.getId(),
                anterior == null ? "URGENCIA_ASIGNADA" : "CAMBIO_URGENCIA",
                quien,
                anterior,
                nueva,
                null,
                detalles,
                "Urgencia de la lista de espera definida por el personal");
    }

    private void completarPosiciones(List<ListaEsperaDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<ListaEspera> pendientes = repository.findByEstado("PENDIENTE");
        if (pendientes == null) {
            return;
        }
        Map<String, List<ListaEspera>> grupos = new HashMap<>();
        for (ListaEspera solicitud : pendientes) {
            if (solicitud.getId() == null) {
                continue;
            }
            grupos.computeIfAbsent(claveCola(solicitud), clave -> new ArrayList<>()).add(solicitud);
        }
        Map<Long, Integer> posiciones = new HashMap<>();
        for (List<ListaEspera> grupo : grupos.values()) {
            grupo.sort(OrdenListaEspera.COMPARADOR);
            int puesto = 1;
            for (ListaEspera solicitud : grupo) {
                posiciones.put(solicitud.getId(), puesto++);
            }
        }
        for (ListaEsperaDTO dto : dtos) {
            if (dto.getId() != null && "PENDIENTE".equals(dto.getEstado())) {
                dto.setPosicion(posiciones.get(dto.getId()));
            }
        }
    }

    private static String claveCola(ListaEspera solicitud) {
        Integer especialidad = solicitud.getEspecialidad() == null ? null : solicitud.getEspecialidad().getId();
        Integer centro = solicitud.getCentroAtencion() == null ? null : solicitud.getCentroAtencion().getId();
        return especialidad + ":" + centro;
    }

    // Convertir entidad a DTO
    private ListaEsperaDTO convertToDTO(ListaEspera entity) {
        ListaEsperaDTO dto = new ListaEsperaDTO();

        dto.setId(entity.getId());

        // Información del paciente
        dto.setPacienteId(entity.getPaciente().getId());
        dto.setPacienteNombre(entity.getPaciente().getNombre());
        dto.setPacienteApellido(entity.getPaciente().getApellido());
        dto.setPacienteDni(entity.getPaciente().getDni());
        dto.setPacienteTelefono(entity.getPaciente().getTelefono());
        dto.setPacienteEmail(entity.getPaciente().getEmail());

        // Información de la especialidad
        dto.setEspecialidadId(entity.getEspecialidad().getId());
        dto.setEspecialidadNombre(entity.getEspecialidad().getNombre());

        // Información del médico preferido
        if (entity.getMedicoPreferido() != null) {
            dto.setMedicoPreferidoId(entity.getMedicoPreferido().getId());
            dto.setMedicoPreferidoNombre(entity.getMedicoPreferido().getNombre() + " " +
                    entity.getMedicoPreferido().getApellido());
        }

        // Información del centro de atención
        dto.setCentroAtencionId(entity.getCentroAtencion().getId());
        dto.setCentroAtencionNombre(entity.getCentroAtencion().getNombre());

        // Fechas
        dto.setFechaDeseadaDesde(entity.getFechaDeseadaDesde());
        dto.setFechaDeseadaHasta(entity.getFechaDeseadaHasta());
        dto.setFechaSolicitud(entity.getFechaSolicitud());

        // Estado y urgencia
        dto.setUrgenciaMedica(entity.getUrgenciaMedica().name());
        dto.setEstado(entity.getEstado());

        // Calcular días en espera
        long diasEnEspera = ChronoUnit.DAYS.between(
                entity.getFechaSolicitud().toLocalDate(),
                LocalDateTime.now().toLocalDate());
        dto.setDiasEnEspera(diasEnEspera);

        return dto;
    }
}