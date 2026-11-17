package unpsjb.labprog.backend.dto.receta;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmitirDTO {
    private LocalDate fechaVencimiento;
}
