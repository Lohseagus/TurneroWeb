package unpsjb.labprog.backend.model.hce;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import unpsjb.labprog.backend.model.Paciente;

@Entity
@Table(name = "hce_perfil")
@Getter
@Setter
@NoArgsConstructor
public class PerfilClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, unique = true)
    private Paciente paciente;

    @Column(columnDefinition = "TEXT")
    private String antecedentesPersonales;

    @Column(columnDefinition = "TEXT")
    private String antecedentesFamiliares;

    @Column(columnDefinition = "TEXT")
    private String antecedentesQuirurgicos;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GrupoSanguineo grupoSanguineo = GrupoSanguineo.DESCONOCIDO;

    @Column(columnDefinition = "TEXT")
    private String habitos;

    /**
     * Consentimiento simple para que un médico de otro centro lea la historia.
     * Sin este flag, la lectura queda en los centros donde el paciente se atendió.
     */
    @Column(nullable = false)
    private boolean comparteEntreCentros = false;

    private LocalDateTime actualizadoEn;

    @Column(length = 180)
    private String actualizadoPor;
}
