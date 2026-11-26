package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.service.CentroAccessGuard;
import unpsjb.labprog.backend.business.service.CentroAtencionService;
import unpsjb.labprog.backend.business.service.PacienteAccessGuard;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

/**
 * Crear y borrar un centro es de SUPERADMIN. Un ADMIN edita solo el suyo.
 * El operador lee el suyo y no escribe.
 */
@WebMvcTest(controllers = CentroAtencionPresenter.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        PacienteAccessGuard.class, CentroAccessGuard.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:centros-alta",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class CentroAtencionSecurityTest {

    private static final String CENTRO = "{\"id\":1,\"nombre\":\"Norte\"}";
    private static final String OTRO = "{\"id\":2,\"nombre\":\"Sur\"}";
    private static final String ALTA = "{\"nombre\":\"Nuevo\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private CentroAtencionService centroService;
    @MockitoBean
    private PacienteRepository pacienteRepository;
    @MockitoBean
    private MedicoRepository medicoRepository;
    @MockitoBean
    private StaffMedicoRepository staffMedicoRepository;
    @MockitoBean
    private TurnoRepository turnoRepository;
    @MockitoBean
    private ConsultorioRepository consultorioRepository;
    @MockitoBean
    private DisponibilidadMedicoRepository disponibilidadRepository;
    @MockitoBean
    private EsquemaTurnoRepository esquemaTurnoRepository;

    @Test
    void soloElSuperadminCreaYElimina() throws Exception {
        String root = token(Role.SUPERADMIN, "root@turnero.com", null);
        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);

        pedir(200, root, post("/api/centrosAtencion").contentType(MediaType.APPLICATION_JSON).content(ALTA));
        pedir(200, root, delete("/api/centrosAtencion/2"));

        pedir(403, admin, post("/api/centrosAtencion").contentType(MediaType.APPLICATION_JSON).content(ALTA));
        pedir(403, admin, delete("/api/centrosAtencion/1"));
        pedir(403, operador, post("/api/centrosAtencion").contentType(MediaType.APPLICATION_JSON).content(ALTA));
        pedir(403, operador, delete("/api/centrosAtencion/1"));

        verify(centroService).saveOrUpdate(any(), any());
        verify(centroService).delete(org.mockito.ArgumentMatchers.eq(2), any());
        verify(centroService, never()).delete(org.mockito.ArgumentMatchers.eq(1), any());
    }

    @Test
    void elAdminEditaSoloSuCentro() throws Exception {
        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);
        pedir(200, admin, put("/api/centrosAtencion").contentType(MediaType.APPLICATION_JSON).content(CENTRO));
        pedir(403, admin, put("/api/centrosAtencion").contentType(MediaType.APPLICATION_JSON).content(OTRO));

        String root = token(Role.SUPERADMIN, "root@turnero.com", null);
        pedir(200, root, put("/api/centrosAtencion").contentType(MediaType.APPLICATION_JSON).content(OTRO));

        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);
        pedir(403, operador, put("/api/centrosAtencion").contentType(MediaType.APPLICATION_JSON).content(CENTRO));
    }

    @Test
    void elOperadorSoloLeeSuCentro() throws Exception {
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);
        pedir(200, operador, get("/api/centrosAtencion/1"));
        pedir(403, operador, get("/api/centrosAtencion/2"));
        verify(centroService, never()).findById(2);

        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);
        pedir(200, admin, get("/api/centrosAtencion/2"));
        verify(centroService).findById(2);
    }

    private String token(Role role, String email, Integer centroId) throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        user.setHashedPassword("hash");
        if (centroId != null) {
            CentroAtencion centro = new CentroAtencion();
            centro.setId(centroId);
            user.setCentroAtencion(centro);
        }
        org.mockito.Mockito.when(userService.loadUserByUsername(email)).thenReturn(user);
        Field expiration = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        expiration.setAccessible(true);
        if (expiration.getLong(jwtTokenProvider) <= 0) {
            expiration.setLong(jwtTokenProvider, 900_000L);
        }
        return jwtTokenProvider.generateAccessToken(user);
    }

    private void pedir(int esperado, String bearer, MockHttpServletRequestBuilder request) throws Exception {
        var respuesta = mvc.perform(request.accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + bearer)).andReturn().getResponse();
        assertEquals(esperado, respuesta.getStatus(), respuesta.getContentAsString());
        if (esperado == 403) {
            assertTrue(respuesta.getContentAsString().contains("\"status_code\":403"), respuesta.getContentAsString());
        }
    }
}
