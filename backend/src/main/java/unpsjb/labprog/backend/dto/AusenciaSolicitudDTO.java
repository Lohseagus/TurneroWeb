package unpsjb.labprog.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * Alta de una ausencia. Las fechas viajan como yyyy-MM-dd para el input de fecha.
 * centroAtencionId va en el cuerpo para que el filtro de solo lectura resuelva la sede
 * cuando la cuenta del médico no tiene centro propio.
 */
@Data
public class AusenciaSolicitudDTO {

    private Integer staffMedicoId;

    private Integer centroAtencionId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaInicio;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaFin;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaFin;

    private String motivo;
}
