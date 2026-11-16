package unpsjb.labprog.backend.model.hce;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de accesos a la historia. Append-only: no hay baja.
 */
@Entity
@Table(name = "hce_acceso")
@Getter
@Setter
@NoArgsConstructor
public class AccesoHistoriaClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer pacienteId;

    @Column(length = 180)
    private String actorEmail;

    @Column(length = 40)
    private String actorRol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccionAuditoriaHce accion;

    @Column(length = 40)
    private String recursoTipo;

    @Column(length = 40)
    private String recursoId;

    private Integer centroId;

    @Column(length = 300)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime ocurridoEn;
}
