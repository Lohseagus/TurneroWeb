package unpsjb.labprog.backend.dto.hce;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DiagnosticoDTO {
    private Long id;
    private String systemUri;
    private String code;
    private String display;
    private String tipo;
}
