package unpsjb.labprog.backend.saas.pasarela;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lee el cuerpo de un webhook de Mercado Pago.
 * El aviso real trae type y data.id; el estado del recurso hay que pedirlo a la API
 * (todavía no está cableado). Si el JSON trae status o external_reference, se usan.
 */
public final class MercadoPagoNotificacionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MercadoPagoNotificacionParser() {
    }

    public static EventoPasarela parsear(String payload) {
        if (payload == null || payload.isBlank()) {
            return new EventoPasarela(TipoNotificacionPasarela.IGNORADA, null, null, null, "Cuerpo vacío");
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            String type = texto(root, "type");
            if (type == null) {
                type = texto(root, "topic");
            }
            String id = null;
            JsonNode data = root.get("data");
            if (data != null && data.hasNonNull("id")) {
                id = data.get("id").asText();
            }
            if (id == null) {
                id = texto(root, "id");
            }
            String estado = texto(root, "status");
            if (estado == null && data != null) {
                estado = texto(data, "status");
            }
            String referencia = texto(root, "external_reference");
            TipoNotificacionPasarela tipo = clasificar(type);
            String detalle = tipo == TipoNotificacionPasarela.IGNORADA
                    ? "Tipo de notificación no reconocido: " + type
                    : "Notificación " + type;
            return new EventoPasarela(tipo, id, estado, referencia, detalle);
        } catch (Exception e) {
            return new EventoPasarela(TipoNotificacionPasarela.IGNORADA, null, null, null,
                    "JSON inválido");
        }
    }

    private static TipoNotificacionPasarela clasificar(String type) {
        if (type == null) {
            return TipoNotificacionPasarela.IGNORADA;
        }
        String normalizado = type.trim().toLowerCase();
        if (normalizado.contains("preapproval") || normalizado.contains("subscription")) {
            return TipoNotificacionPasarela.PREAPPROVAL;
        }
        if (normalizado.contains("payment")) {
            return TipoNotificacionPasarela.PAYMENT;
        }
        return TipoNotificacionPasarela.IGNORADA;
    }

    private static String texto(JsonNode node, String campo) {
        if (node == null || !node.hasNonNull(campo)) {
            return null;
        }
        String valor = node.get(campo).asText();
        return valor == null || valor.isBlank() ? null : valor;
    }
}
