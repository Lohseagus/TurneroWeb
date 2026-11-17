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
public class OrdenEstudioWriteDTO {
    private Long consultaId;
    private String diagnosticoCode;
    private String urgencia;
    private String indicacionClinica;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String numeroAfiliado;
    private String obraSocialNombre;
    private LocalDate fechaVencimiento;
    private List<OrdenEstudioItemWriteDTO> items = new ArrayList<>();
}
