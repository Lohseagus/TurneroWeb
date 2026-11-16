package unpsjb.labprog.backend.saas.pasarela;

import java.math.BigDecimal;

public record ResultadoPreapproval(
        String preapprovalId,
        String estadoMp,
        String paymentId,
        BigDecimal monto,
        String moneda,
        String detalle) {
}
