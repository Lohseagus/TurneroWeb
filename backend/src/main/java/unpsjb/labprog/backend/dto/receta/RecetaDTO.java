package unpsjb.labprog.backend.dto.receta;

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
public class RecetaDTO {
    private Long id;
    private Long consultaId;
    private Integer pacienteId;
    private Integer medicoId;
    private Integer centroId;
    private String tipo;
    private String estado;
    private String identificador;
    private String cuir;
    private String codigoVerificacion;
    private String urlVerificacion;
    private String hashContenido;
    private boolean integridad;
    private String tipoFirma;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private LocalDateTime firmadoEn;
    private String diagnosticoSystem;
    private String diagnosticoCode;
    private String diagnosticoDisplay;
    private String pacienteNombre;
    private String pacienteApellido;
    private Long pacienteDni;
    private String pacienteSexo;
    private LocalDate pacienteNacimiento;
    private String obraSocialNombre;
    private String numeroAfiliado;
    private String prescriptorNombre;
    private String prescriptorApellido;
    private String matricula;
    private String matriculaNacional;
    private String matriculaProvincial;
    private String refepsId;
    private String especialidadNombre;
    private String centroNombre;
    private String plataforma;
    private String motivoAnulacion;
    private String anuladoPor;
    private LocalDateTime anuladoEn;
    private LocalDateTime creadaEn;
    private List<RecetaItemDTO> items = new ArrayList<>();
}
