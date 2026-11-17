package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.service.EncuestaService;
import unpsjb.labprog.backend.business.service.ExportService;
import unpsjb.labprog.backend.business.service.ListaEsperaService;
import unpsjb.labprog.backend.business.service.NotificacionService;
import unpsjb.labprog.backend.business.service.PacienteAccessGuard;
import unpsjb.labprog.backend.business.service.PacienteService;
import unpsjb.labprog.backend.business.service.RecordatorioService;
import unpsjb.labprog.backend.business.service.TurnoAutomationService;
import unpsjb.labprog.backend.business.service.TurnoService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.dto.ListaEsperaDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

/**
 * Lote 3 de la auditoría (SEC-A5, A6, A9, A10 y el alta en lista de espera), con
 * security.dev.mode=false como en producción: una cuenta solo llega a los datos de
 * la ficha de paciente que tiene su mismo email.
 */
@WebMvcTest(controllers = {NotificacionPresenter.class, TurnoPresenter.class,
        EncuestaPacientePresenter.class, ListaEsperaPresenter.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, PacienteAccessGuard.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:titular",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class PacienteTitularSecurityTest {

    private static final int PROPIO = 10;
    private static final int AJENO = 11;
    private static final int TURNO_PROPIO = 100;
    private static final int TURNO_AJENO = 101;
    private static final String EMAIL = "paciente@turnero.com";

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
    private NotificacionService notificacionService;
    @MockitoBean
    private TurnoService turnoService;
    @MockitoBean
    private ExportService exportService;
    @MockitoBean
    private RecordatorioService recordatorioService;
    @MockitoBean
    private TurnoAutomationService turnoAutomationService;
    @MockitoBean
    private EncuestaService encuestaService;
    @MockitoBean
    private ListaEsperaService listaEsperaService;

    @BeforeEach
    void setUp() {
        when(pacienteRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(paciente(PROPIO)));
        when(turnoRepository.findById(TURNO_PROPIO)).thenReturn(Optional.of(turno(PROPIO)));
        when(turnoRepository.findById(TURNO_AJENO)).thenReturn(Optional.of(turno(AJENO)));
        when(turnoService.pacienteIdDelTurno(TURNO_PROPIO)).thenReturn(Optional.of(PROPIO));
        when(turnoService.pacienteIdDelTurno(TURNO_AJENO)).thenReturn(Optional.of(AJENO));
    }

    // SEC-A9

    @Test
    void elPacienteLeeSusNotificacionesYNoLasDeOtro() throws Exception {
        String paciente = token(Role.PACIENTE, EMAIL, null);

        pedir(200, paciente, get("/api/notificaciones/paciente/" + PROPIO + "/count-no-leidas"));
        pedir(403, paciente, get("/api/notificaciones/paciente/" + AJENO + "/count-no-leidas"));
        pedir(403, paciente, get("/api/notificaciones/paciente/" + AJENO));
        pedir(403, paciente, delete("/api/notificaciones/5").param("pacienteId", String.valueOf(AJENO)));

        verify(notificacionService, never()).contarNotificacionesNoLeidas(AJENO);
        verify(notificacionService, never()).eliminarNotificacion(any(), anyInt());
    }

    @Test
    void elPersonalDelCentroNoLeeNotificacionesDePacientes() throws Exception {
        pedir(403, token(Role.OPERADOR, "operador@turnero.com", 1),
                get("/api/notificaciones/paciente/" + PROPIO + "/no-leidas"));
        pedir(403, token(Role.ADMINISTRADOR, "admin@turnero.com", 1),
                get("/api/notificaciones/paciente/" + PROPIO + "/no-leidas"));
    }

    // SEC-A5

    @Test
    void elHistorialEsSoloDelTitular() throws Exception {
        String paciente = token(Role.PACIENTE, EMAIL, null);

        pedir(200, paciente, get("/api/turno/historial/" + PROPIO + "/tiene-pendientes"));
        for (String ruta : List.of("", "/filtrado", "/estadisticas", "/proximos", "/cancelados",
                "/tiene-pendientes", "/export/csv")) {
            pedir(403, paciente, get("/api/turno/historial/" + AJENO + ruta));
        }
        pedir(403, paciente, post("/api/turno/historial/" + AJENO + "/buscar")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, paciente, get("/api/turno/historial/detalle/" + TURNO_AJENO));
        pedir(200, paciente, get("/api/turno/historial/detalle/" + TURNO_PROPIO));
        pedir(403, token(Role.MEDICO, "medico@turnero.com", 1), get("/api/turno/historial/" + PROPIO));

        verify(turnoService, never()).getHistorialTurnoById(TURNO_AJENO);
        verify(turnoService, never()).tieneTurnosPendientes(AJENO);
    }

    // SEC-A6

    @Test
    void elPacienteVeSusTurnosCompletosYNoLosDeOtro() throws Exception {
        String paciente = token(Role.PACIENTE, EMAIL, null);

        pedir(200, paciente, get("/api/turno/paciente/" + PROPIO));
        pedir(403, paciente, get("/api/turno/paciente/" + AJENO));

        verify(turnoService).findByPacienteId(PROPIO, null);
        verify(turnoService, never()).findByPacienteId(eq(AJENO), any());
    }

    @Test
    void elPersonalVeSoloLosTurnosDeSuCentro() throws Exception {
        pedir(200, token(Role.OPERADOR, "operador@turnero.com", 3), get("/api/turno/paciente/" + AJENO));

        verify(turnoService).findByPacienteId(AJENO, 3);
    }

    // SEC-A10

    @Test
    void laEncuestaEsSoloDelTitularDelTurno() throws Exception {
        String paciente = token(Role.PACIENTE, EMAIL, null);

        pedir(200, paciente, get("/api/encuestas/turno/" + TURNO_PROPIO + "/pendiente"));
        pedir(403, paciente, get("/api/encuestas/turno/" + TURNO_AJENO));
        pedir(403, paciente, get("/api/encuestas/turno/" + TURNO_AJENO + "/pendiente"));
        pedir(403, paciente, post("/api/encuestas/responder").contentType(MediaType.APPLICATION_JSON)
                .content("{\"turnoId\":" + TURNO_AJENO + ",\"respuestas\":[]}"));
        pedir(403, token(Role.OPERADOR, "operador@turnero.com", 1),
                get("/api/encuestas/turno/" + TURNO_PROPIO + "/pendiente"));

        verify(encuestaService, never()).guardarRespuestas(any());
        verify(encuestaService, never()).getPlantillaForTurno(TURNO_AJENO);
    }

    // Alta en lista de espera

    @Test
    void elPacienteSeAnotaEnListaDeEsperaSoloASuNombre() throws Exception {
        pedir(200, token(Role.PACIENTE, EMAIL, null), altaListaEspera(AJENO));

        ArgumentCaptor<ListaEsperaDTO> guardado = ArgumentCaptor.forClass(ListaEsperaDTO.class);
        verify(listaEsperaService).save(guardado.capture());
        assertEquals(PROPIO, guardado.getValue().getPacienteId());
        assertEquals(null, guardado.getValue().getId());
    }

    @Test
    void unPacienteSinFichaNoSeAnotaEnListaDeEspera() throws Exception {
        pedir(403, token(Role.PACIENTE, "sin.ficha@turnero.com", null), altaListaEspera(AJENO));

        verifyNoInteractions(listaEsperaService);
    }

    @Test
    void elOperadorAnotaAlPacienteQueElige() throws Exception {
        pedir(200, token(Role.OPERADOR, "operador@turnero.com", 1), altaListaEspera(AJENO));

        ArgumentCaptor<ListaEsperaDTO> guardado = ArgumentCaptor.forClass(ListaEsperaDTO.class);
        verify(listaEsperaService).save(guardado.capture());
        assertEquals(AJENO, guardado.getValue().getPacienteId());
    }

    private static MockHttpServletRequestBuilder altaListaEspera(int pacienteId) {
        return post("/api/lista-espera").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":77,\"pacienteId\":" + pacienteId + ",\"especialidadId\":1,\"centroAtencionId\":1}");
    }

    private static Paciente paciente(int id) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        return paciente;
    }

    private static Turno turno(int pacienteId) {
        Turno turno = new Turno();
        turno.setPaciente(paciente(pacienteId));
        return turno;
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

    private void pedir(int esperado, String bearer, MockHttpServletRequestBuilder request) throws Exception {
        var respuesta = mvc.perform(request.accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + bearer)).andReturn().getResponse();
        assertEquals(esperado, respuesta.getStatus(), respuesta.getContentAsString());
        if (esperado == 403) {
            assertTrue(respuesta.getContentAsString().contains("\"status_code\":403"), respuesta.getContentAsString());
        }
    }
}
