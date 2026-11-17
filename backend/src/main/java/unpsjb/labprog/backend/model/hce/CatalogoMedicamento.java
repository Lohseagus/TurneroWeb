package unpsjb.labprog.backend.model.hce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hce_catalogo_medicamento", uniqueConstraints = @UniqueConstraint(columnNames = {
        "nombre_generico", "concentracion", "forma_farmaceutica" }))
@Getter
@Setter
@NoArgsConstructor
public class CatalogoMedicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_generico", nullable = false, length = 200)
    private String nombreGenerico;

    @Column(length = 80)
    private String concentracion;

    @Column(name = "forma_farmaceutica", length = 80)
    private String formaFarmaceutica;

    @Column(length = 40)
    private String via;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoReceta tipo = TipoReceta.COMUN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ListaControlada lista = ListaControlada.NINGUNA;
}
