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
 * Un Bearer vencido, mal formado o de otro tipo no autentica a nadie: el
 * pedido sigue como anónimo. En una ruta pública responde igual que sin header
 * (un token viejo en el navegador no tapa planes ni la agenda pública). En una
 * ruta protegida, {@link RestAuthenticationEntryPoint} contesta 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Marca el pedido cuyo Bearer se rechazó, para que el 401 diga por qué. */
    public static final String TOKEN_RECHAZADO_ATTR = JwtAuthenticationFilter.class.getName() + ".tokenRechazado";

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
                } else {
                    request.setAttribute(TOKEN_RECHAZADO_ATTR, Boolean.TRUE);
                }
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            request.setAttribute(TOKEN_RECHAZADO_ATTR, Boolean.TRUE);
        }

        filterChain.doFilter(request, response);
    }
}
