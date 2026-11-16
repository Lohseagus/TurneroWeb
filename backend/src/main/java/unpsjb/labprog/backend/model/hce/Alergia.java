package unpsjb.labprog.backend.model.hce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "hce_alergia")
@Getter
@Setter
@NoArgsConstructor
public class Alergia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private PerfilClinico perfil;

    @Column(nullable = false, length = 200)
    private String sustancia;

    @Column(length = 400)
    private String reaccion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CriticidadAlergia criticidad = CriticidadAlergia.NO_EVALUADA;

    @Column(length = 200)
    private String systemUri;

    @Column(length = 64)
    private String code;

    @Column(nullable = false)
    private boolean activa = true;
}
