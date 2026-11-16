package unpsjb.labprog.backend.model.hce;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import unpsjb.labprog.backend.model.Medico;

/**
 * Corrección posterior al cierre. No se edita ni se borra.
 */
@Entity
@Table(name = "hce_enmienda")
@Getter
@Setter
@NoArgsConstructor
public class Enmienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico autor;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(nullable = false, length = 300)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime creadaEn;
}
