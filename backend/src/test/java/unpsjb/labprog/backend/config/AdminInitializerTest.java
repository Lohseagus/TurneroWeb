package unpsjb.labprog.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class AdminInitializerTest {

    private static final String EMAIL = "root@cheturno.test";
    private static final String PASSWORD_VALIDA = "una-clave-larga-de-prueba";

    private UserService userService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        passwordEncoder = mock(PasswordEncoder.class);
    }

    @Test
    void apagadoNoTocaLaBase() throws Exception {
        initializer(false, PASSWORD_VALIDA).run();

        verifyNoInteractions(userService, passwordEncoder);
    }

    @Test
    void prendidoSinPasswordNoCreaNada() throws Exception {
        initializer(true, "").run();

        verifyNoInteractions(userService, passwordEncoder);
    }

    @Test
    void prendidoConPasswordCortaNoCreaNada() throws Exception {
        initializer(true, "corta123").run();

        verifyNoInteractions(userService, passwordEncoder);
    }

    @Test
    void prendidoConPasswordPropiaCreaElSuperadmin() throws Exception {
        when(userService.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD_VALIDA)).thenReturn("hash");

        initializer(true, PASSWORD_VALIDA).run();

        ArgumentCaptor<User> creado = ArgumentCaptor.forClass(User.class);
        verify(userService).save(creado.capture());
        assertEquals(EMAIL, creado.getValue().getEmail());
        assertEquals(Role.SUPERADMIN, creado.getValue().getRole());
        assertEquals("hash", creado.getValue().getHashedPassword());
    }

    @Test
    void siLaCuentaYaExisteNoLaPisa() throws Exception {
        when(userService.existsByEmail(EMAIL)).thenReturn(true);

        initializer(true, PASSWORD_VALIDA).run();

        verify(userService, never()).save(any(User.class));
    }

    @Test
    void elMotivoDeRechazoNoRepiteLaPassword() {
        assertNull(AdminInitializer.motivoDeRechazo(PASSWORD_VALIDA));
        assertNotNull(AdminInitializer.motivoDeRechazo(null));
        assertNotNull(AdminInitializer.motivoDeRechazo("   "));

        String motivo = AdminInitializer.motivoDeRechazo("corta123");
        assertNotNull(motivo);
        assertEquals(false, motivo.contains("corta123"));
    }

    private AdminInitializer initializer(boolean enabled, String password) {
        AdminInitializer initializer = new AdminInitializer();
        ReflectionTestUtils.setField(initializer, "adminSeedEnabled", enabled);
        ReflectionTestUtils.setField(initializer, "adminEmail", EMAIL);
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
        ReflectionTestUtils.setField(initializer, "adminNombre", "Administrador");
        ReflectionTestUtils.setField(initializer, "adminApellido", "Sistema");
        ReflectionTestUtils.setField(initializer, "adminDni", 99999999L);
        ReflectionTestUtils.setField(initializer, "adminTelefono", "+54-9-11-0000-0000");
        ReflectionTestUtils.setField(initializer, "userService", userService);
        ReflectionTestUtils.setField(initializer, "passwordEncoder", passwordEncoder);
        return initializer;
    }
}
