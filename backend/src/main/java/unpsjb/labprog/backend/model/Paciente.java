package unpsjb.labprog.backend.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AttributeOverride(name = "dni", column = @Column(name = "dni", unique = true, nullable = false))
public class Paciente extends Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Temporal(TemporalType.DATE)
    private Date fechaNacimiento;

    /**
     * F, M o X. Opcional: la receta electrónica lo pide al emitir.
     */
    @Column(length = 1)
    private String sexo;

    /**
     * Número de afiliado a la obra social o prepaga. Opcional.
     */
    @Column(length = 40)
    private String numeroAfiliado;

    @ManyToOne
        private ObraSocial obraSocial;
    
    @Column(nullable = false)
    private boolean profileCompleted = false;

    /**
     * Identificador del paciente en un federador (Red Nacional de Salud Digital).
     * Opcional hasta que exista la integración.
     */
    @Column(length = 120)
    private String identificadorFederado;

    @OneToMany(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private Set<PreferenciaHoraria> preferenciasHorarias = new HashSet<>();
}
