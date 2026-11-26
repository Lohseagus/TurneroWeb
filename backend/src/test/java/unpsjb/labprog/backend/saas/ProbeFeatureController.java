package unpsjb.labprog.backend.saas;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Destino mínimo para probar la compuerta de funciones por plan.
 */
@RestController
@RequiereFeature(FeaturePlan.AUDITORIA)
public class ProbeFeatureController {

    @GetMapping("/api/probe-feature")
    public Map<String, Object> deLaClase() {
        return Map.of("status_code", 200, "data", "ok");
    }

    @RequiereFeature(FeaturePlan.REPORTES_CONSOLIDADOS)
    @PostMapping("/api/probe-feature/consolidado")
    public Map<String, Object> delMetodo() {
        return Map.of("status_code", 200, "data", "ok");
    }
}
