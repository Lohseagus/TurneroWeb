package unpsjb.labprog.backend.dto.hce;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PerfilClinicoDTO {
    private Integer pacienteId;
    private Long dni;
    private String identificadorFederado;
    private String nombre;
    private String apellido;
    private String antecedentesPersonales;
    private String antecedentesFamiliares;
    private String antecedentesQuirurgicos;
    private String grupoSanguineo;
    private String habitos;
    private boolean comparteEntreCentros;
    private List<AlergiaDTO> alergias = new ArrayList<>();
    private List<MedicacionDTO> medicaciones = new ArrayList<>();
    private List<VacunaDTO> vacunas = new ArrayList<>();
}
