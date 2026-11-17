package unpsjb.labprog.backend.dto.receta;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ItemPublicoDTO {
    private String nombre;
    private String concentracion;
    private String forma;
    private Integer cantidad;
    private String cantidadEnLetras;
    private boolean noSustituir;
    private String codigo;
}
