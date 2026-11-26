package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.PlanFeatureWebConfig;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Suscripcion;
import unpsjb.labprog.backend.model.User;

/** Con saas.enforcement.enabled apagado la compuerta no corta nada. */
@WebMvcTest(controllers = ProbeFeatureController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, SaasEnforcement.class,
        PlanFeatureGate.class, PlanFeatureWebConfig.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "saas.enforcement.enabled=false",
        "DB_URL=jdbc:h2:mem:featureoff",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class PlanFeatureApagadaWebTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SuscripcionAccessService suscripcionAccessService;

    @MockitoBean
    private CentroObjetivoResolver centroObjetivoResolver;

    @BeforeEach
    void centroConPlanProfesional() {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setPlan(PlanFeaturesTest.plan(CodigosPlan.PROFESIONAL, false, false, false));
        when(suscripcionAccessService.buscarPorCentro(any())).thenReturn(Optional.of(suscripcion));
        when(suscripcionAccessService.evaluarEscritura(any())).thenReturn(ResultadoEscritura.permitir());
    }

    @Test
    void conLaBanderaApagadaNadaCambia() throws Exception {
        String admin = token(Role.ADMINISTRADOR, 1);
        MockHttpServletResponse respuesta = pedir(get("/api/probe-feature"), admin);

        assertEquals(200, respuesta.getStatus());
        assertFalse(respuesta.getContentAsString().contains("PLAN_SIN_FEATURE"));
        assertEquals(200, pedir(post("/api/probe-feature/consolidado")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), admin).getStatus());
        assertEquals(200, pedir(get("/api/probe-feature"), token(Role.OPERADOR, 1)).getStatus());
    }

    private MockHttpServletResponse pedir(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private String token(Role role, Integer centroId) throws Exception {
        User user = PlanFeatureGateTest.usuario(role, centroId);
        user.setHashedPassword("hash");
        when(userService.loadUserByUsername(user.getEmail())).thenReturn(user);
        Field expiration = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        expiration.setAccessible(true);
        if (expiration.getLong(jwtTokenProvider) <= 0) {
            expiration.setLong(jwtTokenProvider, 900_000L);
        }
        return jwtTokenProvider.generateAccessToken(user);
    }
}
