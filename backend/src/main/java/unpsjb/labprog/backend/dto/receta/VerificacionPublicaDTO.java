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
public class VerificacionPublicaDTO {
    private String tipoDocumento;
    private String estado;
    private boolean vigente;
    private boolean integridad;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private String prescriptor;
    private String matricula;
    private String matriculaNacional;
    private String matriculaProvincial;
    private String pacienteIniciales;
    private String dniParcial;
    private String mensaje;
    private List<ItemPublicoDTO> items = new ArrayList<>();
}
