package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

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

import unpsjb.labprog.backend.business.service.AltaFichaPropia;
import unpsjb.labprog.backend.business.service.PacienteService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.dto.PacienteDTO;
import unpsjb.labprog.backend.exception.DatosFichaIncompletosException;
import unpsjb.labprog.backend.exception.FichaAjenaException;
import unpsjb.labprog.backend.exception.PacienteSinFichaException;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

/**
 * security.dev.mode=false. La ficha propia se lee y se edita en /api/pacientes/me
 * con el rol PACIENTE (OPERADOR lo hereda). El catálogo por id no se abre a un paciente.
 * Un anónimo recibe 401, igual que el resto de las rutas protegidas.
 */
@WebMvcTest(controllers = PacientePresenter.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:pacienteme",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class PacienteMeSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PacienteService pacienteService;

    @Test
    void operadorLeeSuFichaSinPasarPorElId() throws Exception {
        when(pacienteService.actual()).thenReturn(Optional.of(ficha(2558, "Ana")));

        String body = cuerpo(200, token(Role.OPERADOR, "ana@turnero.com"), "/api/pacientes/me");
        assertTrue(body.contains("\"id\":2558"), body);
        assertTrue(body.contains("Ana"), body);
    }

    @Test
    void pacienteLeeSuFicha() throws Exception {
        when(pacienteService.actual()).thenReturn(Optional.of(ficha(4, "María")));
        String body = cuerpo(200, token(Role.PACIENTE, "maria@turnero.com"), "/api/pacientes/me");
        assertTrue(body.contains("\"id\":4"), body);
    }

    @Test
    void sinFichaRecibe404ConMensaje() throws Exception {
        when(pacienteService.actual()).thenReturn(Optional.empty());
        String body = cuerpo(404, token(Role.OPERADOR, "sin.ficha@turnero.com"), "/api/pacientes/me");
        assertTrue(body.contains("ficha de paciente"), body);
    }

    @Test
    void pacienteNoLeeUnaFichaAjenaPorId() throws Exception {
        when(pacienteService.findById(2558)).thenReturn(Optional.of(ficha(2558, "Ana")));
        cuerpo(403, token(Role.PACIENTE, "otro@turnero.com"), "/api/pacientes/2558");
    }

    @Test
    void operadorSiguePudiendoAbrirElCatalogoPorId() throws Exception {
        when(pacienteService.findById(9)).thenReturn(Optional.of(ficha(9, "Luis")));
        String body = cuerpo(200, token(Role.OPERADOR, "ana@turnero.com"), "/api/pacientes/9");
        assertTrue(body.contains("\"id\":9"), body);
    }

    @Test
    void laEdicionPropiaNoUsaElIdDelCatalogo() throws Exception {
        when(pacienteService.actualizarPropio(any())).thenReturn(ficha(2558, "Ana"));
        var request = put("/api/pacientes/me")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(Role.OPERADOR, "ana@turnero.com"))
                .content("{\"id\":999,\"nombre\":\"Ana\",\"apellido\":\"Martínez\",\"email\":\"ana@turnero.com\"}");
        var respuesta = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, respuesta.getStatus(), respuesta.getContentAsString());
        assertTrue(respuesta.getContentAsString().contains("\"id\":2558"), respuesta.getContentAsString());
    }

    @Test
    void pacienteNoEditaUnaFichaAjenaPorId() throws Exception {
        var request = put("/api/pacientes/2558")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(Role.PACIENTE, "otro@turnero.com"))
                .content("{\"id\":2558,\"nombre\":\"Intruso\"}");
        var respuesta = mvc.perform(request).andReturn().getResponse();
        assertEquals(403, respuesta.getStatus(), respuesta.getContentAsString());
    }

    @Test
    void anonimoNoEntra() throws Exception {
        String body = cuerpo(401, null, "/api/pacientes/me");
        assertTrue(body.contains("Autenticación requerida"), body);
    }

    @Test
    void elPersonalCreaSuFichaEnElMismoEndpoint() throws Exception {
        when(pacienteService.crearPropio(any())).thenReturn(new AltaFichaPropia(ficha(2558, "Ana"), false));

        String body = postCuerpo(201, token(Role.OPERADOR, "ana@turnero.com"),
                "{\"id\":999,\"email\":\"otro@turnero.com\",\"nombre\":\"Ana\"}");
        assertTrue(body.contains("\"id\":2558"), body);
        assertTrue(body.contains("Creaste tu ficha"), body);
    }

    @Test
    void unPacienteTambienPuedePedirLaSuya() throws Exception {
        when(pacienteService.crearPropio(any())).thenReturn(new AltaFichaPropia(ficha(4, "María"), true));
        String body = postCuerpo(200, token(Role.PACIENTE, "maria@turnero.com"), "{}");
        assertTrue(body.contains("\"id\":4"), body);
        assertTrue(body.contains("vinculó"), body);
    }

    @Test
    void crearLaFichaDeOtroResponde409() throws Exception {
        when(pacienteService.crearPropio(any())).thenThrow(new FichaAjenaException());
        String body = postCuerpo(409, token(Role.MEDICO, "ana@turnero.com"),
                "{\"email\":\"paciente1.santamaria@turnero.com\"}");
        assertTrue(body.contains("propia ficha"), body);
    }

    @Test
    void faltanDatosResponde400ConLosCampos() throws Exception {
        when(pacienteService.crearPropio(any())).thenThrow(new DatosFichaIncompletosException(List.of("telefono")));
        String body = postCuerpo(400, token(Role.ADMINISTRADOR, "admin@turnero.com"), "{}");
        assertTrue(body.contains("telefono"), body);
        assertTrue(body.contains("Completá"), body);
    }

    @Test
    void anonimoNoCreaSuFicha() throws Exception {
        String body = postCuerpo(401, null, "{\"nombre\":\"Ana\"}");
        assertTrue(body.contains("Autenticación requerida"), body);
    }

    private PacienteDTO ficha(int id, String nombre) {
        PacienteDTO dto = new PacienteDTO();
        dto.setId(id);
        dto.setNombre(nombre);
        dto.setApellido("Martínez");
        dto.setEmail("ana@turnero.com");
        return dto;
    }

    private String token(Role role, String email) throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        user.setHashedPassword("hash");
        when(userService.loadUserByUsername(email)).thenReturn(user);
        Field expiration = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        expiration.setAccessible(true);
        if (expiration.getLong(jwtTokenProvider) <= 0) {
            expiration.setLong(jwtTokenProvider, 900_000L);
        }
        return jwtTokenProvider.generateAccessToken(user);
    }

    private String postCuerpo(int expected, String bearer, String json) throws Exception {
        var request = post("/api/pacientes/me")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json);
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        var respuesta = mvc.perform(request).andReturn().getResponse();
        assertEquals(expected, respuesta.getStatus(), respuesta.getContentAsString());
        return respuesta.getContentAsString();
    }

    private String cuerpo(int expected, String bearer, String path) throws Exception {
        var request = get(path).accept(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        var respuesta = mvc.perform(request).andReturn().getResponse();
        assertEquals(expected, respuesta.getStatus(), path + " respondió " + respuesta.getStatus()
                + " " + respuesta.getContentAsString());
        return respuesta.getContentAsString();
    }
}
