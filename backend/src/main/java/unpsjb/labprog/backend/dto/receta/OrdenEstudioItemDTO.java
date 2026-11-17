package unpsjb.labprog.backend.dto.receta;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrdenEstudioItemDTO {
    private Long id;
    private int orden;
    private String nombrePractica;
    private String systemUri;
    private String code;
}
