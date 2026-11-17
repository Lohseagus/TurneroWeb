package unpsjb.labprog.backend.dto.hce;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MetadataHceDTO {
    private Integer pacienteId;
    private boolean tienePerfil;
    private boolean comparteEntreCentros;
    private int cantidadConsultas;
    private int cantidadBorradores;
    private int cantidadCerradas;
    private int cantidadAdjuntos;
    private int cantidadEnmiendas;
    private int cantidadRecetas;
    private int cantidadOrdenes;
    private LocalDateTime ultimaActividad;
}
