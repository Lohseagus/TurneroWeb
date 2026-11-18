package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
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

import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.service.AgendaService;
import unpsjb.labprog.backend.business.service.CentroAccessGuard;
import unpsjb.labprog.backend.business.service.ConfiguracionExcepcionalService;
import unpsjb.labprog.backend.business.service.EncuestaInvitacionService;
import unpsjb.labprog.backend.business.service.OperadorService;
import unpsjb.labprog.backend.business.service.PacienteAccessGuard;
import unpsjb.labprog.backend.business.service.PacienteService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.dto.ConfiguracionExcepcionalDTO;
import unpsjb.labprog.backend.dto.OperadorDTO;
import unpsjb.labprog.backend.dto.TurnoDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Consultorio;
import unpsjb.labprog.backend.model.EsquemaTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.User;

/**
 * Lote 5 de la auditoría (SEC-A11, SEC-A16, SEC-M2) con security.dev.mode=false.
 *
 * Los recursos de prueba llevan el id de su centro: el esquema 2, el día excepcional 2
 * y el operador 2 son del centro 2.
 */
@WebMvcTest(controllers = {AgendaPresenter.class, OperadorPresenter.class, AdminPresenter.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        PacienteAccessGuard.class, CentroAccessGuard.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:agenda",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class AgendaOperadoresSecurityTest {

    private static final String NOMBRE_PACIENTE = "Florencia";
    private static final String EMAIL_MEDICO = "medico@turnero.com";

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
    private AgendaService agendaService;
    @MockitoBean
    private ConfiguracionExcepcionalService configuracionExcepcionalService;
    @MockitoBean
    private OperadorService operadorService;
    @MockitoBean
    private EncuestaInvitacionService encuestaInvitacionService;

    private final List<EsquemaTurno> esquemas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        esquemas.clear();
        for (int centroId = 1; centroId <= 3; centroId++) {
            EsquemaTurno esquema = esquema(centroId);
            esquemas.add(esquema);
            when(esquemaTurnoRepository.findById(centroId)).thenReturn(Optional.of(esquema));
            when(agendaService.generarEventosDesdeEsquemaTurno(eq(esquema), anyInt()))
                    .thenAnswer(llamada -> List.of(slotOcupado(esquema.getId())));

            ConfiguracionExcepcionalDTO config = new ConfiguracionExcepcionalDTO();
            config.setId(centroId);
            config.setTipo("MANTENIMIENTO");
            config.setCentroAtencionId(centroId);
            when(configuracionExcepcionalService.findByIdDTO(centroId)).thenReturn(Optional.of(config));

            OperadorDTO operador = new OperadorDTO();
            operador.setId((long) centroId);
            operador.setDni(1000L + centroId);
            operador.setEmail("operador" + centroId + "@turnero.com");
            operador.setCentroAtencionId(centroId);
            when(operadorService.findById((long) centroId)).thenReturn(Optional.of(operador));
            when(operadorService.findByDni(1000L + centroId)).thenReturn(Optional.of(operador));
            when(operadorService.findByEmail(operador.getEmail())).thenReturn(Optional.of(operador));
        }
        when(esquemaTurnoRepository.findAll()).thenReturn(esquemas);

        // Feriado: sin centro, vale para todos.
        ConfiguracionExcepcionalDTO feriado = new ConfiguracionExcepcionalDTO();
        feriado.setId(9);
        feriado.setTipo("FERIADO");
        List<ConfiguracionExcepcionalDTO> todas = new ArrayList<>();
        todas.add(feriado);
        for (int centroId = 1; centroId <= 3; centroId++) {
            todas.add(configuracionExcepcionalService.findByIdDTO(centroId).orElseThrow());
        }
        when(configuracionExcepcionalService.obtenerConfiguracionesPorRangoDTO(any(), any())).thenReturn(todas);

        Medico medico = new Medico();
        medico.setId(50);
        when(medicoRepository.findByEmailIgnoreCase(EMAIL_MEDICO)).thenReturn(Optional.of(medico));
        when(medicoRepository.findByEmail(EMAIL_MEDICO)).thenReturn(Optional.empty());
    }

    // SEC-A11: agenda

    @Test
    void elPacienteVeQueElHorarioEstaOcupadoPeroNoPorQuien() throws Exception {
        String cuerpo = pedir(200, token(Role.PACIENTE, "paciente@turnero.com", null),
                get("/api/agenda/eventos/todos").param("semanas", "1"));

        assertFalse(cuerpo.contains(NOMBRE_PACIENTE), cuerpo);
        assertTrue(cuerpo.contains("\"ocupado\":true"), cuerpo);
        assertTrue(cuerpo.contains("\"pacienteId\":null"), cuerpo);
        // El paciente elige centro: ve la disponibilidad de los tres.
        for (EsquemaTurno esquema : esquemas) {
            verify(agendaService).generarEventosDesdeEsquemaTurno(eq(esquema), anyInt());
        }
    }

    @Test
    void elMedicoQueSacaTurnoTampocoVeAlPaciente() throws Exception {
        String cuerpo = pedir(200, token(Role.MEDICO, EMAIL_MEDICO, 1),
                get("/api/agenda/eventos/todos").param("semanas", "1"));
        assertFalse(cuerpo.contains(NOMBRE_PACIENTE), cuerpo);
    }

    @Test
    void elOperadorVeLaAgendaDeSuCentroConElPaciente() throws Exception {
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);

        String cuerpo = pedir(200, operador, get("/api/agenda/eventos/todos").param("semanas", "1"));
        assertTrue(cuerpo.contains(NOMBRE_PACIENTE), cuerpo);
        verify(agendaService).generarEventosDesdeEsquemaTurno(eq(esquemas.get(0)), anyInt());
        verify(agendaService, never()).generarEventosDesdeEsquemaTurno(eq(esquemas.get(1)), anyInt());
        verify(agendaService, never()).generarEventosDesdeEsquemaTurno(eq(esquemas.get(2)), anyInt());

        pedir(200, operador, get("/api/agenda/eventos/todos").param("semanas", "1").param("centroId", "1"));
        pedir(403, operador, get("/api/agenda/eventos/todos").param("semanas", "1").param("centroId", "2"));
        verify(agendaService, never()).generarEventosDesdeEsquemaTurno(eq(esquemas.get(1)), anyInt());
    }

    @Test
    void elSuperadminVeTodosLosCentrosConElPaciente() throws Exception {
        String cuerpo = pedir(200, token(Role.SUPERADMIN, "root@turnero.com", null),
                get("/api/agenda/eventos/todos").param("semanas", "1").param("centroId", "2"));
        assertTrue(cuerpo.contains(NOMBRE_PACIENTE), cuerpo);
    }

    @Test
    void losDiasExcepcionalesSeTocanSoloEnElCentroPropio() throws Exception {
        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);
        String especial = "{\"fecha\":\"2030-01-10\",\"tipoAgenda\":\"ATENCION_ESPECIAL\",\"descripcion\":\"x\","
                + "\"esquemaTurnoId\":%d,\"horaInicio\":\"08:00\",\"horaFin\":\"10:00\"}";

        pedir(403, admin, delete("/api/agenda/dia-excepcional/2"));
        pedir(403, admin, put("/api/agenda/dia-excepcional/2").contentType(MediaType.APPLICATION_JSON)
                .content(String.format(especial, 1)));
        pedir(403, admin, put("/api/agenda/dia-excepcional/1").contentType(MediaType.APPLICATION_JSON)
                .content(String.format(especial, 2)));
        pedir(403, admin, post("/api/agenda/dia-excepcional").contentType(MediaType.APPLICATION_JSON)
                .content(String.format(especial, 2)));
        verify(configuracionExcepcionalService, never()).eliminarConfiguracion(anyInt());
        verify(configuracionExcepcionalService, never()).saveOrUpdateDTO(any());
        verify(configuracionExcepcionalService, never())
                .crearAtencionEspecialDTO(any(), any(), anyInt(), any(), any(), any());

        pedir(200, admin, delete("/api/agenda/dia-excepcional/1"));
        pedir(200, admin, post("/api/agenda/dia-excepcional").contentType(MediaType.APPLICATION_JSON)
                .content(String.format(especial, 1)));
        verify(configuracionExcepcionalService).eliminarConfiguracion(1);
        verify(configuracionExcepcionalService)
                .crearAtencionEspecialDTO(any(), any(), eq(1), any(), any(), any());
    }

    @Test
    void elListadoDeDiasExcepcionalesNoMuestraLosDeOtroCentro() throws Exception {
        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);
        MockHttpServletRequestBuilder listado = get("/api/agenda/dias-excepcionales")
                .param("fechaInicio", LocalDate.now().toString()).param("fechaFin", LocalDate.now().toString());

        String cuerpo = pedir(200, admin, listado);
        assertTrue(cuerpo.contains("FERIADO"), cuerpo);
        assertTrue(cuerpo.contains("\"centroAtencionId\":1"), cuerpo);
        assertFalse(cuerpo.contains("\"centroAtencionId\":2"), cuerpo);
        assertFalse(cuerpo.contains("\"centroAtencionId\":3"), cuerpo);

        pedir(403, admin, get("/api/agenda/dias-excepcionales").param("fechaInicio", LocalDate.now().toString())
                .param("fechaFin", LocalDate.now().toString()).param("centroId", "2"));
    }

    // SEC-A16: operadores

    @Test
    void elOperadorNoLeeNiDesactivaOperadoresDeOtroCentro() throws Exception {
        String operador = token(Role.OPERADOR, "operador1@turnero.com", 1);

        pedir(403, operador, get("/api/operadores/dni/1002"));
        pedir(403, operador, get("/api/operadores/by-email/operador2@turnero.com"));
        pedir(403, operador, delete("/api/operadores/2/logico"));
        verify(operadorService, never()).deleteLogico(2L);

        pedir(200, operador, get("/api/operadores/dni/1001"));
        pedir(200, operador, get("/api/operadores/by-email/operador1@turnero.com"));
        pedir(200, operador, delete("/api/operadores/1/logico"));
        verify(operadorService).deleteLogico(1L);
    }

    @Test
    void elSuperadminOperaOperadoresDeCualquierCentro() throws Exception {
        String superadmin = token(Role.SUPERADMIN, "root@turnero.com", null);
        pedir(200, superadmin, get("/api/operadores/dni/1002"));
        pedir(200, superadmin, delete("/api/operadores/2/logico"));
        verify(operadorService).deleteLogico(2L);
    }

    @Test
    void elPacienteNoTieneAccesoGlobal() {
        User paciente = new User();
        paciente.setRole(Role.PACIENTE);
        User superadmin = new User();
        superadmin.setRole(Role.SUPERADMIN);

        assertFalse(paciente.tieneAccesoGlobal());
        assertFalse(paciente.perteneceACentro(1));
        assertTrue(superadmin.tieneAccesoGlobal());
        assertTrue(superadmin.perteneceACentro(1));
    }

    // SEC-M2: rutas de /api/admins que consultan o actúan sobre toda la plataforma

    @Test
    void elAdministradorDeUnCentroNoConsultaMailsNiDisparaInvitacionesDeTodaLaPlataforma() throws Exception {
        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 1);

        pedir(403, admin, get("/api/admins/check-email").param("email", "alguien@turnero.com"));
        pedir(403, admin, post("/api/admins/test-encuesta-invitaciones"));
        verify(userService, never()).existsByEmail(any());
        verify(encuestaInvitacionService, never()).procesarInvitacionesPendientesForzandoEnvio();

        // El conteo de usuarios activos es del centro y lo usa su panel.
        pedir(200, admin, get("/api/admins/active-count"));

        String superadmin = token(Role.SUPERADMIN, "root@turnero.com", null);
        pedir(200, superadmin, get("/api/admins/check-email").param("email", "alguien@turnero.com"));
        pedir(200, superadmin, post("/api/admins/test-encuesta-invitaciones"));
        verify(encuestaInvitacionService).procesarInvitacionesPendientesForzandoEnvio();
    }

    private static CentroAtencion centro(int id) {
        CentroAtencion centro = new CentroAtencion();
        centro.setId(id);
        return centro;
    }

    private static EsquemaTurno esquema(int centroId) {
        Consultorio consultorio = new Consultorio();
        consultorio.setCentroAtencion(centro(centroId));
        StaffMedico staff = new StaffMedico();
        staff.setId(centroId);
        staff.setCentroAtencion(centro(centroId));
        EsquemaTurno esquema = new EsquemaTurno();
        esquema.setId(centroId);
        esquema.setConsultorio(consultorio);
        esquema.setCentroAtencion(centro(centroId));
        esquema.setStaffMedico(staff);
        return esquema;
    }

    private static TurnoDTO slotOcupado(int centroId) {
        TurnoDTO slot = new TurnoDTO();
        slot.setFecha(LocalDate.now().plusDays(1));
        slot.setCentroId(centroId);
        slot.setEsSlot(true);
        slot.setOcupado(true);
        slot.setPacienteId(77);
        slot.setNombrePaciente(NOMBRE_PACIENTE);
        slot.setApellidoPaciente("Gallo");
        return slot;
    }

    private String token(Role role, String email, Integer centroId) throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        user.setHashedPassword("hash");
        if (centroId != null) {
            user.setCentroAtencion(centro(centroId));
        }
        when(userService.loadUserByUsername(email)).thenReturn(user);
        Field expiration = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        expiration.setAccessible(true);
        if (expiration.getLong(jwtTokenProvider) <= 0) {
            expiration.setLong(jwtTokenProvider, 900_000L);
        }
        return jwtTokenProvider.generateAccessToken(user);
    }

    private String pedir(int esperado, String bearer, MockHttpServletRequestBuilder request) throws Exception {
        var respuesta = mvc.perform(request.accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + bearer)).andReturn().getResponse();
        String cuerpo = respuesta.getContentAsString();
        assertEquals(esperado, respuesta.getStatus(),
                request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI()
                        + " -> " + cuerpo);
        if (esperado == 403) {
            assertTrue(cuerpo.contains("\"status_code\":403"), cuerpo);
        }
        return cuerpo;
    }
}
