package unpsjb.labprog.backend.business.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.dto.ConfiguracionExcepcionalDTO;
import unpsjb.labprog.backend.dto.FeriadoResultadoDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

/**
 * Feriados de un centro o de todo el sistema.
 *
 * <p>Un feriado global (sin centro) lo carga, edita y borra solo un superadmin.
 * El administrador y el operador cargan feriados de su centro y no tocan los
 * de otro centro ni los globales.
 *
 * <p>Al crear el feriado se cancelan los turnos activos de ese día
 * (programados, confirmados y reagendados) dentro del alcance. Se avisa al
 * paciente como en una ausencia, con enlace para reservar de nuevo, y no se
 * ofrece el hueco a la lista de espera: el día queda cerrado.
 */
@Service
public class FeriadoService {

    static final List<EstadoTurno> ESTADOS_CANCELABLES = List.of(
            EstadoTurno.PROGRAMADO, EstadoTurno.CONFIRMADO, EstadoTurno.REAGENDADO);

    @Autowired
    private ConfiguracionExcepcionalRepository repository;

    @Autowired
    private CentroAtencionRepository centroAtencionRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private CentroAccessGuard centroAccess;

    @Autowired
    private AuditLogService auditLogService;

    @Transactional
    public FeriadoResultadoDTO crear(LocalDate fecha, String descripcion, Integer centroPedido) {
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha del feriado es obligatoria.");
        }
        String texto = texto(descripcion);
        Integer centroId = alcanceParaAlta(centroPedido);
        if (yaExiste(fecha, centroId, null)) {
            throw new IllegalArgumentException("Ya hay un feriado activo para ese día en ese alcance.");
        }
        ConfiguracionExcepcional feriado = new ConfiguracionExcepcional();
        feriado.setFecha(fecha);
        feriado.setTipo(ConfiguracionExcepcional.TipoExcepcion.FERIADO);
        feriado.setDescripcion(texto);
        feriado.setActivo(true);
        feriado.setCentroAtencion(centroDe(centroId));
        feriado = repository.save(feriado);
        int cancelados = cancelarTurnos(fecha, centroId, texto);
        auditar(feriado, "CREATE", null, "ACTIVO", "Feriado cargado");
        return new FeriadoResultadoDTO(ConfiguracionExcepcionalDTO.fromEntity(feriado), cancelados);
    }

    @Transactional
    public FeriadoResultadoDTO actualizar(Integer id, LocalDate fecha, String descripcion) {
        ConfiguracionExcepcional feriado = exigirFeriado(id);
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha del feriado es obligatoria.");
        }
        String texto = texto(descripcion);
        Integer centroId = centroDe(feriado);
        if (yaExiste(fecha, centroId, feriado.getId())) {
            throw new IllegalArgumentException("Ya hay un feriado activo para ese día en ese alcance.");
        }
        LocalDate anterior = feriado.getFecha();
        feriado.setFecha(fecha);
        feriado.setDescripcion(texto);
        feriado = repository.save(feriado);
        int cancelados = 0;
        if (anterior == null || !anterior.equals(fecha)) {
            cancelados = cancelarTurnos(fecha, centroId, texto);
        }
        auditar(feriado, "UPDATE", anterior == null ? null : anterior.toString(), fecha.toString(), "Feriado actualizado");
        return new FeriadoResultadoDTO(ConfiguracionExcepcionalDTO.fromEntity(feriado), cancelados);
    }

    @Transactional
    public void eliminar(Integer id) {
        ConfiguracionExcepcional feriado = exigirFeriado(id);
        feriado.setActivo(false);
        repository.save(feriado);
        auditar(feriado, "DELETE", "ACTIVO", "INACTIVO", "Feriado dado de baja");
    }

    public int contarAfectados(LocalDate fecha, Integer centroPedido) {
        if (fecha == null) {
            return 0;
        }
        Integer centroId = alcanceParaAlta(centroPedido);
        return afectados(fecha, centroId).size();
    }

    private ConfiguracionExcepcional exigirFeriado(Integer id) {
        ConfiguracionExcepcional feriado = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el feriado"));
        if (feriado.getTipo() != ConfiguracionExcepcional.TipoExcepcion.FERIADO || !feriado.isActivo()) {
            throw new IllegalArgumentException("No se encontró el feriado");
        }
        exigirPuedeTocar(feriado);
        return feriado;
    }

    /**
     * @return id de centro, o null si el feriado es de todo el sistema
     */
    private Integer alcanceParaAlta(Integer centroPedido) {
        User user = usuario();
        if (user.getRole() == Role.SUPERADMIN) {
            return centroPedido;
        }
        if (user.getRole() != Role.ADMINISTRADOR && user.getRole() != Role.OPERADOR) {
            throw new AccessDeniedException("Solo el personal del centro o un superadmin puede cargar feriados.");
        }
        if (centroPedido == null && centroAccess != null) {
            centroPedido = centroAccess.centroParaFiltro(null);
        } else if (centroAccess != null) {
            centroAccess.exigirCentro(centroPedido);
        }
        if (centroPedido == null) {
            throw new AccessDeniedException("Elegí el centro del feriado. Los feriados de todo el sistema los carga un superadmin.");
        }
        return centroPedido;
    }

    private void exigirPuedeTocar(ConfiguracionExcepcional feriado) {
        User user = usuario();
        Integer centroId = centroDe(feriado);
        if (centroId == null) {
            if (user.getRole() != Role.SUPERADMIN) {
                throw new AccessDeniedException("Los feriados de todo el sistema los carga un superadmin.");
            }
            return;
        }
        if (user.getRole() == Role.SUPERADMIN) {
            return;
        }
        if (user.getRole() == Role.ADMINISTRADOR || user.getRole() == Role.OPERADOR) {
            if (centroAccess != null) {
                centroAccess.exigirCentro(centroId);
            }
            return;
        }
        throw new AccessDeniedException("No podés modificar este feriado.");
    }

    private User usuario() {
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() == null) {
            throw new AccessDeniedException("Tenés que iniciar sesión para cargar un feriado.");
        }
        return user;
    }

    private int cancelarTurnos(LocalDate fecha, Integer centroId, String descripcion) {
        if (fecha.isBefore(LocalDate.now(AusenciaMedicoService.ZONA))) {
            return 0;
        }
        String motivo = "Feriado: " + descripcion;
        String actor = actor();
        int cancelados = 0;
        for (Turno turno : afectados(fecha, centroId)) {
            try {
                turnoService.cancelarTurno(turno.getId(), motivo, actor, false, "AUSENCIA", true);
                cancelados++;
            } catch (RuntimeException ex) {
                System.err.println("No se pudo cancelar el turno " + turno.getId() + " por el feriado: " + ex.getMessage());
            }
        }
        return cancelados;
    }

    private List<Turno> afectados(LocalDate fecha, Integer centroId) {
        List<Turno> delDia = turnoRepository == null ? List.of() : turnoRepository.findByFecha(fecha);
        if (delDia == null) {
            return List.of();
        }
        return delDia.stream()
                .filter(turno -> turno.getEstado() != null && ESTADOS_CANCELABLES.contains(turno.getEstado()))
                .filter(turno -> centroId == null || centroId.equals(centroDelTurno(turno)))
                .toList();
    }

    private static Integer centroDelTurno(Turno turno) {
        if (turno.getCentroAtencion() != null) {
            return turno.getCentroAtencion().getId();
        }
        if (turno.getStaffMedico() != null && turno.getStaffMedico().getCentroAtencion() != null) {
            return turno.getStaffMedico().getCentroAtencion().getId();
        }
        if (turno.getConsultorio() != null && turno.getConsultorio().getCentroAtencion() != null) {
            return turno.getConsultorio().getCentroAtencion().getId();
        }
        return null;
    }

    private boolean yaExiste(LocalDate fecha, Integer centroId, Integer ignorarId) {
        List<ConfiguracionExcepcional> delDia = repository.findByTipoAndFechaAndActivoTrue(
                ConfiguracionExcepcional.TipoExcepcion.FERIADO, fecha);
        if (delDia == null) {
            return false;
        }
        return delDia.stream()
                .filter(ConfiguracionExcepcional::isActivo)
                .filter(existente -> ignorarId == null || !ignorarId.equals(existente.getId()))
                .anyMatch(existente -> mismoAlcance(centroDe(existente), centroId));
    }

    private static boolean mismoAlcance(Integer a, Integer b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.equals(b);
    }

    private CentroAtencion centroDe(Integer centroId) {
        if (centroId == null) {
            return null;
        }
        return centroAtencionRepository.findById(centroId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el centro"));
    }

    private static Integer centroDe(ConfiguracionExcepcional feriado) {
        return feriado.getCentroAtencion() == null ? null : feriado.getCentroAtencion().getId();
    }

    private static String texto(String descripcion) {
        String limpio = descripcion == null ? "" : descripcion.trim();
        if (limpio.length() < 3) {
            throw new IllegalArgumentException("El feriado necesita una descripción.");
        }
        return limpio.length() > 500 ? limpio.substring(0, 500) : limpio;
    }

    private String actor() {
        User user = AuditContext.getCurrentUserObject();
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        String nombre = AuditContext.getCurrentUser();
        return nombre == null || nombre.isBlank() ? "SISTEMA" : nombre;
    }

    private void auditar(ConfiguracionExcepcional feriado, String accion, String antes, String despues, String motivo) {
        if (auditLogService == null || feriado.getId() == null) {
            return;
        }
        Map<String, Object> datos = new HashMap<>();
        datos.put("fecha", feriado.getFecha() == null ? null : feriado.getFecha().toString());
        datos.put("centroId", centroDe(feriado));
        datos.put("global", feriado.getCentroAtencion() == null);
        datos.put("descripcion", feriado.getDescripcion());
        auditLogService.logGenericAction("FERIADO", feriado.getId().longValue(), accion, actor(),
                antes, despues, null, datos, motivo);
    }
}
