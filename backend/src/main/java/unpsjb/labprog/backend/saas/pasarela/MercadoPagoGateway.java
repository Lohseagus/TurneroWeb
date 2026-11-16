package unpsjb.labprog.backend.saas.pasarela;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub de Mercado Pago Subscriptions. No está activo salvo
 * saas.payment.provider=mercadopago. No llama a la API: cada operación deja el TODO
 * del endpoint oficial. Las claves se leen de variables de entorno y no se loguean.
 *
 * Alta de plan:  POST {api}/preapproval_plan
 * Alta de suscripción: POST {api}/preapproval  (preapproval_plan_id, external_reference, payer_email, auto_recurring)
 * Pausa / reactivación / cancelación: PUT {api}/preapproval/{id}  status=paused|authorized|cancelled
 * Webhook: header x-signature, manifest id:{data.id};request-id:{x-request-id};ts:{ts};
 */
@Component
@ConditionalOnProperty(name = "saas.payment.provider", havingValue = "mercadopago")
public class MercadoPagoGateway implements PasarelaPago {

    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoGateway.class);

    private final String accessToken;
    private final String webhookSecret;
    private final String apiBase;

    public MercadoPagoGateway(
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${mercadopago.webhook-secret:}") String webhookSecret,
            @Value("${mercadopago.api-base:https://api.mercadopago.com}") String apiBase) {
        this.accessToken = accessToken == null ? "" : accessToken;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
        this.apiBase = apiBase;
    }

    @Override
    public String proveedor() {
        return "mercadopago";
    }

    @Override
    public ResultadoPlanExterno asegurarPlan(SolicitudPlanExterno solicitud) {
        exigirToken();
        // TODO: POST {apiBase}/preapproval_plan con reason, auto_recurring.frequency,
        // frequency_type (months), transaction_amount = monto con IVA, currency_id = ARS.
        // Guardar el id devuelto en PlanSuscripcion.mpPreapprovalPlanIdMensual o Anual.
        throw pendiente("crear preapproval_plan en " + apiBase + "/preapproval_plan");
    }

    @Override
    public ResultadoPreapproval crearPreapproval(SolicitudPreapproval solicitud) {
        exigirToken();
        // TODO: POST {apiBase}/preapproval con preapproval_plan_id, external_reference,
        // payer_email, status pending y back_url. El cobro real lo confirma el webhook
        // cuando el estado pasa a authorized.
        throw pendiente("crear preapproval en " + apiBase + "/preapproval");
    }

    @Override
    public ResultadoPreapproval actualizarMonto(String preapprovalId, SolicitudPreapproval solicitud) {
        exigirToken();
        // TODO: PUT {apiBase}/preapproval/{id} actualizando auto_recurring.transaction_amount
        // cuando cambia el cupo, las sedes extra o los add-ons.
        throw pendiente("actualizar preapproval " + preapprovalId);
    }

    @Override
    public ResultadoPreapproval pausar(String preapprovalId) {
        exigirToken();
        // TODO: PUT {apiBase}/preapproval/{id} { "status": "paused" }
        throw pendiente("pausar preapproval " + preapprovalId);
    }

    @Override
    public ResultadoPreapproval reactivar(String preapprovalId) {
        exigirToken();
        // TODO: PUT {apiBase}/preapproval/{id} { "status": "authorized" }
        throw pendiente("reactivar preapproval " + preapprovalId);
    }

    @Override
    public ResultadoPreapproval cancelar(String preapprovalId) {
        exigirToken();
        // TODO: PUT {apiBase}/preapproval/{id} { "status": "cancelled" }
        throw pendiente("cancelar preapproval " + preapprovalId);
    }

    @Override
    public boolean validarFirma(String payload, String xSignature, String xRequestId) {
        // TODO: extraer ts y v1 del header x-signature (ts=...,v1=...).
        // Manifest: id:{data.id};request-id:{x-request-id};ts:{ts};
        // HMAC-SHA256 con mercadopago.webhook-secret y comparar en tiempo constante con v1.
        // Mientras esto no esté, se rechaza la notificación para no aplicar pagos sin firma.
        if (webhookSecret.isBlank()) {
            logger.warn("MP_WEBHOOK_SECRET vacío: no se puede validar la firma del webhook de Mercado Pago.");
        } else {
            logger.warn("Validación de firma de Mercado Pago pendiente de implementar. Notificación rechazada.");
        }
        return false;
    }

    @Override
    public EventoPasarela interpretarNotificacion(String payload, Map<String, String> headers) {
        return MercadoPagoNotificacionParser.parsear(payload);
    }

    private void exigirToken() {
        if (accessToken.isBlank()) {
            throw new PasarelaNoImplementadaException(
                    "Falta MP_ACCESS_TOKEN. Mercado Pago no está configurado. Ver docs/SAAS.md.");
        }
    }

    private PasarelaNoImplementadaException pendiente(String operacion) {
        return new PasarelaNoImplementadaException(
                "Mercado Pago todavía no está cableado (" + operacion
                        + "). Seguí docs/SAAS.md o usá saas.payment.provider=simulated.");
    }
}
