package unpsjb.labprog.backend.saas.pasarela;

import java.util.Map;

/**
 * Cobro de suscripciones. La implementación real prevista es Mercado Pago
 * Subscriptions (preapproval_plan + preapproval). La simulada deja el contrato listo.
 */
public interface PasarelaPago {

    String proveedor();

    ResultadoPlanExterno asegurarPlan(SolicitudPlanExterno solicitud);

    ResultadoPreapproval crearPreapproval(SolicitudPreapproval solicitud);

    ResultadoPreapproval actualizarMonto(String preapprovalId, SolicitudPreapproval solicitud);

    ResultadoPreapproval pausar(String preapprovalId);

    ResultadoPreapproval reactivar(String preapprovalId);

    ResultadoPreapproval cancelar(String preapprovalId);

    /**
     * TODO de Mercado Pago: validar el header x-signature (HMAC-SHA256) con el secret.
     * Hasta implementarlo debe rechazar la notificación.
     */
    boolean validarFirma(String payload, String xSignature, String xRequestId);

    EventoPasarela interpretarNotificacion(String payload, Map<String, String> headers);
}
