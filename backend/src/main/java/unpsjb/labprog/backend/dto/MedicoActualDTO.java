package unpsjb.labprog.backend.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ficha del médico que corresponde al usuario autenticado, más sus vínculos de staff.
 * La arma el backend a partir del principal del JWT: el cliente no elige el id.
 */
@Getter
@Setter
@NoArgsConstructor
public class MedicoActualDTO {

    private Integer id;
    private String nombre;
    private String apellido;
    private String dni;
    private String email;
    private String telefono;
    private String matricula;
    private String refepsId;
    private Set<EspecialidadDTO> especialidades;
    /** Primer staff del médico. Null si todavía no está asociado a un centro. */
    private Integer staffMedicoId;
    private List<StaffResumen> staff = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class StaffResumen {
        private Integer id;
        private Integer centroId;
        private String centroNombre;
        private Integer especialidadId;
        private String especialidadNombre;
    }
}
