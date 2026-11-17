package unpsjb.labprog.backend.business.receta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "recetas")
@Getter
@Setter
public class RecetaProperties {

    /** local o renapdis. */
    private String plataforma = "local";

    private int maxItems = 3;

    private int vigenciaDias = 30;

    private int ordenMaxItems = 8;

    private String publicBaseUrl = "http://localhost:4200";

    private int verificacionPorMinuto = 30;
}
