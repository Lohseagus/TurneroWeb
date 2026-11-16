package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdjuntoDTO {
    private Long id;
    private Integer pacienteId;
    private Long consultaId;
    private Integer centroId;
    private String nombreOriginal;
    private String contentType;
    private long tamanioBytes;
    private String tipo;
    private String subidoPor;
    private LocalDateTime creadoEn;
}
