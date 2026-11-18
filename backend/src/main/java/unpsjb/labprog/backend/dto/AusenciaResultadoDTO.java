package unpsjb.labprog.backend.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class AusenciaResultadoDTO {

    private ConfiguracionExcepcionalDTO ausencia;
    private List<TurnoAfectadoDTO> cancelados = new ArrayList<>();
    private List<TurnoAfectadoDTO> requierenGestionManual = new ArrayList<>();
    private String mensaje;
}
