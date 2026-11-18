package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class SaasEnforcementFilterTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apagadoNoBloqueaAunqueLaSuscripcionNoPermitaEscribir() throws Exception {
        SuscripcionAccessService access = mock(SuscripcionAccessService.class);
        when(access.evaluarEscritura(any()))
                .thenReturn(ResultadoEscritura.bloquear("no debería verse", true));
        autenticar(Role.OPERADOR);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean siguio = new AtomicBoolean();
        filtro(false, access).doFilter(pedido(), response, cadena(siguio));

        assertTrue(siguio.get());
        assertEquals(200, response.getStatus());
        assertFalse(response.getContentAsString().contains("SUSCRIPCION_SOLO_LECTURA"));
        verify(access, never()).evaluarEscritura(any());
    }

    @Test
    void prendidoResponde403ConElCodigo() throws Exception {
        SuscripcionAccessService access = mock(SuscripcionAccessService.class);
        when(access.evaluarEscritura(1))
                .thenReturn(ResultadoEscritura.bloquear(
                        SuscripcionAccessService.mensajeSoloLectura(EstadoSuscripcion.SUSPENDIDA), false));
        autenticar(Role.ADMINISTRADOR);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean siguio = new AtomicBoolean();
        filtro(true, access).doFilter(pedido(), response, cadena(siguio));

        assertFalse(siguio.get());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"SUSCRIPCION_SOLO_LECTURA\""));
        assertTrue(response.getContentAsString().contains("solo lectura"));
    }

    @Test
    void laFichaPropiaSigueLaMismaReglaDeSoloLectura() throws Exception {
        SuscripcionAccessService access = mock(SuscripcionAccessService.class);
        when(access.evaluarEscritura(1))
                .thenReturn(ResultadoEscritura.bloquear(
                        SuscripcionAccessService.mensajeSoloLectura(EstadoSuscripcion.SUSPENDIDA), false));
        autenticar(Role.MEDICO);

        MockHttpServletResponse bloqueada = new MockHttpServletResponse();
        AtomicBoolean siguioBloqueada = new AtomicBoolean();
        filtro(true, access).doFilter(pedido("POST", "/api/pacientes/me"), bloqueada, cadena(siguioBloqueada));

        assertFalse(siguioBloqueada.get());
        assertEquals(403, bloqueada.getStatus());
        assertTrue(bloqueada.getContentAsString().contains("SUSCRIPCION_SOLO_LECTURA"));

        MockHttpServletResponse libre = new MockHttpServletResponse();
        AtomicBoolean siguioLibre = new AtomicBoolean();
        filtro(false, access).doFilter(pedido("POST", "/api/pacientes/me"), libre, cadena(siguioLibre));

        assertTrue(siguioLibre.get());
        assertEquals(200, libre.getStatus());
    }

    @Test
    void elDefaultDelComponenteQuedaApagadoSiNoLoPrenden() {
        assertFalse(new SaasEnforcement(false).habilitado());
        assertTrue(new SaasEnforcement(true).habilitado());
    }

    private static SuscripcionSoloLecturaFilter filtro(boolean habilitado, SuscripcionAccessService access) {
        return new SuscripcionSoloLecturaFilter(
                proveedor(access),
                proveedor(mock(CentroObjetivoResolver.class)),
                proveedor(new SaasEnforcement(habilitado)));
    }

    private static FilterChain cadena(AtomicBoolean siguio) {
        return (request, response) -> siguio.set(true);
    }

    private static MockHttpServletRequest pedido() {
        return pedido("POST", "/api/probe");
    }

    private static MockHttpServletRequest pedido(String metodo, String path) {
        return new MockHttpServletRequest(metodo, path);
    }

    private static void autenticar(Role role) {
        User user = new User();
        user.setId(1L);
        user.setEmail(role.name().toLowerCase() + "@cheturno.test");
        user.setRole(role);
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        user.setCentroAtencion(centro);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> proveedor(T valor) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(valor);
        return provider;
    }
}
