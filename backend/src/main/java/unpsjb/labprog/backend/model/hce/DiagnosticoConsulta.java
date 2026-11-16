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
@Table(name = "hce_diagnostico")
@Getter
@Setter
@NoArgsConstructor
public class DiagnosticoConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @Column(name = "system_uri", nullable = false, length = 200)
    private String systemUri;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 400)
    private String display;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDiagnostico tipo = TipoDiagnostico.PRESUNTIVO;
}
