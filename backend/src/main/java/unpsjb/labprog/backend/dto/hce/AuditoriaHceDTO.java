package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuditoriaHceDTO {
    private Long id;
    private String actorEmail;
    private String actorRol;
    private String accion;
    private String recursoTipo;
    private String recursoId;
    private Integer centroId;
    private String detalle;
    private LocalDateTime ocurridoEn;
}
