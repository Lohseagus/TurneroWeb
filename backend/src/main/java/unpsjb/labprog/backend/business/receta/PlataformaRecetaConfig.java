package unpsjb.labprog.backend.business.receta;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlataformaRecetaConfig {

    @Bean
    public PlataformaRecetaElectronica plataformaRecetaElectronica(RecetaProperties properties) {
        if (properties.getPlataforma() != null && properties.getPlataforma().equalsIgnoreCase("renapdis")) {
            return new RepositorioNacionalStub();
        }
        return new LocalPlataforma();
    }
}
