package unpsjb.labprog.backend.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Turnos y feriados de un rango (la grilla visible del mes). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarioMesDTO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate desde;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate hasta;
    private List<TurnoCalendarioDTO> turnos;
    private List<FeriadoCalendarioDTO> feriados;
}
