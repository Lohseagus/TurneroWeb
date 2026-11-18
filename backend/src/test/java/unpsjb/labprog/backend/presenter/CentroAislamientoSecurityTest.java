package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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

import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.service.CentroAccessGuard;
import unpsjb.labprog.backend.business.service.ConsultorioService;
import unpsjb.labprog.backend.business.service.DisponibilidadMedicoService;
import unpsjb.labprog.backend.business.service.EsquemaTurnoService;
import unpsjb.labprog.backend.business.service.ExportService;
import unpsjb.labprog.backend.business.service.PacienteAccessGuard;
import unpsjb.labprog.backend.business.service.PacienteService;
import unpsjb.labprog.backend.business.service.RecordatorioService;
import unpsjb.labprog.backend.business.service.StaffMedicoService;
import unpsjb.labprog.backend.business.service.TurnoAutomationService;
import unpsjb.labprog.backend.business.service.OperadorResumenService;
import unpsjb.labprog.backend.business.service.TurnoService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Consultorio;
import unpsjb.labprog.backend.model.DisponibilidadMedico;
import unpsjb.labprog.backend.model.EsquemaTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

/**
 * Lote 4 de la auditoría (SEC-A7, SEC-A8 y recursos del centro por id), con
 * security.dev.mode=false como en producción y cuentas de tres centros.
 *
 * Cada recurso de prueba tiene el id de su centro: el turno 2, el consultorio 2 y el
 * staff 2 son del centro 2.
 */
