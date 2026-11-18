package unpsjb.labprog.backend.presenter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import unpsjb.labprog.backend.Response;
import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.service.AusenciaMedicoService;
import unpsjb.labprog.backend.business.service.CentroAccessGuard;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.dto.AusenciaResultadoDTO;
import unpsjb.labprog.backend.dto.AusenciaSolicitudDTO;
import unpsjb.labprog.backend.dto.ConfiguracionExcepcionalDTO;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.ReglaSuscripcionException;

@RestController
@RequestMapping("/api/agenda/ausencias")
public class AusenciaPresenter {

    @Autowired
    private AusenciaMedicoService ausenciaService;

    @Autowired
    private CentroAccessGuard centroAccess;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;

    @Autowired
    private MedicoRepository medicoRepository;

    @Autowired
    private ConfiguracionExcepcionalRepository configuracionRepository;

    @GetMapping
    public ResponseEntity<Object> listar(@RequestParam(required = false) Integer staffMedicoId,
            @RequestParam(required = false) Integer centroId) {
        try {
            List<ConfiguracionExcepcionalDTO> ausencias;
            User user = AuditContext.getCurrentUserObject();
            if (user != null && user.getRole() == Role.MEDICO) {
                if (staffMedicoId != null) {
                    autorizarStaff(staffMedicoId);
                    ausencias = ausenciaService.listarDeStaff(List.of(staffMedicoId));
                } else {
                    ausencias = ausenciaService.listarDeStaff(staffDelMedico(user));
                }
            } else if (staffMedicoId != null) {
                autorizarStaff(staffMedicoId);
                ausencias = ausenciaService.listarDeStaff(List.of(staffMedicoId));
            } else if (centroAccess.centrosPermitidos() == null && centroId == null) {
                ausencias = ausenciaService.listarTodas();
            } else {
                ausencias = ausenciaService.listarDeCentro(centroAccess.centroParaFiltro(centroId));
            }
            return Response.ok(ausencias, "Ausencias obtenidas");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<Object> previsualizar(
            @RequestParam Integer staffMedicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime horaFin,
            @RequestParam(required = false) String motivo) {
        try {
            autorizarStaff(staffMedicoId);
            AusenciaSolicitudDTO solicitud = new AusenciaSolicitudDTO();
            solicitud.setStaffMedicoId(staffMedicoId);
            solicitud.setFechaInicio(fechaInicio);
            solicitud.setFechaFin(fechaFin);
            solicitud.setHoraInicio(horaInicio);
            solicitud.setHoraFin(horaFin);
            solicitud.setMotivo(motivo);
            return Response.ok(ausenciaService.previsualizar(solicitud), "Turnos afectados");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Object> confirmar(@RequestBody AusenciaSolicitudDTO solicitud) {
        try {
            if (solicitud == null || solicitud.getStaffMedicoId() == null) {
                return Response.error(null, "Tenés que elegir al profesional");
            }
            autorizarStaff(solicitud.getStaffMedicoId());
            completarCentro(solicitud);
            AusenciaResultadoDTO resultado = ausenciaService.confirmar(solicitud, AuditContext.getCurrentUser());
            return Response.ok(resultado, resultado.getMensaje());
        } catch (AccessDeniedException e) {
            throw e;
        } catch (ReglaSuscripcionException e) {
            return Response.forbidden(e.getMessage());
        } catch (IllegalArgumentException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/{id}/baja")
    public ResponseEntity<Object> baja(@PathVariable Integer id, @RequestBody(required = false) AusenciaSolicitudDTO cuerpo) {
        return eliminarAusencia(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> eliminar(@PathVariable Integer id) {
        return eliminarAusencia(id);
    }

    private ResponseEntity<Object> eliminarAusencia(Integer id) {
        try {
            ConfiguracionExcepcional ausencia = configuracionRepository.findById(id).orElse(null);
            if (ausencia == null || !ausencia.isActivo()
                    || ausencia.getTipo() != ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO) {
                return Response.notFound("No se encontró la ausencia");
            }
            if (ausencia.getStaffMedico() == null || ausencia.getStaffMedico().getId() == null) {
                return Response.error(null, "La ausencia no tiene un profesional asociado");
            }
            autorizarStaff(ausencia.getStaffMedico().getId());
            ausenciaService.eliminar(id);
            return Response.ok(null, "Ausencia eliminada");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (ReglaSuscripcionException e) {
            return Response.forbidden(e.getMessage());
        } catch (IllegalArgumentException e) {
            return Response.error(null, e.getMessage());
        }
    }

    private void completarCentro(AusenciaSolicitudDTO solicitud) {
        if (solicitud.getCentroAtencionId() != null) {
            return;
        }
        staffMedicoRepository.findById(solicitud.getStaffMedicoId()).ifPresent(staff -> {
            if (staff.getCentroAtencion() != null) {
                solicitud.setCentroAtencionId(staff.getCentroAtencion().getId());
            }
        });
    }

    private void autorizarStaff(Integer staffMedicoId) {
        centroAccess.exigirStaffMedico(staffMedicoId);
        User user = AuditContext.getCurrentUserObject();
        if (user == null || user.getRole() != Role.MEDICO) {
            return;
        }
        StaffMedico staff = staffMedicoRepository.findById(staffMedicoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el profesional"));
        Medico medico = medicoRepository.findByEmailIgnoreCase(
                user.getEmail() == null ? "" : user.getEmail().trim())
                .orElseThrow(() -> new AccessDeniedException(
                        "No se encontró tu ficha de médico"));
        if (staff.getMedico() == null || staff.getMedico().getId() == null
                || !staff.getMedico().getId().equals(medico.getId())) {
            throw new AccessDeniedException("Solo podés cargar ausencias de tu propia agenda");
        }
    }

    private List<Integer> staffDelMedico(User user) {
        Medico medico = medicoRepository.findByEmailIgnoreCase(
                user.getEmail() == null ? "" : user.getEmail().trim())
                .orElseThrow(() -> new AccessDeniedException("No se encontró tu ficha de médico"));
        return staffMedicoRepository.findByMedicoId(medico.getId()).stream()
                .map(StaffMedico::getId)
                .toList();
    }
}
