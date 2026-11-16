package unpsjb.labprog.backend.saas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import unpsjb.labprog.backend.model.ConfiguracionSaas;
import unpsjb.labprog.backend.model.PlanSuscripcion;

/**
 * Precio neto + IVA. El descuento anual se aplica sobre el neto de 12 meses, antes del IVA.
 */
public final class CalculadoraPrecioSaas {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal DOCE = new BigDecimal("12");

    private CalculadoraPrecioSaas() {
    }

    public static PrecioCalculado calcular(
            PlanSuscripcion plan,
            ConfiguracionSaas config,
            int profesionalesContratados,
            int sedes,
            List<BigDecimal> addonsNetosMensuales,
            CicloFacturacion ciclo) {

        if (plan == null) {
            throw new ReglaSuscripcionException("El plan es obligatorio.");
        }
        if (config == null) {
            throw new ReglaSuscripcionException("Falta la configuración comercial.");
        }
        CicloFacturacion cicloEfectivo = ciclo == null ? CicloFacturacion.MENSUAL : ciclo;

        int seats = 0;
        BigDecimal porProfesional = nz(plan.getPrecioPorProfesional());
        if (porProfesional.signum() > 0) {
            int min = plan.getMinProfesionales() == null ? 1 : plan.getMinProfesionales();
            int max = plan.getMaxProfesionales() == null ? Integer.MAX_VALUE : plan.getMaxProfesionales();
            seats = Math.max(profesionalesContratados, min);
            if (seats > max) {
                throw new ReglaSuscripcionException(
                        "El plan " + plan.getNombre() + " admite entre " + min + " y " + max + " profesionales.");
            }
        }

        BigDecimal netoMensual = nz(plan.getPrecioBaseMensual());
        if (seats > 0) {
            netoMensual = netoMensual.add(porProfesional.multiply(BigDecimal.valueOf(seats)));
        }

        int incluidas = plan.getSedesIncluidas() == null ? 0 : plan.getSedesIncluidas();
        int extras = Math.max(0, sedes - incluidas);
        if (plan.getPrecioPorSedeExtra() != null && extras > 0) {
            netoMensual = netoMensual.add(plan.getPrecioPorSedeExtra().multiply(BigDecimal.valueOf(extras)));
        }
        if (addonsNetosMensuales != null) {
            for (BigDecimal addon : addonsNetosMensuales) {
                netoMensual = netoMensual.add(nz(addon));
            }
        }

        BigDecimal descuento = plan.getDescuentoAnualPorcentaje() != null
                ? plan.getDescuentoAnualPorcentaje()
                : nz(config.getDescuentoAnualPorcentaje());
        BigDecimal netoPeriodo = netoMensual;
        if (cicloEfectivo == CicloFacturacion.ANUAL) {
            BigDecimal factor = BigDecimal.ONE.subtract(descuento.divide(CIEN, 6, RoundingMode.HALF_UP));
            if (factor.signum() < 0) {
                factor = BigDecimal.ZERO;
            }
            netoPeriodo = netoMensual.multiply(DOCE).multiply(factor);
        }

        BigDecimal ivaPct = nz(config.getIvaPorcentaje());
        netoMensual = netoMensual.setScale(2, RoundingMode.HALF_UP);
        netoPeriodo = netoPeriodo.setScale(2, RoundingMode.HALF_UP);
        BigDecimal iva = netoPeriodo.multiply(ivaPct).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal total = netoPeriodo.add(iva).setScale(2, RoundingMode.HALF_UP);

        return new PrecioCalculado(
                netoMensual,
                netoPeriodo,
                iva,
                total,
                seats,
                extras,
                descuento,
                ivaPct,
                cicloEfectivo);
    }

    public static BigDecimal conIva(BigDecimal neto, BigDecimal ivaPorcentaje) {
        BigDecimal base = nz(neto).setScale(2, RoundingMode.HALF_UP);
        BigDecimal iva = base.multiply(nz(ivaPorcentaje)).divide(CIEN, 2, RoundingMode.HALF_UP);
        return base.add(iva);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
