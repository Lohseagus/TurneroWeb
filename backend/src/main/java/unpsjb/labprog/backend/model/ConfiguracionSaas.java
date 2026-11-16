package unpsjb.labprog.backend.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parámetros comerciales editables por SUPERADMIN.
 * Hay una sola fila (id = 1).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionSaas {

    public static final int ID = 1;

    @Id
    private Integer id = ID;

    /** Porcentaje de IVA. 21 = 21%. Se aplica sobre el neto. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ivaPorcentaje = new BigDecimal("21.00");

    /** Duración del trial sin tarjeta, en días. */
    @Column(nullable = false)
    private int diasTrial = 30;

    /** Descuento sobre el neto anual (15 = 15% sobre 12 meses). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoAnualPorcentaje = new BigDecimal("15.00");

    @Column(nullable = false, length = 8)
    private String moneda = "ARS";
}
