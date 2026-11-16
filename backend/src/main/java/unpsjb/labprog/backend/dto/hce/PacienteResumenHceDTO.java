package unpsjb.labprog.backend.dto.hce;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PacienteResumenHceDTO {
    private Integer id;
    private String nombre;
    private String apellido;
    private Long dni;
}
