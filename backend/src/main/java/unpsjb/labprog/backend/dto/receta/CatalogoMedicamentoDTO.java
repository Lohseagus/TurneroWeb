package unpsjb.labprog.backend.dto.receta;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoMedicamentoDTO {
    private Long id;
    private String nombreGenerico;
    private String concentracion;
    private String formaFarmaceutica;
    private String via;
    private String tipo;
    private String lista;
}
