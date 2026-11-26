package unpsjb.labprog.backend.saas;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un controller (o un método) que solo se usa si el plan del centro incluye la función.
 * Lo aplica {@link PlanFeatureInterceptor}, y solo con {@code saas.enforcement.enabled} prendido.
 * El método pisa a la clase.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface RequiereFeature {

    FeaturePlan value();
}
