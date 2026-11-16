package unpsjb.labprog.backend.business.hce;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.AccesoHistoriaClinicaRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.AccesoHistoriaClinica;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;

@Service
public class HceAuditoriaService {

    private final AccesoHistoriaClinicaRepository repository;

    public HceAuditoriaService(AccesoHistoriaClinicaRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Integer pacienteId, AccionAuditoriaHce accion, String recursoTipo, String recursoId,
            Integer centroId, String detalle) {
        if (pacienteId == null || accion == null) {
            return;
        }
        AccesoHistoriaClinica acceso = new AccesoHistoriaClinica();
        acceso.setPacienteId(pacienteId);
        acceso.setAccion(accion);
        acceso.setRecursoTipo(recursoTipo);
        acceso.setRecursoId(recursoId);
        acceso.setCentroId(centroId);
        acceso.setDetalle(recortar(detalle));
        acceso.setOcurridoEn(LocalDateTime.now());
        User user = AuditContext.getCurrentUserObject();
        if (user != null) {
            acceso.setActorEmail(user.getEmail());
            acceso.setActorRol(user.getRole() != null ? user.getRole().name() : null);
        }
        repository.save(acceso);
    }

    private String recortar(String detalle) {
        if (detalle == null) {
            return null;
        }
        String limpio = detalle.replaceAll("\\s+", " ").trim();
        return limpio.length() <= 300 ? limpio : limpio.substring(0, 300);
    }
}
