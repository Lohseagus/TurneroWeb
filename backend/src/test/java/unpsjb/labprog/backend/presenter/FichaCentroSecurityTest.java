package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.service.FichaCentroGuard;
import unpsjb.labprog.backend.business.service.PacienteService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.dto.PacienteDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

/**
 * Un ADMIN o un OPERADOR abre la ficha solo si el paciente tiene un turno o una
 * solicitud en su centro. SUPERADMIN no tiene ese límite.
 */
@WebMvcTest(controllers = PacientePresenter.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, FichaCentroGuard.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:ficha-centro",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class FichaCentroSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private PacienteService pacienteService;
    @MockitoBean
    private PacienteRepository pacienteRepository;
    @MockitoBean
    private TurnoRepository turnoRepository;
    @MockitoBean
    private ListaEsperaRepository listaEsperaRepository;

    @Test
    void elAdminVeAlPacienteDeSuCentroYNoAlDeOtro() throws Exception {
        cuandoExiste(9);
        when(turnoRepository.existsByPaciente_IdAndCentroAtencion_Id(9, 1)).thenReturn(true);
        when(pacienteService.findById(9)).thenReturn(Optional.of(ficha(9, "Lucía")));

        cuandoExiste(10);
        when(pacienteService.findById(10)).thenReturn(Optional.of(ficha(10, "Marcos")));

        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);
        String propio = pedir(200, admin, "/api/pacientes/9");
        assertTrue(propio.contains("Lucía"), propio);
        pedir(403, admin, "/api/pacientes/10");
        verify(pacienteService, never()).findById(10);
    }

    @Test
    void laListaDeEsperaTambienRelacionaAlPacienteConElCentro() throws Exception {
        cuandoExiste(11);
        when(listaEsperaRepository.existsByPaciente_IdAndCentroAtencion_Id(11, 1)).thenReturn(true);
        when(pacienteService.findById(11)).thenReturn(Optional.of(ficha(11, "Nora")));

        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);
        String body = pedir(200, operador, "/api/pacientes/11");
        assertTrue(body.contains("Nora"), body);
    }

    @Test
    void elOperadorTampocoCruzaDeCentro() throws Exception {
        cuandoExiste(10);
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);
        pedir(403, operador, "/api/pacientes/10");
        verify(pacienteService, never()).findById(10);
    }

    @Test
    void elSuperadminNoTieneEseLimite() throws Exception {
        cuandoExiste(10);
        when(pacienteService.findById(10)).thenReturn(Optional.of(ficha(10, "Marcos")));
        String root = token(Role.SUPERADMIN, "root@turnero.com", null);
        String body = pedir(200, root, "/api/pacientes/10");
        assertTrue(body.contains("Marcos"), body);
    }

    private void cuandoExiste(int id) {
        when(pacienteRepository.existsById(id)).thenReturn(true);
    }

    private PacienteDTO ficha(int id, String nombre) {
        PacienteDTO dto = new PacienteDTO();
        dto.setId(id);
        dto.setNombre(nombre);
        dto.setApellido("Test");
        return dto;
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
        when(userService.loadUserByUsername(email)).thenReturn(user);
        Field expiration = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        expiration.setAccessible(true);
        if (expiration.getLong(jwtTokenProvider) <= 0) {
            expiration.setLong(jwtTokenProvider, 900_000L);
        }
        return jwtTokenProvider.generateAccessToken(user);
    }

    private String pedir(int esperado, String bearer, String path) throws Exception {
        var respuesta = mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + bearer)).andReturn().getResponse();
        assertEquals(esperado, respuesta.getStatus(), path + " -> " + respuesta.getContentAsString());
        if (esperado == 403) {
            assertTrue(respuesta.getContentAsString().contains("\"status_code\":403"), respuesta.getContentAsString());
        }
        return respuesta.getContentAsString();
    }
}
