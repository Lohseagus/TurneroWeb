package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.lang.reflect.Field;

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

import jakarta.servlet.http.HttpServletRequest;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.config.JwtAuthenticationFilter;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.SecurityConfig;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

@WebMvcTest(controllers = ProbeEscrituraController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, SaasEnforcement.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@TestPropertySource(properties = {
        "security.dev.mode=false",
        "jwt.secret=BIKxlr6HZ0lJtDo0GAAWww+bJF7cyNHwj1XrLGjdgQg=",
        "jwt.access-token-expiration=900000",
        "app.allowed.origins=http://localhost:4200",
        "saas.enforcement.enabled=true",
        "DB_URL=jdbc:h2:mem:sololectura",
        "DB_USERNAME=sa",
        "DB_PASSWORD=sa"
})
class SuscripcionSoloLecturaWebTest {

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
    void centroEnSoloLectura() {
        when(suscripcionAccessService.evaluarEscritura(any()))
                .thenReturn(ResultadoEscritura.bloquear(
                        SuscripcionAccessService.mensajeSoloLectura(EstadoSuscripcion.VENCIDA), false));
        when(centroObjetivoResolver.resolver(any(), any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            return new CentroObjetivoResolver.Resolucion(1, request);
        });
    }

    @Test
    void cadaEstadoQueNoEscribeResponde403ConElCodigo() throws Exception {
        for (EstadoSuscripcion estado : EstadoSuscripcion.values()) {
            if (estado.permiteEscribir()) {
                continue;
            }
            when(suscripcionAccessService.evaluarEscritura(1))
                    .thenReturn(ResultadoEscritura.bloquear(
                            SuscripcionAccessService.mensajeSoloLectura(estado), false));
            MockHttpServletResponse respuesta = pedir(post("/api/probe")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"), token(Role.OPERADOR, 1));
            assertEquals(403, respuesta.getStatus(), estado.name());
            assertTrue(respuesta.getContentAsString().contains("\"code\":\"SUSCRIPCION_SOLO_LECTURA\""), estado.name());
            assertTrue(respuesta.getContentAsString().contains("solo lectura"), estado.name());
        }
    }

    @Test
    void trialYActivaDejanEscribir() throws Exception {
        when(suscripcionAccessService.evaluarEscritura(1)).thenReturn(ResultadoEscritura.permitir());
        for (Role rol : new Role[] {Role.ADMINISTRADOR, Role.MEDICO, Role.OPERADOR}) {
            MockHttpServletResponse respuesta = pedir(post("/api/probe")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"), token(rol, 1));
            assertEquals(200, respuesta.getStatus(), rol.name());
        }
    }

    @Test
    void unCentroSinSuscripcionTambienQuedaEnSoloLectura() throws Exception {
        when(suscripcionAccessService.evaluarEscritura(1))
                .thenReturn(ResultadoEscritura.bloquear(SuscripcionAccessService.mensajeSinSuscripcion(), true));

        MockHttpServletResponse respuesta = pedir(post("/api/probe")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), token(Role.ADMINISTRADOR, 1));

        assertEquals(403, respuesta.getStatus());
        assertTrue(respuesta.getContentAsString().contains("SUSCRIPCION_SOLO_LECTURA"));
        assertTrue(respuesta.getContentAsString().contains("no tiene una suscripción"));
    }

    @Test
    void elSuperadminNoQuedaBloqueado() throws Exception {
        MockHttpServletResponse respuesta = pedir(post("/api/probe")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), token(Role.SUPERADMIN, null));

        assertEquals(200, respuesta.getStatus());
        verify(suscripcionAccessService, never()).evaluarEscritura(any());
    }

    @Test
    void unaLecturaSigueAunqueElCentroEsteVencido() throws Exception {
        MockHttpServletResponse respuesta = pedir(get("/api/probe"), token(Role.MEDICO, 1));

        assertEquals(200, respuesta.getStatus());
        verify(suscripcionAccessService, never()).evaluarEscritura(any());
    }

    @Test
    void laListaBlancaDejaPasarSesionCobroWebhookYVerificacion() throws Exception {
        String admin = token(Role.ADMINISTRADOR, 1);
        String superadmin = token(Role.SUPERADMIN, null);
        assertEquals(200, pedir(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"), null).getStatus());
        assertEquals(200, pedir(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"), admin).getStatus());
        assertEquals(200, pedir(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"), admin).getStatus());
        assertEquals(200, pedir(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content("{}"), admin).getStatus());
        assertEquals(200, pedir(post("/api/saas/registro").contentType(MediaType.APPLICATION_JSON).content("{}"), null).getStatus());
        assertEquals(200, pedir(post("/api/saas/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON).content("{}"), null).getStatus());
        assertEquals(200, pedir(post("/api/saas/mi-suscripcion/pagar").contentType(MediaType.APPLICATION_JSON).content("{}"), admin).getStatus());
        assertEquals(200, pedir(post("/api/saas/admin/suscripciones/1/suspender").contentType(MediaType.APPLICATION_JSON).content("{}"), superadmin).getStatus());
        assertEquals(200, pedir(get("/api/recetas/verificar/token-publico"), null).getStatus());
        assertEquals(200, pedir(get("/api/ordenes-estudio/verificar/token-publico"), null).getStatus());
        assertEquals(200, pedir(post("/api/turno/search").contentType(MediaType.APPLICATION_JSON).content("{}"), admin).getStatus());
        verify(suscripcionAccessService, never()).evaluarEscritura(any());
    }

    @Test
    void elPacienteQueReservaEnUnCentroVencidoRecibeElMensajeDeTurnos() throws Exception {
        MockHttpServletResponse respuesta = pedir(post("/api/turno/asignar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"centroId\":1}"), token(Role.PACIENTE, null));

        assertEquals(403, respuesta.getStatus());
        String cuerpo = respuesta.getContentAsString();
        assertTrue(cuerpo.contains("SUSCRIPCION_SOLO_LECTURA"));
        assertTrue(cuerpo.contains("no está tomando turnos"));
        assertFalse(cuerpo.contains("no se pueden crear turnos ni recursos nuevos"));
    }

    @Test
    void elPacienteNoCancelaUnTurnoDeUnCentroSinSuscripcion() throws Exception {
        when(suscripcionAccessService.evaluarEscritura(1))
                .thenReturn(ResultadoEscritura.bloquear(SuscripcionAccessService.mensajeSinSuscripcion(), true));

        MockHttpServletResponse respuesta = pedir(put("/api/turno/9/cancelar")
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no voy\"}"),
                token(Role.PACIENTE, null));

        assertEquals(403, respuesta.getStatus());
        assertTrue(respuesta.getContentAsString().contains("no tiene una suscripción activa"));
        assertTrue(respuesta.getContentAsString().contains("no reservar, confirmar, cancelar ni reagendar"));
    }

    private MockHttpServletResponse pedir(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private String token(Role role, Integer centroId) throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail(role.name().toLowerCase() + "@cheturno.test");
        user.setRole(role);
        user.setHashedPassword("hash");
        if (centroId != null) {
            CentroAtencion centro = new CentroAtencion();
            centro.setId(centroId);
            user.setCentroAtencion(centro);
        }
        when(userService.loadUserByUsername(user.getEmail())).thenReturn(user);
        Field expiration = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        expiration.setAccessible(true);
        if (expiration.getLong(jwtTokenProvider) <= 0) {
            expiration.setLong(jwtTokenProvider, 900_000L);
        }
        return jwtTokenProvider.generateAccessToken(user);
    }
}
