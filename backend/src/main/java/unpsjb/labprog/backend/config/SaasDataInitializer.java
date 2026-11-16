package unpsjb.labprog.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import unpsjb.labprog.backend.saas.SaasService;

/**
 * Siembra planes, IVA, prueba y complementos. Los centros que ya existían
 * reciben una suscripción activa heredada para que producción no quede bloqueada.
 * No pisa precios ni días de prueba si ya fueron editados.
 */
@Component
public class SaasDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SaasDataInitializer.class);

    private final SaasService saasService;

    public SaasDataInitializer(SaasService saasService) {
        this.saasService = saasService;
    }

    @Override
    public void run(String... args) {
        saasService.asegurarDatosIniciales();
        logger.info("Catálogo SaaS listo. Centros previos quedaron con suscripción heredada activa.");
    }
}
