package unpsjb.labprog.backend.model.hce;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hce_receta")
@Getter
@Setter
@NoArgsConstructor
public class Receta extends DocumentoAsistencial {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoReceta tipo = TipoReceta.COMUN;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<RecetaItem> items = new ArrayList<>();
}
