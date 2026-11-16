package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TimelineItemDTO {
    private String tipo;
    private String recursoId;
    private Long consultaId;
    private LocalDateTime fecha;
    private Integer centroId;
    private String centroNombre;
    private Integer medicoId;
    private String medicoNombre;
    private Integer especialidadId;
    private String especialidadNombre;
    private String estado;
    private String resumen;
}
