package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.lang.reflect.Field;
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

import unpsjb.labprog.backend.business.service.MedicoService;
import unpsjb.labprog.backend.business.service.PacienteService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.dto.MedicoActualDTO;
import unpsjb.labprog.backend.dto.MedicoDTO;
import unpsjb.labprog.backend.exception.MedicoSinFichaException;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

/**
 * security.dev.mode=false, igual que producción. Un MEDICO tiene que poder
 * leer /api/medicos/me y /api/medicos/email/{email} (el mail lleva puntos).
 */
@WebMvcTest(controllers = MedicoPresenter.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:medicome",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class MedicoMeSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PacienteService pacienteService;

    @MockitoBean
    private MedicoService medicoService;

    @Test
    void medicoAutenticadoObtieneSuFicha() throws Exception {
        MedicoActualDTO dto = new MedicoActualDTO();
        dto.setId(7);
        dto.setStaffMedicoId(15);
        when(medicoService.actual()).thenReturn(Optional.of(dto));

        String body = cuerpo(200, token(Role.MEDICO, "medico@turnero.com"), "/api/medicos/me");
        assertTrue(body.contains("\"id\":7"), body);
        assertTrue(body.contains("\"staffMedicoId\":15"), body);
    }

    @Test
    void medicoSinFichaRecibe404ConMensaje() throws Exception {
        when(medicoService.actual()).thenThrow(new MedicoSinFichaException());
        String body = cuerpo(404, token(Role.MEDICO, "sin.ficha@turnero.com"), "/api/medicos/me");
        assertTrue(body.contains("ficha profesional"), body);
    }

    @Test
    void anonimoNoEntra() throws Exception {
        cuerpo(403, null, "/api/medicos/me");
    }

    @Test
    void elLookupPorEmailConservaElPuntoDelDominio() throws Exception {
        MedicoDTO dto = new MedicoDTO();
        dto.setId(7);
        dto.setEmail("medico@turnero.com");
        when(medicoService.findByEmail(anyString())).thenReturn(Optional.of(dto));

        String body = cuerpo(200, token(Role.MEDICO, "medico@turnero.com"),
                "/api/medicos/email/medico@turnero.com");
        assertTrue(body.contains("medico@turnero.com"), body);
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
