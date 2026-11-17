package unpsjb.labprog.backend.dto.receta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecetaWriteDTO {
    private Long consultaId;
    private String tipo;
    private String diagnosticoCode;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String numeroAfiliado;
    private String obraSocialNombre;
    private LocalDate fechaVencimiento;
    private List<RecetaItemWriteDTO> items = new ArrayList<>();
}
