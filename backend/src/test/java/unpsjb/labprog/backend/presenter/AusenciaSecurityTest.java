package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.service.AusenciaMedicoService;
import unpsjb.labprog.backend.business.service.CentroAccessGuard;
import unpsjb.labprog.backend.business.service.PacienteAccessGuard;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.dto.AusenciaResultadoDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.User;

/**
 * El médico solo toca las ausencias de su propia ficha. Admin y operador, solo las de su centro.
 */
@WebMvcTest(controllers = AusenciaPresenter.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        PacienteAccessGuard.class, CentroAccessGuard.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:ausencias",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class AusenciaSecurityTest {

    private static final String EMAIL_MEDICO = "medico@turnero.com";
    private static final int MEDICO_ID = 50;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private PacienteRepository pacienteRepository;
    @MockitoBean
    private TurnoRepository turnoRepository;
    @MockitoBean
    private MedicoRepository medicoRepository;
    @MockitoBean
    private StaffMedicoRepository staffMedicoRepository;
    @MockitoBean
    private ConsultorioRepository consultorioRepository;
    @MockitoBean
    private DisponibilidadMedicoRepository disponibilidadRepository;
    @MockitoBean
    private EsquemaTurnoRepository esquemaTurnoRepository;
    @MockitoBean
    private ConfiguracionExcepcionalRepository configuracionRepository;
    @MockitoBean
    private AusenciaMedicoService ausenciaService;

    @BeforeEach
    void setUp() {
        when(staffMedicoRepository.findById(1)).thenReturn(Optional.of(staff(1, 1, MEDICO_ID)));
        when(staffMedicoRepository.findById(2)).thenReturn(Optional.of(staff(2, 2, 99)));
        when(staffMedicoRepository.findById(4)).thenReturn(Optional.of(staff(4, 1, 99)));
        when(configuracionRepository.findById(10)).thenReturn(Optional.of(ausencia(10, staff(1, 1, MEDICO_ID))));
        when(configuracionRepository.findById(20)).thenReturn(Optional.of(ausencia(20, staff(2, 2, 99))));
        when(configuracionRepository.findById(40)).thenReturn(Optional.of(ausencia(40, staff(4, 1, 99))));

        Medico medico = new Medico();
        medico.setId(MEDICO_ID);
        when(medicoRepository.findByEmailIgnoreCase(EMAIL_MEDICO)).thenReturn(Optional.of(medico));
        when(staffMedicoRepository.findByMedicoId(MEDICO_ID)).thenReturn(List.of(staff(1, 1, MEDICO_ID)));
        when(ausenciaService.confirmar(any(), anyString())).thenReturn(new AusenciaResultadoDTO());
        when(ausenciaService.previsualizar(any())).thenReturn(new AusenciaResultadoDTO());
        when(ausenciaService.listarDeStaff(any())).thenReturn(List.of());
        when(ausenciaService.listarDeCentro(any())).thenReturn(List.of());
        org.mockito.Mockito.clearInvocations(ausenciaService);
    }

    @Test
    void elMedicoNoTocaLaAgendaDeOtroProfesional() throws Exception {
        String medico = token(Role.MEDICO, EMAIL_MEDICO, 1);

        pedir(200, medico, post("/api/agenda/ausencias").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1)));
        pedir(200, medico, get("/api/agenda/ausencias/preview")
                .param("staffMedicoId", "1").param("fechaInicio", "2026-12-10"));
        pedir(200, medico, delete("/api/agenda/ausencias/10"));

        pedir(403, medico, post("/api/agenda/ausencias").contentType(MediaType.APPLICATION_JSON).content(cuerpo(4)));
        pedir(403, medico, post("/api/agenda/ausencias").contentType(MediaType.APPLICATION_JSON).content(cuerpo(2)));
        pedir(403, medico, get("/api/agenda/ausencias/preview")
                .param("staffMedicoId", "4").param("fechaInicio", "2026-12-10"));
        pedir(403, medico, delete("/api/agenda/ausencias/40"));
        pedir(403, medico, post("/api/agenda/ausencias/40/baja").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(4)));

        verify(ausenciaService).confirmar(any(), anyString());
        verify(ausenciaService).eliminar(10);
        verify(ausenciaService, never()).eliminar(40);
        verify(ausenciaService, never()).eliminar(20);
    }

    @Test
    void elAdministradorYElOperadorNoCruzanDeCentro() throws Exception {
        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);
        String adminAjeno = token(Role.ADMINISTRADOR, "admin2@turnero.com", 2);

        pedir(200, admin, post("/api/agenda/ausencias").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1)));
        pedir(200, operador, delete("/api/agenda/ausencias/10"));
        pedir(403, admin, post("/api/agenda/ausencias").contentType(MediaType.APPLICATION_JSON).content(cuerpo(2)));
        pedir(403, operador, post("/api/agenda/ausencias").contentType(MediaType.APPLICATION_JSON).content(cuerpo(2)));
        pedir(403, operador, delete("/api/agenda/ausencias/20"));
        pedir(403, adminAjeno, get("/api/agenda/ausencias").param("centroId", "1"));
        pedir(403, adminAjeno, delete("/api/agenda/ausencias/10"));

        verify(ausenciaService, never()).eliminar(20);
    }

    private static String cuerpo(int staffId) {
        return "{\"staffMedicoId\":" + staffId + ",\"centroAtencionId\":1,"
                + "\"fechaInicio\":\"2026-12-10\",\"fechaFin\":\"2026-12-12\",\"motivo\":\"vacaciones\"}";
    }

    private static StaffMedico staff(int id, int centroId, int medicoId) {
        Medico medico = new Medico();
        medico.setId(medicoId);
        CentroAtencion centro = new CentroAtencion();
        centro.setId(centroId);
        StaffMedico staff = new StaffMedico();
        staff.setId(id);
        staff.setMedico(medico);
        staff.setCentroAtencion(centro);
        return staff;
    }

    private static ConfiguracionExcepcional ausencia(int id, StaffMedico staff) {
        ConfiguracionExcepcional ausencia = new ConfiguracionExcepcional();
        ausencia.setId(id);
        ausencia.setActivo(true);
        ausencia.setTipo(ConfiguracionExcepcional.TipoExcepcion.AUSENCIA_MEDICO);
        ausencia.setStaffMedico(staff);
        ausencia.setCentroAtencion(staff.getCentroAtencion());
        return ausencia;
    }

    private String token(Role role, String email, Integer centroId) throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        user.setHashedPassword("hash");
        if (centroId != null) {
            user.setCentroAtencion(staff(0, centroId, 0).getCentroAtencion());
        }
        when(userService.loadUserByUsername(email)).thenReturn(user);
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
        assertEquals(esperado, respuesta.getStatus(),
                request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI()
                        + " -> " + respuesta.getContentAsString());
        if (esperado == 403) {
            assertTrue(respuesta.getContentAsString().contains("\"status_code\":403"), respuesta.getContentAsString());
        }
    }
}
