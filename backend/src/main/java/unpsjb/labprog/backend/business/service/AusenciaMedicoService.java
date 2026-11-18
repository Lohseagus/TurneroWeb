package unpsjb.labprog.backend.business.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.dto.AnticipacionAusenciaDTO;
import unpsjb.labprog.backend.dto.AusenciaResultadoDTO;
import unpsjb.labprog.backend.dto.AusenciaSolicitudDTO;
import unpsjb.labprog.backend.dto.ConfiguracionExcepcionalDTO;
import unpsjb.labprog.backend.dto.TurnoAfectadoDTO;
import unpsjb.labprog.backend.model.AuditLog;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.ReglaSuscripcionException;
import unpsjb.labprog.backend.saas.ResultadoEscritura;
import unpsjb.labprog.backend.saas.SaasEnforcement;
import unpsjb.labprog.backend.saas.SuscripcionAccessService;

/**
 * Ausencia de un profesional en un rango de fechas. Cancela los turnos futuros
 * del rango y no ofrece el hueco a la lista de espera: el paciente saca un turno nuevo.
 */
@Service
public class AusenciaMedicoService {

    public static final String PREFIJO_MOTIVO = "Ausencia del médico: ";
    public static final String TIPO_DEEP_LINK = "AUSENCIA";
    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    public static final int MOTIVO_URGENCIA_MINIMO = 5;

    private static final int RANGO_MAXIMO_DIAS = 366;

    @Autowired
    private ConfiguracionExcepcionalRepository repository;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private SuscripcionAccessService suscripcionAccessService;

    @Autowired
    private SaasEnforcement saasEnforcement;

    @Autowired
    private ConfiguracionService configuracionService;

    @Autowired
    private AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public AusenciaResultadoDTO previsualizar(AusenciaSolicitudDTO solicitud) {
        StaffMedico staff = staffDe(solicitud);
        Rango rango = validar(solicitud, staff, false, true, null);
        return armar(null, turnoRepository.findAfectablesPorAusencia(
                staff.getId(), rango.inicio, rango.fin,
                List.of(EstadoTurno.PROGRAMADO, EstadoTurno.CONFIRMADO)), rango, false);
    }

