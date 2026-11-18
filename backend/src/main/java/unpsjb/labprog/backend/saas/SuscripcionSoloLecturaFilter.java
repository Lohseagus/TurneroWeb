package unpsjb.labprog.backend.saas;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

/**
 * Corta las escrituras de un centro cuya suscripción no está en prueba ni activa,
 * y también las de un centro que no tiene suscripción. El SUPERADMIN no entra.
 * Las lecturas y la lista blanca ({@link RutasEscrituraSaas}) siguen.
 */
public class SuscripcionSoloLecturaFilter extends OncePerRequestFilter {

    private final ObjectProvider<SuscripcionAccessService> accessProvider;
    private final ObjectProvider<CentroObjetivoResolver> resolverProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SuscripcionSoloLecturaFilter(ObjectProvider<SuscripcionAccessService> accessProvider,
            ObjectProvider<CentroObjetivoResolver> resolverProvider) {
        this.accessProvider = accessProvider;
        this.resolverProvider = resolverProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SuscripcionAccessService access = accessProvider == null ? null : accessProvider.getIfAvailable();
        if (access == null) {
            chain.doFilter(request, response);
            return;
        }
        CentroObjetivoResolver resolver = resolverProvider == null ? null : resolverProvider.getIfAvailable();
        String metodo = request.getMethod();
        String path = path(request);
        if (RutasEscrituraSaas.esLectura(metodo) || RutasEscrituraSaas.estaPermitida(path)) {
            chain.doFilter(request, response);
            return;
        }
        User user = usuario();
        if (user != null && user.getRole() == Role.SUPERADMIN) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest pedido = request;
        Integer centroId = centroDelUsuario(user);
        if (centroId == null && resolver != null) {
            CentroObjetivoResolver.Resolucion resolucion = resolver.resolver(request, path);
            if (resolucion != null) {
                centroId = resolucion.centroId();
                if (resolucion.request() != null) {
                    pedido = resolucion.request();
                }
            }
        }
        if (centroId == null) {
            chain.doFilter(pedido, response);
            return;
        }

        ResultadoEscritura resultado = access.evaluarEscritura(centroId);
        if (resultado == null || resultado.permite()) {
            chain.doFilter(pedido, response);
            return;
        }

        boolean paciente = user != null && user.getRole() == Role.PACIENTE;
        String mensaje = paciente
                ? SuscripcionAccessService.mensajeParaPaciente(resultado.sinSuscripcion())
                : resultado.mensaje();
        rechazar(response, mensaje);
    }

    private static Integer centroDelUsuario(User user) {
        if (user == null || user.getCentroAtencion() == null) {
            return null;
        }
        return user.getCentroAtencion().getId();
    }

    private static User usuario() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        return null;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri;
    }

    private void rechazar(HttpServletResponse response, String mensaje) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status_code", HttpStatus.FORBIDDEN.value());
        cuerpo.put("status_text", mensaje);
        cuerpo.put("code", SuscripcionAccessService.CODIGO_SOLO_LECTURA);
        cuerpo.put("data", null);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(cuerpo));
    }
}
