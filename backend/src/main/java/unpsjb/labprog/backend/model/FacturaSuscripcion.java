package unpsjb.labprog.backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FacturaSuscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String numero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "suscripcion_id", nullable = false)
    private Suscripcion suscripcion;

    @ManyToOne
    @JoinColumn(name = "pago_id")
    private PagoSuscripcion pago;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal neto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal iva;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 8)
    private String moneda = "ARS";

    @Column(nullable = false)
    private LocalDateTime fechaEmision;

    private LocalDateTime periodoDesde;

    private LocalDateTime periodoHasta;

    @Column(nullable = false, length = 20)
    private String estado = "PAGADA";
}