    @Transactional
    public AusenciaResultadoDTO confirmar(AusenciaSolicitudDTO solicitud, String performedBy) {
        StaffMedico staff = staffDe(solicitud);
        exigirEscritura(centroDe(staff));
        Rango rango = validar(solicitud, staff, true, true, null);

        ConfiguracionExcepcional ausencia = new ConfiguracionExcepcional();
        ausencia.setFecha(rango.inicio);
        ausencia.setFechaFin(rango.fin);
        ausencia.setTipo(ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO);
        ausencia.setDescripcion(texto(solicitud.getMotivo()));
        ausencia.setHoraInicio(rango.horaInicio);
        ausencia.setHoraFin(rango.horaFin);
        ausencia.setStaffMedico(staff);
        ausencia.setCentroAtencion(staff.getCentroAtencion());
        ausencia.setActivo(true);
        ausencia.setUrgente(rango.urgente);
        ausencia = repository.save(ausencia);
        auditar(ausencia, AuditLog.Actions.CREATE, performedBy);

        List<Turno> afectados = turnoRepository.findAfectablesPorAusencia(
                staff.getId(), rango.inicio, rango.fin,
                List.of(EstadoTurno.PROGRAMADO, EstadoTurno.CONFIRMADO));
        AusenciaResultadoDTO resultado = armar(ausencia, afectados, rango, true);
        String motivo = PREFIJO_MOTIVO + texto(solicitud.getMotivo());
        String actor = performedBy == null || performedBy.isBlank() ? "SISTEMA" : performedBy;
        for (TurnoAfectadoDTO cancelable : new ArrayList<>(resultado.getCancelados())) {
            turnoService.cancelarTurno(cancelable.getId(), motivo, actor, false, TIPO_DEEP_LINK);
        }
        resultado.setAusencia(ConfiguracionExcepcionalDTO.fromEntity(ausencia));
        resultado.setMensaje(mensaje(resultado, true));
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionExcepcionalDTO> listarDeStaff(List<Integer> staffIds) {
        if (staffIds == null || staffIds.isEmpty()) {
            return List.of();
        }
        List<ConfiguracionExcepcionalDTO> ausencias = new ArrayList<>();
        for (Integer staffId : staffIds) {
            ausencias.addAll(repository
                    .findByTipoAndStaffMedico_IdAndActivoTrueOrderByFechaDesc(
                            ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO, staffId)
                    .stream()
                    .map(ConfiguracionExcepcionalDTO::fromEntity)
                    .toList());
        }
        return ausencias;
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionExcepcionalDTO> listarDeCentro(Integer centroId) {
        return repository
                .findByTipoAndCentroAtencion_IdAndActivoTrueOrderByFechaDesc(
                        ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO, centroId)
                .stream()
                .map(ConfiguracionExcepcionalDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionExcepcionalDTO> listarTodas() {
        return repository
                .findByTipoAndActivoTrueOrderByFechaDesc(ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO)
                .stream()
                .map(ConfiguracionExcepcionalDTO::fromEntity)
                .toList();
    }

    @Transactional
    public void eliminar(Integer id) {
        ConfiguracionExcepcional ausencia = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la ausencia"));
        if (ausencia.getTipo() != ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO || !ausencia.isActivo()) {
            throw new IllegalArgumentException("No se encontró la ausencia");
        }
        exigirEscritura(ausencia.getCentroAtencion() != null
                ? ausencia.getCentroAtencion().getId()
                : centroDe(ausencia.getStaffMedico()));
        ausencia.setActivo(false);
        repository.save(ausencia);
    }

    @Transactional
    public ConfiguracionExcepcionalDTO actualizar(Integer id, AusenciaSolicitudDTO solicitud, String performedBy) {
        ConfiguracionExcepcional ausencia = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la ausencia"));
        if (ausencia.getTipo() != ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO || !ausencia.isActivo()) {
            throw new IllegalArgumentException("No se encontró la ausencia");
        }
        StaffMedico staff = staffDe(solicitud);
        exigirEscritura(centroDe(staff));
        LocalDate inicioAnterior = ausencia.getFecha();
        boolean mueveInicio = inicioAnterior == null || !inicioAnterior.equals(solicitud.getFechaInicio());
        Rango rango = validar(solicitud, staff, true, mueveInicio, ausencia.getId());
        ausencia.setFecha(rango.inicio);
        ausencia.setFechaFin(rango.fin);
        ausencia.setDescripcion(texto(solicitud.getMotivo()));
        ausencia.setHoraInicio(rango.horaInicio);
        ausencia.setHoraFin(rango.horaFin);
        ausencia.setStaffMedico(staff);
        ausencia.setCentroAtencion(staff.getCentroAtencion());
        ausencia.setUrgente(mueveInicio ? rango.urgente : ausencia.isUrgente());
        ausencia = repository.save(ausencia);
        auditar(ausencia, AuditLog.Actions.UPDATE, performedBy);
        return ConfiguracionExcepcionalDTO.fromEntity(ausencia);
    }

    @Transactional(readOnly = true)
    public AnticipacionAusenciaDTO anticipacion(Integer centroId) {
        int dias = diasAnticipacion(centroId);
        AnticipacionAusenciaDTO dto = new AnticipacionAusenciaDTO();
        dto.setDias(dias);
        dto.setFechaMinima(LocalDate.now(ZONA).plusDays(Math.max(dias, 0)));
        return dto;
    }

    private StaffMedico staffDe(AusenciaSolicitudDTO solicitud) {
        if (solicitud == null || solicitud.getStaffMedicoId() == null) {
            throw new IllegalArgumentException("Tenés que elegir al profesional");
        }
        return staffMedicoRepository.findById(solicitud.getStaffMedicoId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el profesional"));
    }

    private Rango validar(AusenciaSolicitudDTO solicitud, StaffMedico staff, boolean rechazarSolapadas,
            boolean exigirAnticipacion, Integer ignorarId) {
        if (solicitud.getFechaInicio() == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria");
        }
        LocalDate fin = solicitud.getFechaFin() != null ? solicitud.getFechaFin() : solicitud.getFechaInicio();
        if (fin.isBefore(solicitud.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio");
        }
        if (ChronoUnit.DAYS.between(solicitud.getFechaInicio(), fin) > RANGO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("El rango no puede superar un año");
        }
        LocalTime horaInicio = solicitud.getHoraInicio();
        LocalTime horaFin = solicitud.getHoraFin();
        if ((horaInicio == null) != (horaFin == null)) {
            throw new IllegalArgumentException("Indicá la hora de inicio y la de fin, o ninguna");
        }
        if (horaInicio != null && !horaFin.isAfter(horaInicio)) {
            throw new IllegalArgumentException("La hora de fin tiene que ser posterior a la de inicio");
        }
        if (solicitud.getMotivo() != null && solicitud.getMotivo().length() > 500) {
            throw new IllegalArgumentException("El motivo no puede superar los 500 caracteres");
        }
        if (staff.getCentroAtencion() == null) {
            throw new IllegalArgumentException("El profesional no tiene un centro de atención");
        }
        if (rechazarSolapadas && seSolapa(staff.getId(), solicitud.getFechaInicio(), fin, horaInicio, horaFin, ignorarId)) {
            throw new IllegalArgumentException("Ya hay una ausencia cargada que se superpone con esas fechas");
        }
        boolean urgente = exigirAnticipacion && validarAnticipacion(solicitud, staff);
        return new Rango(solicitud.getFechaInicio(), fin, horaInicio, horaFin, urgente);
    }

    /**
     * @return true si quedó marcada como urgencia
     */
    private boolean validarAnticipacion(AusenciaSolicitudDTO solicitud, StaffMedico staff) {
        int dias = diasAnticipacion(centroDe(staff));
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate minima = hoy.plusDays(Math.max(dias, 0));
        boolean llegaATiempo = !solicitud.getFechaInicio().isBefore(minima);
        Role rol = rolActual();
        boolean pideUrgencia = Boolean.TRUE.equals(solicitud.getUrgente());
        if (rol == Role.MEDICO && pideUrgencia && !llegaATiempo) {
            throw new IllegalArgumentException(mensajeAnticipacion(dias));
        }
        if (llegaATiempo) {
            return false;
        }
        if (!puedeUrgencia(rol) || !pideUrgencia) {
            throw new IllegalArgumentException(mensajeAnticipacion(dias));
        }
        String motivo = solicitud.getMotivo() == null ? "" : solicitud.getMotivo().trim();
        if (motivo.length() < MOTIVO_URGENCIA_MINIMO) {
            throw new IllegalArgumentException(
                    "La urgencia necesita un motivo de al menos " + MOTIVO_URGENCIA_MINIMO + " caracteres.");
        }
        return true;
    }

    public static String mensajeAnticipacion(int dias) {
        return "Las ausencias se cargan con al menos " + dias
                + " días de anticipación. Para una urgencia, pedile a la administración del centro que la cargue.";
    }

    private int diasAnticipacion(Integer centroId) {
        if (configuracionService == null) {
            return 7;
        }
        return configuracionService.getDiasMaxNoConfirm(centroId);
    }

    private static boolean puedeUrgencia(Role rol) {
        return rol == Role.ADMINISTRADOR || rol == Role.OPERADOR || rol == Role.SUPERADMIN;
    }

    private static Role rolActual() {
        User user = AuditContext.getCurrentUserObject();
        return user == null ? null : user.getRole();
    }

    private void auditar(ConfiguracionExcepcional ausencia, String accion, String performedBy) {
        if (auditLogService == null || ausencia.getId() == null) {
            return;
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("urgente", ausencia.isUrgente());
        datos.put("motivo", ausencia.getDescripcion());
        datos.put("fechaInicio", ausencia.getFecha() == null ? null : ausencia.getFecha().toString());
        datos.put("fechaFin", ausencia.getFechaFin() == null ? null : ausencia.getFechaFin().toString());
        datos.put("staffMedicoId", ausencia.getStaffMedico() == null ? null : ausencia.getStaffMedico().getId());
        String actor = performedBy == null || performedBy.isBlank() ? "SISTEMA" : performedBy;
        auditLogService.logGenericAction("AUSENCIA_MEDICO", ausencia.getId().longValue(), accion, actor,
                null, ausencia.isUrgente() ? "URGENTE" : "PROGRAMADA", null, datos, ausencia.getDescripcion());
    }

    private boolean seSolapa(Integer staffId, LocalDate inicio, LocalDate fin, LocalTime horaInicio, LocalTime horaFin,
            Integer ignorarId) {
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fin)) {
            LocalDate dia = cursor;
            boolean solapa = repository.findAusenciasVigentes(staffId, dia,
                    ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO)
                    .stream()
                    .filter(existente -> ignorarId == null || !ignorarId.equals(existente.getId()))
                    .anyMatch(existente -> horasSolapan(existente, horaInicio, horaFin));
            if (solapa) {
                return true;
            }
            cursor = cursor.plusDays(1);
        }
        return false;
    }

    private boolean horasSolapan(ConfiguracionExcepcional existente, LocalTime horaInicio, LocalTime horaFin) {
        if (existente.getHoraInicio() == null || existente.getHoraFin() == null
                || horaInicio == null || horaFin == null) {
            return true;
        }
        return horaInicio.isBefore(existente.getHoraFin()) && horaFin.isAfter(existente.getHoraInicio());
    }

    private AusenciaResultadoDTO armar(ConfiguracionExcepcional ausencia, List<Turno> turnos, Rango rango,
            boolean confirmado) {
        AusenciaResultadoDTO resultado = new AusenciaResultadoDTO();
        if (ausencia != null) {
            resultado.setAusencia(ConfiguracionExcepcionalDTO.fromEntity(ausencia));
        }
        LocalDate hoy = LocalDate.now(ZONA);
        for (Turno turno : turnos) {
            if (!caeEnVentana(turno, rango)) {
                continue;
            }
            TurnoAfectadoDTO dto = TurnoAfectadoDTO.from(turno);
            if (turno.getFecha() != null && turno.getFecha().isAfter(hoy)) {
                resultado.getCancelados().add(dto);
            } else {
                resultado.getRequierenGestionManual().add(dto);
            }
        }
        resultado.setMensaje(mensaje(resultado, confirmado));
        return resultado;
    }

    private boolean caeEnVentana(Turno turno, Rango rango) {
        if (rango.horaInicio == null || rango.horaFin == null) {
            return true;
        }
        if (turno.getHoraInicio() == null) {
            return true;
        }
        LocalTime finTurno = turno.getHoraFin() != null ? turno.getHoraFin() : turno.getHoraInicio();
        return turno.getHoraInicio().isBefore(rango.horaFin) && finTurno.isAfter(rango.horaInicio);
    }

    private String mensaje(AusenciaResultadoDTO resultado, boolean confirmado) {
        int cancelados = resultado.getCancelados().size();
        int manuales = resultado.getRequierenGestionManual().size();
        String verbo = confirmado ? "Se cancelaron" : "Se van a cancelar";
        String texto = verbo + " " + cancelados + " turno" + (cancelados == 1 ? "" : "s")
                + " futuro" + (cancelados == 1 ? "" : "s") + ".";
        if (manuales > 0) {
            texto += " " + manuales + " turno" + (manuales == 1 ? "" : "s")
                    + " de hoy o de días anteriores no se puede"
                    + (manuales == 1 ? "" : "n")
                    + " cancelar solo: hay que resolverlo a mano.";
        }
        return texto;
    }

    /**
     * Misma regla que el filtro HTTP: con el interruptor apagado no corta nada.
     */
    private void exigirEscritura(Integer centroId) {
        if (saasEnforcement == null || !saasEnforcement.habilitado()) {
            return;
        }
        ResultadoEscritura resultado = suscripcionAccessService.evaluarEscritura(centroId);
        if (resultado != null && !resultado.permite()) {
            throw new ReglaSuscripcionException(resultado.mensaje());
        }
    }

    private static Integer centroDe(StaffMedico staff) {
        return staff.getCentroAtencion() == null ? null : staff.getCentroAtencion().getId();
    }

    private static String texto(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return "sin detalle adicional";
        }
        return motivo.trim();
    }

    private record Rango(LocalDate inicio, LocalDate fin, LocalTime horaInicio, LocalTime horaFin, boolean urgente) {
    }
}
