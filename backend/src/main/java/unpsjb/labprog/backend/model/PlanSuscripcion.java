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
 * Plan comercial. Los importes son netos, en ARS, sin IVA.
 * El precio mensual es precioBase + precioPorProfesional * cupo + precioPorSedeExtra * sedes extra.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PlanSuscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 2000)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioBaseMensual = BigDecimal.ZERO;

    /** Precio neto por profesional contratado. Cero si el plan no es por asiento. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioPorProfesional = BigDecimal.ZERO;

    private Integer minProfesionales;

    private Integer maxProfesionales;

    /** Null = sin tope. */
    private Integer maxMedicos;

    private Integer maxOperadores;

    private Integer maxConsultorios;

    private Integer maxEspecialidades;

    /** Null = sedes ilimitadas (el excedente se cobra si hay precioPorSedeExtra). */
    private Integer maxCentros;

    /** Sedes cubiertas por el precio base. */
    @Column(nullable = false)
    private Integer sedesIncluidas = 1;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioPorSedeExtra;

    /**
     * Si no es null, pisa el descuento anual global para este plan.
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal descuentoAnualPorcentaje;

    @Column(nullable = false)
    private boolean reportesBasicos;

    @Column(nullable = false)
    private boolean reportesConsolidados;

    @Column(nullable = false)
    private boolean soportePrioritario;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private int orden = 0;

    /** Id de preapproval_plan de Mercado Pago para el cobro mensual. */
    private String mpPreapprovalPlanIdMensual;

    /** Id de preapproval_plan de Mercado Pago para el cobro anual. */
    private String mpPreapprovalPlanIdAnual;
}
