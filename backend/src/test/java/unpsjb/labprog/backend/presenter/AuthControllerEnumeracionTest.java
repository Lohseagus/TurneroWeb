package unpsjb.labprog.backend.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import unpsjb.labprog.backend.business.service.GoogleAuthService;
import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.dto.CheckEmailRequest;
import unpsjb.labprog.backend.dto.CheckEmailResponse;
import unpsjb.labprog.backend.dto.GoogleLoginRequest;
import unpsjb.labprog.backend.dto.LoginRequest;

/**
 * SEC-M1: lo que responde el login sin sesión no puede decir si un email tiene cuenta.
 */
class AuthControllerEnumeracionTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final UserService userService = mock(UserService.class);
    private final AuthController controller = new AuthController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(controller, "userService", userService);
    }

    @Test
    void checkEmailNoConsultaUsuariosNiDevuelveNombreORol() {
        Map<String, Object> cuerpo = cuerpo(controller.checkEmail(new CheckEmailRequest("quien.sea@example.com")));

        assertEquals(200, cuerpo.get("status_code"));
        CheckEmailResponse data = (CheckEmailResponse) cuerpo.get("data");
        assertEquals("quien.sea@example.com", data.getEmail());
        assertNull(data.getNombre());
        assertNull(data.getRole());
        verifyNoInteractions(userService);
    }

    @Test
    void checkEmailRechazaUnEmailMalFormado() {
        assertEquals(400, cuerpo(controller.checkEmail(new CheckEmailRequest("no-es-un-email"))).get("status_code"));
        assertEquals(400, cuerpo(controller.checkEmail(new CheckEmailRequest(null))).get("status_code"));
    }

    @Test
    void elLoginRespondeIgualSiLaClaveEstaMalOLaCuentaEstaDeshabilitadaOBloqueada() {
        Map<String, Object> claveMal = loginCon(new BadCredentialsException("Bad credentials"));
        Map<String, Object> deshabilitada = loginCon(new DisabledException("User is disabled"));
        Map<String, Object> bloqueada = loginCon(new LockedException("User account is locked"));

        assertEquals(401, claveMal.get("status_code"));
        assertEquals("Credenciales inválidas", claveMal.get("status_text"));
        assertEquals(claveMal, deshabilitada);
        assertEquals(claveMal, bloqueada);
    }

    @Test
    void elErrorDeGoogleEsPlanoYConElStatusReal() {
        GoogleAuthService google = mock(GoogleAuthService.class);
        ReflectionTestUtils.setField(controller, "googleAuthService", google);
        when(google.verifyTokenAndGetPayload(any())).thenReturn(null);

        GoogleLoginRequest invalido = new GoogleLoginRequest();
        invalido.setIdToken("token-roto");
        ResponseEntity<?> rechazado = controller.authenticateWithGoogle(invalido);
        assertEquals(401, rechazado.getStatusCode().value());
        assertFalse(rechazado.getBody() instanceof ResponseEntity);
        Map<String, Object> plano = cuerpoDe(rechazado);
        assertEquals(401, plano.get("status_code"));
        assertEquals("Token de Google inválido.", plano.get("status_text"));

        when(google.verifyTokenAndGetPayload(any())).thenReturn(mock(GoogleIdToken.Payload.class));
        User deshabilitado = new User("Ana", "Paz", 1L, "ana@turnero.com", "hash", "2804000000");
        deshabilitado.disable();
        when(userService.processGoogleUser(any())).thenReturn(deshabilitado);

        GoogleLoginRequest pedido = new GoogleLoginRequest();
        pedido.setIdToken("token-valido");
        ResponseEntity<?> cuenta = controller.authenticateWithGoogle(pedido);
        assertEquals(403, cuenta.getStatusCode().value());
        assertFalse(cuenta.getBody() instanceof ResponseEntity);
        assertEquals("La cuenta está deshabilitada.", cuerpoDe(cuenta).get("status_text"));
        assertEquals(403, cuerpoDe(cuenta).get("status_code"));
    }

    private Map<String, Object> loginCon(AuthenticationException error) {
        doThrow(error).when(authenticationManager).authenticate(any());
        LoginRequest request = new LoginRequest();
        request.setEmail("alguien@example.com");
        request.setPassword("una-clave-cualquiera");
        return cuerpo(controller.login(request));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cuerpo(org.springframework.http.ResponseEntity<Object> respuesta) {
        return (Map<String, Object>) respuesta.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cuerpoDe(ResponseEntity<?> respuesta) {
        return (Map<String, Object>) respuesta.getBody();
    }
}
