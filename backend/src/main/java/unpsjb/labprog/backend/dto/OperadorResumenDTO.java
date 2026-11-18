package unpsjb.labprog.backend.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * KPIs del centro del operador para la solapa Resumen de Mi Centro.
 * Los conteos ya vienen filtrados por los centros de la cuenta.
 */
@Getter
@Setter
public class OperadorResumenDTO {

    private int turnosHoy;
    private int pendientesConfirmar;
    private int listaEspera;
    private int cancelacionesSemana;
    private List<TurnoCompacto> proximosHoy = new ArrayList<>();

    @Getter
    @Setter
    public static class TurnoCompacto {
        private Integer id;
        private String hora;
        private String paciente;
        private String medico;
        private String especialidad;
        private String estado;
    }
}
