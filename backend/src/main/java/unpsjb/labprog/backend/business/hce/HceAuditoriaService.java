package unpsjb.labprog.backend.business.hce;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import unpsjb.labprog.backend.business.repository.AccesoHistoriaClinicaRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.AccesoHistoriaClinica;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;

/**
 * La auditoría corre en una transacción aparte y un fallo (por ejemplo un
 * CHECK viejo sobre {@code hce_acceso.accion}) no se propaga. Si se deja
 * subir, Postgres aborta el trabajo y una lectura de recetas termina en 500.
 */
@Service
public class HceAuditoriaService {

    private static final Logger logger = LoggerFactory.getLogger(HceAuditoriaService.class);

    private final AccesoHistoriaClinicaRepository repository;
    private final TransactionTemplate requiereNueva;

    public HceAuditoriaService(AccesoHistoriaClinicaRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiereNueva = template;
    }

    public void registrar(Integer pacienteId, AccionAuditoriaHce accion, String recursoTipo, String recursoId,
            Integer centroId, String detalle) {
        if (pacienteId == null || accion == null) {
            return;
        }
        AccesoHistoriaClinica acceso = base(pacienteId, accion, recursoTipo, recursoId, centroId, detalle);
        User user = AuditContext.getCurrentUserObject();
        if (user != null) {
            acceso.setActorEmail(user.getEmail());
            acceso.setActorRol(user.getRole() != null ? user.getRole().name() : null);
        }
        guardar(acceso);
    }

    public void registrarSistema(Integer pacienteId, AccionAuditoriaHce accion, String recursoTipo, String recursoId,
            Integer centroId, String detalle, String actorEmail, String actorRol) {
        if (pacienteId == null || accion == null) {
            return;
        }
        AccesoHistoriaClinica acceso = base(pacienteId, accion, recursoTipo, recursoId, centroId, detalle);
        acceso.setActorEmail(actorEmail);
        acceso.setActorRol(actorRol);
        guardar(acceso);
    }

    private AccesoHistoriaClinica base(Integer pacienteId, AccionAuditoriaHce accion, String recursoTipo,
            String recursoId, Integer centroId, String detalle) {
        AccesoHistoriaClinica acceso = new AccesoHistoriaClinica();
        acceso.setPacienteId(pacienteId);
        acceso.setAccion(accion);
        acceso.setRecursoTipo(recursoTipo);
        acceso.setRecursoId(recursoId);
        acceso.setCentroId(centroId);
        acceso.setDetalle(recortar(detalle));
        acceso.setOcurridoEn(LocalDateTime.now());
        return acceso;
    }

    private void guardar(AccesoHistoriaClinica acceso) {
        try {
            requiereNueva.executeWithoutResult(status -> repository.save(acceso));
        } catch (RuntimeException ex) {
            logger.warn("No se registró la auditoría HCE {} del paciente {}: {}",
                    acceso.getAccion(), acceso.getPacienteId(), ex.toString());
            logger.debug("Fallo de auditoría HCE", ex);
        }
    }

    private String recortar(String detalle) {
        if (detalle == null) {
            return null;
        }
        String limpio = detalle.replaceAll("\\s+", " ").trim();
        return limpio.length() <= 300 ? limpio : limpio.substring(0, 300);
    }
}
