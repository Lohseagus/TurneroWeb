package unpsjb.labprog.backend.saas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interruptor de los límites SaaS. Apagado, el estado de la suscripción se
 * calcula y se guarda igual, pero no se rechaza ninguna escritura ni se muestra
 * el modo solo lectura. Los cupos, la compuerta de features y el selector de
 * sede tienen que consultar {@link #habilitado()} antes de aplicar su regla.
 */
@Component
public class SaasEnforcement {

    private static final Logger logger = LoggerFactory.getLogger(SaasEnforcement.class);

    private final boolean habilitado;

    public SaasEnforcement(@Value("${saas.enforcement.enabled:false}") boolean habilitado) {
        this.habilitado = habilitado;
        logger.info("Límites SaaS {}: saas.enforcement.enabled={}",
                habilitado ? "activos" : "apagados", habilitado);
    }

    public boolean habilitado() {
        return habilitado;
    }
}
