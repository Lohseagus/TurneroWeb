package unpsjb.labprog.backend.dto.hce;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TurnoContextoHceDTO {
    private Integer turnoId;
    private Integer pacienteId;
    private String pacienteNombre;
    private String pacienteApellido;
    private Long dni;
    private Integer centroId;
    private String centroNombre;
    private Integer especialidadId;
    private String especialidadNombre;
    private Integer medicoId;
    private Long consultaId;
}
