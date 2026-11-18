package unpsjb.labprog.backend.saas;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Destino mínimo para probar el filtro sin levantar los presenters de dominio.
 */
@RestController
public class ProbeEscrituraController {

    @RequestMapping(value = {
            "/api/probe",
            "/api/turno/asignar",
            "/api/turno/search",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/change-password",
            "/api/saas/registro",
            "/api/saas/cotizar",
            "/api/saas/webhooks/mercadopago",
            "/api/saas/mi-suscripcion/pagar",
            "/api/saas/admin/suscripciones/1/suspender",
            "/api/recetas/verificar/token-publico",
            "/api/ordenes-estudio/verificar/token-publico",
            "/api/deep-links/validate"
    }, method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE
    })
    public Map<String, Object> ok() {
        return Map.of("status_code", 200, "data", "ok");
    }

    @RequestMapping(value = "/api/turno/{id}/cancelar", method = RequestMethod.PUT)
    public Map<String, Object> cancelar() {
        return Map.of("status_code", 200, "data", "ok");
    }
}
