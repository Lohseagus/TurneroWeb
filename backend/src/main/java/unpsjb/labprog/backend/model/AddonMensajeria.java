package unpsjb.labprog.backend.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Complemento opcional de mensajería (WhatsApp, SMS).
 * El precio es neto mensual. Hoy es un placeholder: no hay proveedor conectado.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AddonMensajeria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioMensualNeto = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;

    /** True mientras no haya un proveedor real de envío. */
    @Column(nullable = false)
    private boolean placeholder = true;
}
