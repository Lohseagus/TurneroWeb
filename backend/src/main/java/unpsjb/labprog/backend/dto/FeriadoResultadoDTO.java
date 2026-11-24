package unpsjb.labprog.backend.dto;

/**
 * Alta o edición de un feriado, con cuántos turnos se cancelaron.
 */
public class FeriadoResultadoDTO {

    private ConfiguracionExcepcionalDTO feriado;
    private int turnosCancelados;

    public FeriadoResultadoDTO() {
    }

    public FeriadoResultadoDTO(ConfiguracionExcepcionalDTO feriado, int turnosCancelados) {
        this.feriado = feriado;
        this.turnosCancelados = turnosCancelados;
    }

    public ConfiguracionExcepcionalDTO getFeriado() {
        return feriado;
    }

    public void setFeriado(ConfiguracionExcepcionalDTO feriado) {
        this.feriado = feriado;
    }

    public int getTurnosCancelados() {
        return turnosCancelados;
    }

    public void setTurnosCancelados(int turnosCancelados) {
        this.turnosCancelados = turnosCancelados;
    }
}
