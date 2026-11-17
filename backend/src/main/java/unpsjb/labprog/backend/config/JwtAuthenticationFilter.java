package unpsjb.labprog.backend.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro JWT que intercepta las requests para validar tokens de acceso.
 *
 * Con la sesión en STATELESS, SecurityContextHolderFilter vuelve a leer el
 * contexto desde {@link RequestAttributeSecurityContextRepository}. Si el
 * token se acepta, hay que dejarlo ahí: si no, el filtro de autorización ve
 * un anónimo y responde 403.
 *
 * Un Bearer vencido o mal formado no se deja seguir. Si la excepción sale del
 * filtro, el dispatch de error vuelve a pasar por la cadena sin el usuario y
 * también termina en 403, que el frontend muestra como falta de permiso.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            final String userEmail = jwtTokenProvider.extractUsername(jwt);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                if (jwtTokenProvider.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authToken);
                    SecurityContextHolder.setContext(context);
                    securityContextRepository.saveContext(context, request, response);
                } else if (!isAuthEndpoint(request)) {
                    unauthorized(response);
                    return;
                }
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            // /api/auth/refresh sale con el access token ya vencido en el header.
            // Si lo cortamos acá, el cliente no puede renovar la sesión.
            if (!isAuthEndpoint(request)) {
                unauthorized(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/auth/");
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status_code\":401,\"status_text\":\"Token inválido o vencido\",\"data\":null}");
    }
}
