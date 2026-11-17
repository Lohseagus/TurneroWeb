package unpsjb.labprog.backend.dto.receta;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecetaItemWriteDTO {
    private String nombreGenerico;
    private String marcaComercial;
    private String concentracion;
    private String formaFarmaceutica;
    private String presentacion;
    private Integer cantidad;
    private String cantidadEnLetras;
    private String dosis;
    private String frecuencia;
    private String duracion;
    private String via;
    private String indicaciones;
    private boolean noSustituir;
    private String justificacionNoSustituir;
    private String lista;
    private Long catalogoId;
}
