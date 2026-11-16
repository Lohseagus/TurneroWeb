package unpsjb.labprog.backend.model.hce;

import java.time.LocalDate;

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

@Entity
@Table(name = "hce_vacuna")
@Getter
@Setter
@NoArgsConstructor
public class Vacuna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private PerfilClinico perfil;

    @Column(nullable = false, length = 200)
    private String nombre;

    private LocalDate fechaAplicacion;

    @Column(length = 80)
    private String dosis;

    @Column(length = 80)
    private String lote;
}
