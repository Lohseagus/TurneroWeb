package unpsjb.labprog.backend.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class AnticipacionAusenciaDTO {

    private int dias;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaMinima;
}
