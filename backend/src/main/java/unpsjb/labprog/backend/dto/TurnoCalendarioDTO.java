package unpsjb.labprog.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import unpsjb.labprog.backend.model.EstadoTurno;

/**
 * Turno liviano para la grilla del mes. Una sola consulta lo arma:
 * no hidrata el grafo completo de la entidad.
 */
@Getter
@Setter
@NoArgsConstructor
public class TurnoCalendarioDTO {

    private Integer id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaInicio;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaFin;
    private String estado;
    private Integer pacienteId;
    private String pacienteNombre;
    private String pacienteApellido;
    private Integer medicoId;
    private String medicoNombre;
    private String medicoApellido;
    private Integer staffMedicoId;
    private Integer especialidadId;
    private String especialidadNombre;
    private Integer consultorioId;
    private String consultorioNombre;
    private Integer centroId;
    private String centroNombre;
    private Boolean asistio;

    public TurnoCalendarioDTO(
            Integer id,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            EstadoTurno estado,
            Integer pacienteId,
            String pacienteNombre,
            String pacienteApellido,
            Integer medicoId,
            String medicoNombre,
            String medicoApellido,
            Integer staffMedicoId,
            Integer especialidadId,
            String especialidadNombre,
            Integer consultorioId,
            String consultorioNombre,
            Integer centroId,
            String centroNombre,
            Boolean asistio) {
        this.id = id;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.estado = estado == null ? null : estado.name();
        this.pacienteId = pacienteId;
        this.pacienteNombre = pacienteNombre;
        this.pacienteApellido = pacienteApellido;
        this.medicoId = medicoId;
        this.medicoNombre = medicoNombre;
        this.medicoApellido = medicoApellido;
        this.staffMedicoId = staffMedicoId;
        this.especialidadId = especialidadId;
        this.especialidadNombre = especialidadNombre;
        this.consultorioId = consultorioId;
        this.consultorioNombre = consultorioNombre;
        this.centroId = centroId;
        this.centroNombre = centroNombre;
        this.asistio = asistio;
    }
}
