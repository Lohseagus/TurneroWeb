package unpsjb.labprog.backend.saas;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Qué escrituras no pasan por la regla de solo lectura.
 * Las lecturas (GET, HEAD, OPTIONS) no llegan acá: el filtro las deja pasar por método.
 */
public final class RutasEscrituraSaas {

    private static final Pattern HISTORIAL_BUSCAR = Pattern.compile("^/api/turno/historial/\\d+/buscar/?$");
    private static final Pattern VALIDAR_PORCENTAJES = Pattern.compile(
            "^/api/staff-medico/centrosAtencion/\\d+/medicos/porcentajes/validar/?$");

    private RutasEscrituraSaas() {
    }

    public static boolean esLectura(String metodo) {
        if (metodo == null) {
            return true;
        }
        String normalizado = metodo.toUpperCase(Locale.ROOT);
        return normalizado.equals("GET") || normalizado.equals("HEAD") || normalizado.equals("OPTIONS");
    }

    /**
     * Escrituras que tienen que seguir andando aunque el centro esté en solo lectura:
     * sesión, alta y cobro de la suscripción, administración de CheTurno, el webhook
     * y las consultas que viajan como POST.
     */
    public static boolean estaPermitida(String path) {
        String ruta = normalizar(path);
        if (ruta.startsWith("/api/auth/")) {
            return true;
        }
        if (ruta.equals("/api/saas/registro") || ruta.equals("/api/saas/cotizar")) {
            return true;
        }
        if (ruta.equals("/api/saas/webhooks/mercadopago")) {
            return true;
        }
        if (ruta.equals("/api/saas/aviso") || ruta.equals("/api/saas/mi-suscripcion")
                || ruta.startsWith("/api/saas/mi-suscripcion/")
                || ruta.equals("/api/saas/admin") || ruta.startsWith("/api/saas/admin/")) {
            return true;
        }
        if (ruta.equals("/api/recetas/verificar") || ruta.startsWith("/api/recetas/verificar/")) {
            return true;
        }
        if (ruta.equals("/api/ordenes-estudio/verificar") || ruta.startsWith("/api/ordenes-estudio/verificar/")) {
            return true;
        }
        if (ruta.equals("/api/turno/search") || ruta.equals("/api/turno/filters/simple")
                || ruta.equals("/api/turno/export/pdf") || HISTORIAL_BUSCAR.matcher(ruta).matches()) {
            return true;
        }
        if (ruta.equals("/api/export") || ruta.startsWith("/api/export/")) {
            return true;
        }
        if (ruta.equals("/api/deep-links/validate")) {
            return true;
        }
        if (ruta.equals("/api/esquema-turno/validar-conflictos")) {
            return true;
        }
        return VALIDAR_PORCENTAJES.matcher(ruta).matches();
    }

    public static String normalizar(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String ruta = path;
        int query = ruta.indexOf('?');
        if (query >= 0) {
            ruta = ruta.substring(0, query);
        }
        if (ruta.length() > 1 && ruta.endsWith("/")) {
            ruta = ruta.substring(0, ruta.length() - 1);
        }
        return ruta;
    }
}
