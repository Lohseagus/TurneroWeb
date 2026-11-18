package unpsjb.labprog.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Data;
import unpsjb.labprog.backend.model.Turno;

@Data
public class TurnoAfectadoDTO {

    private Integer id;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String estado;
    private String pacienteNombre;
    private String pacienteApellido;

    public static TurnoAfectadoDTO from(Turno turno) {
        TurnoAfectadoDTO dto = new TurnoAfectadoDTO();
        dto.setId(turno.getId());
        dto.setFecha(turno.getFecha());
        dto.setHoraInicio(turno.getHoraInicio());
        dto.setHoraFin(turno.getHoraFin());
        dto.setEstado(turno.getEstado() != null ? turno.getEstado().name() : null);
        if (turno.getPaciente() != null) {
            dto.setPacienteNombre(turno.getPaciente().getNombre());
            dto.setPacienteApellido(turno.getPaciente().getApellido());
        }
        return dto;
    }
}
