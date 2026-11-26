package unpsjb.labprog.backend.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import unpsjb.labprog.backend.saas.PlanFeatureGate;
import unpsjb.labprog.backend.saas.PlanFeatureInterceptor;

/**
 * Registra la compuerta de funciones por plan. Usa ObjectProvider para que los
 * tests de controller (WebMvcTest) no necesiten la capa SaaS: sin compuerta, no corta.
 */
@Configuration
public class PlanFeatureWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<PlanFeatureGate> gateProvider;

    public PlanFeatureWebConfig(ObjectProvider<PlanFeatureGate> gateProvider) {
        this.gateProvider = gateProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PlanFeatureInterceptor(gateProvider)).addPathPatterns("/api/**");
    }
}
