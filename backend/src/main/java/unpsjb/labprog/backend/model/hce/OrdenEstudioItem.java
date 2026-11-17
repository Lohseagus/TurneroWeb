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
@Table(name = "hce_orden_estudio_item")
@Getter
@Setter
@NoArgsConstructor
public class OrdenEstudioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "orden_id", nullable = false)
    private OrdenEstudio orden;

    @Column(name = "nro_item", nullable = false)
    private int ordenNumero;

    @Column(nullable = false, length = 240)
    private String nombrePractica;

    @Column(name = "system_uri", length = 200)
    private String systemUri;

    @Column(length = 40)
    private String code;
}
