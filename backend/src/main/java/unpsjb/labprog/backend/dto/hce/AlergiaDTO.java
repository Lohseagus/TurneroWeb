package unpsjb.labprog.backend.dto.hce;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AlergiaDTO {
    private Long id;
    private String sustancia;
    private String reaccion;
    private String criticidad;
    private String systemUri;
    private String code;
    private boolean activa = true;
}
