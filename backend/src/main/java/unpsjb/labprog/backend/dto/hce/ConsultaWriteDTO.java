package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConsultaWriteDTO {
    private Integer pacienteId;
    private Integer turnoId;
    private Integer centroId;
    private Integer especialidadId;
    private String motivoConsulta;
    private String anamnesis;
    private String examenFisico;
    private String indicaciones;
    private String plan;
    private LocalDate proximoControl;
    private Integer tensionSistolica;
    private Integer tensionDiastolica;
    private Integer frecuenciaCardiaca;
    private Double temperatura;
    private Double pesoKg;
    private Double tallaCm;
    private List<DiagnosticoDTO> diagnosticos = new ArrayList<>();
}
