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
@Table(name = "hce_orden_estudio")
@Getter
@Setter
@NoArgsConstructor
public class OrdenEstudio extends DocumentoAsistencial {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UrgenciaEstudio urgencia = UrgenciaEstudio.NORMAL;

    @Column(columnDefinition = "TEXT")
    private String indicacionClinica;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<OrdenEstudioItem> items = new ArrayList<>();
}
