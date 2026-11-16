package unpsjb.labprog.backend.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import unpsjb.labprog.backend.saas.CicloFacturacion;
import unpsjb.labprog.backend.saas.EstadoSuscripcion;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "organizacion_id", nullable = false, unique = true)
    private Organizacion organizacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PlanSuscripcion plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSuscripcion estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CicloFacturacion ciclo = CicloFacturacion.MENSUAL;

    /**
     * Cupo de profesionales que se factura en planes por asiento.
     * En CONSULTORIO es el mínimo contratado (al menos el mínimo del plan).
     */
    @Column(nullable = false)
    private int profesionalesContratados = 1;

    @Column(nullable = false)
    private LocalDateTime fechaInicio;

    private LocalDateTime fechaFinTrial;

    private LocalDateTime fechaInicioPeriodo;

    private LocalDateTime fechaFinPeriodo;

    /** Id de preapproval (suscripción) en Mercado Pago. */
    private String mpPreapprovalId;

    /** Último estado informado por Mercado Pago (authorized, paused, cancelled, pending). */
    private String mpStatus;

    /**
     * Centros que ya operaban antes del SaaS. Siguen activos y no se los cobra
     * ni se los pasa a solo lectura por vencimiento de período.
     */
    @Column(nullable = false)
    private boolean heredada = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoSuscripcion estadoAnterior;

    @Column(length = 500)
    private String motivo;

    @JsonIgnore
    public boolean isSoloLectura() {
        return estado == null || !estado.permiteEscribir();
    }
}
