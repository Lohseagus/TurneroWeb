package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EnmiendaDTO {
    private Long id;
    private Long consultaId;
    private String texto;
    private String motivo;
    private Integer medicoId;
    private String medicoNombre;
    private String matricula;
    private LocalDateTime creadaEn;
}
