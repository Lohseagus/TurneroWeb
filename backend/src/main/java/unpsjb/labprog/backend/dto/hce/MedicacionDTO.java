package unpsjb.labprog.backend.dto.hce;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MedicacionDTO {
    private Long id;
    private String farmaco;
    private String dosis;
    private String frecuencia;
    private String systemUri;
    private String code;
    private boolean activa = true;
}
