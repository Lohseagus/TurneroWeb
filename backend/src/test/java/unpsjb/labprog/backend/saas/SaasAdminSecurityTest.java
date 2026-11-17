package unpsjb.labprog.backend.saas;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import unpsjb.labprog.backend.business.service.AuditLogService;
import unpsjb.labprog.backend.business.service.EmailService;
import unpsjb.labprog.backend.business.service.EncuestaInvitacionService;
import unpsjb.labprog.backend.business.service.PacienteService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.presenter.AdminPresenter;
import unpsjb.labprog.backend.presenter.EmailController;
import unpsjb.labprog.backend.presenter.RoleManagementController;
import unpsjb.labprog.backend.presenter.SaasPresenter;
import unpsjb.labprog.backend.saas.pasarela.PasarelaPago;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * security.dev.mode=false, igual que producción. El token lo firma
 * JwtTokenProvider y el filtro arma las authorities desde User.getAuthorities()
 * (ROLE_ + el nombre del enum, más los roles heredados).
 */
@WebMvcTest(controllers = {
        SaasPresenter.class,
        AdminPresenter.class,
        EmailController.class,
        RoleManagementController.class
})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:saassec",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class SaasAdminSecurityTest {

    private static final List<Call> SAAS_ADMIN = List.of(
            new Call(HttpMethod.GET, "/api/saas/admin/resumen", null),
            new Call(HttpMethod.GET, "/api/saas/admin/suscripciones", null),
            new Call(HttpMethod.GET, "/api/saas/admin/suscripciones?estado=ACTIVA", null),
            new Call(HttpMethod.GET, "/api/saas/admin/suscripciones/1", null),
            new Call(HttpMethod.POST, "/api/saas/admin/suscripciones/1/extender-trial", "{\"dias\":7}"),
            new Call(HttpMethod.POST, "/api/saas/admin/suscripciones/1/suspender", "{\"motivo\":\"mora\"}"),
            new Call(HttpMethod.POST, "/api/saas/admin/suscripciones/1/reactivar", "{}"),
            new Call(HttpMethod.POST, "/api/saas/admin/suscripciones/1/cancelar", "{\"motivo\":\"baja\"}"),
            new Call(HttpMethod.POST, "/api/saas/admin/suscripciones/1/cambiar-plan", "{\"planCodigo\":\"CENTRO\"}"),
            new Call(HttpMethod.POST, "/api/saas/admin/suscripciones/1/vincular-centro", "{\"centroId\":1}"),
            new Call(HttpMethod.GET, "/api/saas/admin/planes", null),
            new Call(HttpMethod.PUT, "/api/saas/admin/planes/1", "{\"nombre\":\"Centro\"}"),
            new Call(HttpMethod.PUT, "/api/saas/admin/configuracion", "{\"diasTrial\":30}"),
            new Call(HttpMethod.PUT, "/api/saas/admin/addons/1", "{\"nombre\":\"WhatsApp\"}"),
            new Call(HttpMethod.GET, "/api/saas/admin/pagos", null)
    );

    private static final List<Call> OTROS_SUPERADMIN = List.of(
            new Call(HttpMethod.GET, "/api/admins", null),
            new Call(HttpMethod.GET, "/api/role-management/role-changes", null),
            new Call(HttpMethod.GET, "/api/role-management/role-statistics", null),
            new Call(HttpMethod.POST, "/api/email/text", "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}")
    );

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PacienteService pacienteService;

    @MockitoBean
    private SaasService saasService;

    @MockitoBean
    private PasarelaPago pasarelaPago;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private EncuestaInvitacionService encuestaInvitacionService;

    @MockitoBean
    private AuditLogService auditLogService;

    @BeforeEach
    void pacienteVacio() {
        when(pacienteService.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void superadminEntraATodosLosEndpointsDeAdminSaas() throws Exception {
        String token = token(Role.SUPERADMIN, "superadmin@cheturno.test");
        for (Call call : SAAS_ADMIN) {
            assertStatus(200, call, token);
        }
    }

    @Test
    void otrosRolesNoEntranAlAdminSaas() throws Exception {
        for (Role role : List.of(Role.ADMINISTRADOR, Role.MEDICO, Role.PACIENTE)) {
            String bearer = token(role, role.name().toLowerCase() + "@cheturno.test");
            for (Call call : SAAS_ADMIN) {
                assertStatus(403, call, bearer);
            }
        }
    }

    @Test
    void anonimoTampocoEntraAlAdminSaas() throws Exception {
        for (Call call : SAAS_ADMIN) {
            assertStatus(403, call, null);
        }
    }

    @Test
    void superadminEntraAlRestoDeEndpointsExclusivosYLosOtrosNo() throws Exception {
        String superadmin = token(Role.SUPERADMIN, "superadmin@cheturno.test");
        for (Call call : OTROS_SUPERADMIN) {
            assertStatus(200, call, superadmin);
        }
        for (Role role : List.of(Role.ADMINISTRADOR, Role.MEDICO, Role.PACIENTE)) {
            String bearer = token(role, role.name().toLowerCase() + "@cheturno.test");
            for (Call call : OTROS_SUPERADMIN) {
                assertStatus(403, call, bearer);
            }
        }
    }

    @Test
    void elCatalogoPublicoSigueAbiertoYElAltaDeOperadoresSigueSiendoDeAdministrador() throws Exception {
        assertStatus(200, new Call(HttpMethod.GET, "/api/saas/planes", null), null);
        assertStatus(200, new Call(HttpMethod.POST, "/api/saas/cotizar", "{}"), null);

        String admin = token(Role.ADMINISTRADOR, "admin@cheturno.test");
        String medico = token(Role.MEDICO, "medico@cheturno.test");
        String superadmin = token(Role.SUPERADMIN, "superadmin@cheturno.test");
        Call operadores = new Call(HttpMethod.POST, "/api/admins/operadores", "{}");
        assertStatus(200, operadores, admin);
        assertStatus(200, operadores, superadmin);
        assertStatus(403, operadores, medico);
        assertStatus(403, new Call(HttpMethod.GET, "/api/admins", null), admin);
    }

    @Test
    void unBearerVencidoORotoResponde401YNo403() throws Exception {
        User user = usuario(Role.SUPERADMIN, "superadmin@cheturno.test");
        Field expiration = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        expiration.setAccessible(true);
        long original = expiration.getLong(jwtTokenProvider);
        String vencido;
        try {
            expiration.setLong(jwtTokenProvider, -1_000L);
            vencido = jwtTokenProvider.generateAccessToken(user);
        } finally {
            expiration.setLong(jwtTokenProvider, original);
        }
        assertStatus(401, new Call(HttpMethod.GET, "/api/saas/admin/suscripciones", null), vencido);
        assertStatus(401, new Call(HttpMethod.GET, "/api/saas/admin/resumen", null), "not-a-jwt");
        // El refresh viaja con el access token ya vencido. Tiene que llegar al controller.
        int refresh = status(new Call(HttpMethod.POST, "/api/auth/refresh", "{\"refreshToken\":\"x\"}"), vencido);
        assertEquals(true, refresh != 401, "POST /api/auth/refresh respondió 401 y el cliente no puede renovar");
        assertStatus(200, new Call(HttpMethod.GET, "/api/saas/admin/suscripciones", null),
                jwtTokenProvider.generateAccessToken(user));
    }

    private String token(Role role, String email) {
        return jwtTokenProvider.generateAccessToken(usuario(role, email));
    }

    private User usuario(Role role, String email) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        user.setHashedPassword("hash");
        when(userService.loadUserByUsername(email)).thenReturn(user);
        return user;
    }

    private int status(Call call, String bearer) throws Exception {
        MockHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(call.method(), call.path());
        if (call.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(call.body());
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private void assertStatus(int expected, Call call, String bearer) throws Exception {
        int actual = status(call, bearer);
        assertEquals(expected, actual, call.method() + " " + call.path() + " respondió " + actual);
    }

    private record Call(HttpMethod method, String path, String body) {
    }
}
