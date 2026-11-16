package unpsjb.labprog.backend.saas;

import java.math.BigDecimal;

public record PrecioCalculado(
        BigDecimal netoMensual,
        BigDecimal netoPeriodo,
        BigDecimal iva,
        BigDecimal total,
        int profesionalesFacturados,
        int sedesExtra,
        BigDecimal descuentoAnualPorcentaje,
        BigDecimal ivaPorcentaje,
        CicloFacturacion ciclo) {
}
