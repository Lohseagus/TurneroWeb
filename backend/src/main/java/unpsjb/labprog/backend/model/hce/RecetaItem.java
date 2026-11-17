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
@Table(name = "hce_receta_item")
@Getter
@Setter
@NoArgsConstructor
public class RecetaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    @Column(name = "nro_item", nullable = false)
    private int orden;

    @Column(nullable = false, length = 200)
    private String nombreGenerico;

    @Column(length = 200)
    private String marcaComercial;

    @Column(length = 80)
    private String concentracion;

    @Column(length = 80)
    private String formaFarmaceutica;

    @Column(length = 120)
    private String presentacion;

    private Integer cantidad;

    @Column(length = 80)
    private String cantidadEnLetras;

    @Column(length = 120)
    private String dosis;

    @Column(length = 120)
    private String frecuencia;

    @Column(length = 120)
    private String duracion;

    @Column(length = 40)
    private String via;

    @Column(columnDefinition = "TEXT")
    private String indicaciones;

    @Column(nullable = false)
    private boolean noSustituir;

    @Column(length = 300)
    private String justificacionNoSustituir;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ListaControlada lista = ListaControlada.NINGUNA;
}
