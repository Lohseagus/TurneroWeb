package unpsjb.labprog.backend.saas.pasarela;

import java.math.BigDecimal;

import unpsjb.labprog.backend.saas.CicloFacturacion;

/**
 * Alta o actualización de una preapproval de Mercado Pago.
 * El monto es el total con IVA que paga el centro.
 */
public record SolicitudPreapproval(
        Integer suscripcionId,
        String externalReference,
        String preapprovalPlanId,
        String razon,
        String emailPagador,
        CicloFacturacion ciclo,
        BigDecimal montoConIva,
        String moneda) {
}
