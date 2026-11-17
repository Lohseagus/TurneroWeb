package unpsjb.labprog.backend.dto.receta;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrdenEstudioItemWriteDTO {
    private String nombrePractica;
    private String systemUri;
    private String code;
}
