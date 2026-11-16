package unpsjb.labprog.backend.saas.pasarela;

import java.math.BigDecimal;

import unpsjb.labprog.backend.saas.CicloFacturacion;

public record SolicitudPlanExterno(
        String codigoPlan,
        String nombre,
        CicloFacturacion ciclo,
        BigDecimal montoConIva,
        String moneda,
        String preapprovalPlanIdExistente) {
}