@WebMvcTest(controllers = {TurnoPresenter.class, ConsultorioPresenter.class, DisponibilidadMedicoPresenter.class,
        EsquemaTurnoPresenter.class, StaffMedicoPresenter.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        PacienteAccessGuard.class, CentroAccessGuard.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "DB_URL=jdbc:h2:mem:centros",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class CentroAislamientoSecurityTest {

    private static final int PACIENTE_PROPIO = 10;
    private static final int PACIENTE_AJENO = 11;
    /** Turno del centro 1 cuyo paciente es PACIENTE_AJENO. */
    private static final int TURNO_DE_OTRO_PACIENTE = 4;
    private static final int MEDICO_ID = 50;
    private static final String EMAIL_PACIENTE = "paciente@turnero.com";
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
    private TurnoService turnoService;
    @MockitoBean
    private OperadorResumenService operadorResumenService;
    @MockitoBean
    private ExportService exportService;
    @MockitoBean
    private RecordatorioService recordatorioService;
    @MockitoBean
    private TurnoAutomationService turnoAutomationService;
    @MockitoBean
    private ConsultorioService consultorioService;
    @MockitoBean
    private DisponibilidadMedicoService disponibilidadService;
    @MockitoBean
    private EsquemaTurnoService esquemaTurnoService;
    @MockitoBean
    private StaffMedicoService staffMedicoService;

    @BeforeEach
    void setUp() {
        for (int centroId = 1; centroId <= 3; centroId++) {
            when(turnoRepository.findById(centroId)).thenReturn(Optional.of(turno(centroId, PACIENTE_PROPIO)));
            when(consultorioRepository.findById(centroId)).thenReturn(Optional.of(consultorio(centroId)));
            when(staffMedicoRepository.findById(centroId)).thenReturn(Optional.of(staff(centroId)));
            DisponibilidadMedico disponibilidad = new DisponibilidadMedico();
            disponibilidad.setStaffMedico(staff(centroId));
            when(disponibilidadRepository.findById(centroId)).thenReturn(Optional.of(disponibilidad));
            EsquemaTurno esquema = new EsquemaTurno();
            esquema.setCentroAtencion(centro(centroId));
            when(esquemaTurnoRepository.findById(centroId)).thenReturn(Optional.of(esquema));
        }
        when(turnoRepository.findById(TURNO_DE_OTRO_PACIENTE)).thenReturn(Optional.of(turno(1, PACIENTE_AJENO)));

        Paciente propio = new Paciente();
        propio.setId(PACIENTE_PROPIO);
        when(pacienteRepository.findByUserEmail(EMAIL_PACIENTE)).thenReturn(Optional.of(propio));

        // El médico tiene la cuenta en el centro 1 y además atiende en el centro 3.
        Medico medico = new Medico();
        medico.setId(MEDICO_ID);
        when(medicoRepository.findByEmailIgnoreCase(EMAIL_MEDICO)).thenReturn(Optional.of(medico));
        when(staffMedicoRepository.findByMedicoId(MEDICO_ID)).thenReturn(List.of(staff(1), staff(3)));
    }

    // SEC-A7: turnos por id

    @Test
    void elOperadorSoloOperaTurnosDeSuCentro() throws Exception {
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);

        pedir(200, operador, get("/api/turno/1"));
        pedir(200, operador, put("/api/turno/1/confirmar").contentType(MediaType.APPLICATION_JSON).content("{}"));

        pedir(403, operador, get("/api/turno/2"));
        for (String accion : List.of("confirmar", "completar", "cancelar", "reagendar", "estado")) {
            pedir(403, operador, put("/api/turno/2/" + accion).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"estado\":\"COMPLETO\",\"motivo\":\"motivo de prueba\"}"));
        }
        pedir(403, operador, put("/api/turno/2/asistencia").contentType(MediaType.APPLICATION_JSON)
                .content("{\"asistio\":true}"));
        pedir(403, operador, put("/api/turno/2").contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, operador, delete("/api/turno/2"));
        for (String lectura : List.of("datos-cancelacion", "validar-contacto", "estados-validos", "audit",
                "audit/page", "audit/verify")) {
            pedir(403, operador, get("/api/turno/2/" + lectura));
        }

        verify(turnoService, never()).findById(2);
        verify(turnoService, never()).delete(eq(2), anyString(), anyString());
        verify(turnoService, never()).confirmarTurno(eq(2), anyString());
        verify(turnoService, never()).completarTurno(eq(2), anyString());
        verify(turnoService, never()).cancelarTurno(eq(2), any(), anyString());
        verify(turnoService, never()).changeEstado(eq(2), any(), any(), anyString());
        verify(turnoService, never()).save(any(), anyString());
    }

    @Test
    void elAdministradorTampocoCruzaDeCentro() throws Exception {
        String admin = token(Role.ADMINISTRADOR, "admin@turnero.com", 2);

        pedir(200, admin, get("/api/turno/2"));
        pedir(403, admin, get("/api/turno/1"));
        pedir(403, admin, delete("/api/turno/3"));
    }

    @Test
    void elMedicoOperaLosTurnosDeTodosSusCentros() throws Exception {
        String medico = token(Role.MEDICO, EMAIL_MEDICO, 1);

        pedir(200, medico, get("/api/turno/1"));
        pedir(200, medico, get("/api/turno/3"));
        pedir(200, medico, put("/api/turno/3/completar"));
        pedir(403, medico, get("/api/turno/2"));
        pedir(403, medico, put("/api/turno/2/completar"));

        verify(turnoService).completarTurno(eq(3), anyString());
        verify(turnoService, never()).completarTurno(eq(2), anyString());
    }

    @Test
    void elSuperadminNoTieneFiltro() throws Exception {
        String superadmin = token(Role.SUPERADMIN, "root@turnero.com", null);

        pedir(200, superadmin, get("/api/turno/1"));
        pedir(200, superadmin, get("/api/turno/2"));
        pedir(200, superadmin, delete("/api/turno/3"));
    }

    @Test
    void elPacienteOperaSoloSusTurnosYSoloLoQueLeCorresponde() throws Exception {
        String paciente = token(Role.PACIENTE, EMAIL_PACIENTE, null);

        // Sus turnos, en cualquier centro.
        pedir(200, paciente, get("/api/turno/2"));
        pedir(200, paciente, put("/api/turno/3/confirmar").contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(200, paciente, put("/api/turno/1/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"no puedo asistir\"}"));

        // El turno de otro paciente.
        pedir(403, paciente, get("/api/turno/" + TURNO_DE_OTRO_PACIENTE));
        pedir(403, paciente, put("/api/turno/" + TURNO_DE_OTRO_PACIENTE + "/confirmar")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, paciente, put("/api/turno/" + TURNO_DE_OTRO_PACIENTE + "/cancelar")
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no puedo asistir\"}"));
        pedir(403, paciente, put("/api/turno/" + TURNO_DE_OTRO_PACIENTE + "/reagendar")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        // Lo que sigue siendo solo del personal, aunque el turno sea suyo.
        pedir(403, paciente, delete("/api/turno/1"));
        pedir(403, paciente, put("/api/turno/1").contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, paciente, put("/api/turno/1/completar"));
        pedir(403, paciente, put("/api/turno/1/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"COMPLETO\"}"));
        pedir(403, paciente, get("/api/turno/1/audit"));
        pedir(403, paciente, get("/api/turno"));
        pedir(403, paciente, get("/api/turno/page"));
        pedir(403, paciente, get("/api/turno/search"));
        pedir(403, paciente, get("/api/turno/completados"));

        verify(turnoService, never()).confirmarTurno(eq(TURNO_DE_OTRO_PACIENTE), anyString());
        verify(turnoService, never()).cancelarTurno(eq(TURNO_DE_OTRO_PACIENTE), any(), anyString());
        verify(turnoService, never()).delete(anyInt(), anyString(), anyString());
    }

    // Recursos del centro por id

    @Test
    void elOperadorNoTocaConsultoriosDeOtroCentro() throws Exception {
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);

        pedir(200, operador, get("/api/consultorios/1"));
        pedir(403, operador, get("/api/consultorios/2"));
        pedir(403, operador, put("/api/consultorios/2").contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, operador, delete("/api/consultorios/2"));
        // Mover un consultorio propio a otro centro, o crearlo allá.
        pedir(403, operador, put("/api/consultorios/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"centroId\":2}"));
        pedir(403, operador, post("/api/consultorios/centro/2").contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, operador, get("/api/consultorios/centrosAtencion/2/consultorios"));

        verify(consultorioService, never()).delete(anyInt(), any(), any());
        verify(consultorioService, never()).saveOrUpdate(any(), any());
    }

    @Test
    void elOperadorNoTocaStaffNiEsquemasDeOtroCentro() throws Exception {
        String operador = token(Role.OPERADOR, "operador@turnero.com", 1);

        pedir(403, operador, get("/api/staff-medico/2"));
        pedir(403, operador, put("/api/staff-medico/2").contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, operador, delete("/api/staff-medico/2"));
        pedir(403, operador, post("/api/staff-medico").contentType(MediaType.APPLICATION_JSON)
                .content("{\"centroAtencionId\":2}"));
        pedir(403, operador, get("/api/staff-medico/centrosAtencion/2/medicos/conPorcentajes"));
        pedir(403, operador, put("/api/staff-medico/centrosAtencion/2/medicos/porcentajes")
                .contentType(MediaType.APPLICATION_JSON).content("[]"));

        pedir(403, operador, get("/api/esquema-turno/2"));
        pedir(403, operador, put("/api/esquema-turno/2").contentType(MediaType.APPLICATION_JSON).content("{}"));
        pedir(403, operador, delete("/api/esquema-turno/2"));
        pedir(403, operador, post("/api/esquema-turno").contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffMedicoId\":2}"));
        pedir(403, operador, post("/api/esquema-turno").contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffMedicoId\":1,\"consultorioId\":2}"));
        pedir(403, operador, get("/api/esquema-turno/staff/2"));
        pedir(403, operador, post("/api/esquema-turno/centrosAtencion/2/redistribuir"));

        verifyNoInteractions(staffMedicoService);
        verifyNoInteractions(esquemaTurnoService);
    }

    @Test
    void elMedicoEditaSusHorariosEnTodosSusCentrosYNoLosDeOtro() throws Exception {
        String medico = token(Role.MEDICO, EMAIL_MEDICO, 1);

        pedir(200, medico, get("/api/disponibilidades-medico/1"));
        pedir(200, medico, get("/api/disponibilidades-medico/3"));
        pedir(200, medico, delete("/api/disponibilidades-medico/3"));
        pedir(200, medico, get("/api/disponibilidades-medico/staffMedico/3"));

        pedir(403, medico, get("/api/disponibilidades-medico/2"));
        pedir(403, medico, delete("/api/disponibilidades-medico/2"));
        pedir(403, medico, get("/api/disponibilidades-medico/staffMedico/2"));
        pedir(403, medico, post("/api/disponibilidades-medico").contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffMedicoId\":2}"));
        pedir(403, medico, put("/api/disponibilidades-medico/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffMedicoId\":2}"));

        verify(disponibilidadService).deleteById(3);
        verify(disponibilidadService, never()).deleteById(2);
        verify(disponibilidadService, never()).saveOrUpdate(any());
    }

    private static CentroAtencion centro(int id) {
        CentroAtencion centro = new CentroAtencion();
        centro.setId(id);
        return centro;
    }

    private static Turno turno(int centroId, int pacienteId) {
        Paciente paciente = new Paciente();
        paciente.setId(pacienteId);
        Turno turno = new Turno();
        turno.setCentroAtencion(centro(centroId));
        turno.setPaciente(paciente);
        return turno;
    }

    private static Consultorio consultorio(int centroId) {
        Consultorio consultorio = new Consultorio();
        consultorio.setCentroAtencion(centro(centroId));
        return consultorio;
    }

    private static StaffMedico staff(int centroId) {
        StaffMedico staff = new StaffMedico();
        staff.setCentroAtencion(centro(centroId));
        return staff;
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
