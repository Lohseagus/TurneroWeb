package unpsjb.labprog.backend.model.hce;

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
@Table(name = "hce_medicacion")
@Getter
@Setter
@NoArgsConstructor
public class MedicacionHabitual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private PerfilClinico perfil;

    @Column(nullable = false, length = 200)
    private String farmaco;

    @Column(length = 120)
    private String dosis;

    @Column(length = 120)
    private String frecuencia;

    @Column(length = 200)
    private String systemUri;

    @Column(length = 64)
    private String code;

    @Column(nullable = false)
    private boolean activa = true;
}
