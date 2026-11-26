package unpsjb.labprog.backend.saas;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Corta con 403 los handlers marcados con {@link RequiereFeature} cuando el plan del
 * centro no incluye la función. La decisión (bandera, rol, plan) es de {@link PlanFeatureGate}.
 */
public class PlanFeatureInterceptor implements HandlerInterceptor {

    private final ObjectProvider<PlanFeatureGate> gateProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanFeatureInterceptor(ObjectProvider<PlanFeatureGate> gateProvider) {
        this.gateProvider = gateProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        FeaturePlan feature = featureDe(handler);
        if (feature == null) {
            return true;
        }
        PlanFeatureGate gate = gateProvider == null ? null : gateProvider.getIfAvailable();
        if (gate == null) {
            return true;
        }
        PlanFeatureGate.Resultado resultado = gate.evaluar(feature);
        if (resultado == null || resultado.permite()) {
            return true;
        }
        rechazar(response, resultado);
        return false;
    }

    static FeaturePlan featureDe(Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return null;
        }
        RequiereFeature enMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), RequiereFeature.class);
        if (enMetodo != null) {
            return enMetodo.value();
        }
        RequiereFeature enClase = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), RequiereFeature.class);
        return enClase == null ? null : enClase.value();
    }

    private void rechazar(HttpServletResponse response, PlanFeatureGate.Resultado resultado) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("feature", resultado.feature().name());
        data.put("planRequerido", resultado.planRequerido());
        data.put("planRequeridoNombre", PlanFeatures.nombrePlan(resultado.planRequerido()));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status_code", HttpStatus.FORBIDDEN.value());
        cuerpo.put("status_text", resultado.mensaje());
        cuerpo.put("code", PlanFeatureGate.CODIGO_FEATURE_NO_INCLUIDA);
        cuerpo.put("data", data);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(cuerpo));
    }
}
