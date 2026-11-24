package unpsjb.labprog.backend.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Feriado que cae en el rango visible. centroAtencionId null = de todo el sistema. */
@Getter
@Setter
@NoArgsConstructor
public class FeriadoCalendarioDTO {

    private Integer id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaFin;
    private String descripcion;
    private Integer centroAtencionId;
    private String centroAtencionNombre;
}
