package unpsjb.labprog.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Suscriptor SaaS. Dueño de uno o más {@link CentroAtencion}.
 * El aislamiento entre centros se mantiene: cada usuario operativo sigue atado a un centro.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Organizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre;

    private String cuit;

    private String emailContacto;

    private String telefono;

    @Column(nullable = false)
    private LocalDateTime fechaAlta;

    @OneToMany(mappedBy = "organizacion")
    @JsonIgnore
    private List<CentroAtencion> centros = new ArrayList<>();

    @OneToOne(mappedBy = "organizacion")
    @JsonIgnore
    private Suscripcion suscripcion;
}
