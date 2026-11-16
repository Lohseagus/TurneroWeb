package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VacunaDTO {
    private Long id;
    private String nombre;
    private LocalDate fechaAplicacion;
    private String dosis;
    private String lote;
}
