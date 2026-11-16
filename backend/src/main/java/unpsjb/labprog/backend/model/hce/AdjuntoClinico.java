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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Paciente;

@Entity
@Table(name = "hce_adjunto")
@Getter
@Setter
@NoArgsConstructor
public class AdjuntoClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne
    @JoinColumn(name = "consulta_id")
    private Consulta consulta;

    @ManyToOne
    @JoinColumn(name = "centro_atencion_id")
    private CentroAtencion centroAtencion;

    @Column(nullable = false, length = 255)
    private String nombreOriginal;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long tamanioBytes;

    /** Ruta relativa dentro del volumen de almacenamiento. El binario no va a la base. */
    @Column(nullable = false, length = 500)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAdjunto tipo = TipoAdjunto.DOCUMENTO;

    @Column(nullable = false, length = 180)
    private String subidoPor;

    @Column(nullable = false)
    private LocalDateTime creadoEn;
}
