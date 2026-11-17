package unpsjb.labprog.backend.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde 401 cuando una ruta protegida llega sin un usuario autenticado.
 * Sin esto Spring Security contesta 403 y el cliente no distingue
 * "no hay sesión" de "no tenés permiso".
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean tokenRechazado = request.getAttribute(JwtAuthenticationFilter.TOKEN_RECHAZADO_ATTR) != null;
        String mensaje = tokenRechazado ? "Token inválido o vencido" : "Autenticación requerida";

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status_code\":401,\"status_text\":\"" + mensaje + "\",\"data\":null}");
    }
}
