package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConsultaDTO {
    private Long id;
    private Integer pacienteId;
    private String pacienteNombre;
    private String pacienteApellido;
    private Long dni;
    private String identificadorFederado;
    private Integer turnoId;
    private Integer medicoId;
    private String medicoNombre;
    private String matricula;
    private String refepsId;
    private Integer centroId;
    private String centroNombre;
    private Integer especialidadId;
    private String especialidadNombre;
    private String estado;
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
    private Double imc;
    private List<DiagnosticoDTO> diagnosticos = new ArrayList<>();
    private List<EnmiendaDTO> enmiendas = new ArrayList<>();
    private LocalDateTime creadaEn;
    private LocalDateTime actualizadaEn;
    private LocalDateTime cerradaEn;
}
