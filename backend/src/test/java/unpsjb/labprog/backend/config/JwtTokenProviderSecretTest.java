package unpsjb.labprog.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class JwtTokenProviderSecretTest {

    private static final String CLAVE_VALIDA = Base64.getEncoder().encodeToString(new byte[48]);

    @Test
    void sinClaveYFueraDeModoDesarrolloNoArranca() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider("", false).afterPropertiesSet());
        assertTrue(error.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void unaClaveEnBlancoCuentaComoAusente() {
        assertThrows(IllegalStateException.class, () -> provider("   ", false).afterPropertiesSet());
    }

    @Test
    void unaClaveCortaNoArranca() {
        String corta = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider(corta, false).afterPropertiesSet());
        assertTrue(error.getMessage().contains("demasiado corta"));
    }

    @Test
    void unaClaveQueNoEsBase64NoArranca() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider("cambiar-por-un-secreto-largo", false).afterPropertiesSet());
        assertTrue(error.getMessage().contains("Base64"));
    }

    @Test
    void elErrorNoRepiteLaClaveConfigurada() {
        String corta = Base64.getEncoder().encodeToString("clave-secreta".getBytes());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider(corta, false).afterPropertiesSet());
        assertFalse(error.getMessage().contains(corta));
    }

    @Test
    void conUnaClavePropiaFirmaYValidaTokens() {
        JwtTokenProvider provider = provider(CLAVE_VALIDA, false);
        provider.afterPropertiesSet();

        String token = provider.generateAccessToken(usuario());

        assertEquals("medico@cheturno.test", provider.extractUsername(token));
    }

    @Test
    void enModoDesarrolloSinClaveGeneraUnaTemporalDistintaEnCadaArranque() {
        JwtTokenProvider primero = provider("", true);
        primero.afterPropertiesSet();
        JwtTokenProvider segundo = provider("", true);
        segundo.afterPropertiesSet();

        String token = primero.generateAccessToken(usuario());

        assertEquals("medico@cheturno.test", primero.extractUsername(token));
        assertThrows(RuntimeException.class, () -> segundo.extractUsername(token));
    }

    private static JwtTokenProvider provider(String clave, boolean devMode) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", clave);
        ReflectionTestUtils.setField(provider, "devMode", devMode);
        ReflectionTestUtils.setField(provider, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpiration", 604_800_000L);
        return provider;
    }

    private static User usuario() {
        User user = new User();
        user.setId(7L);
        user.setEmail("medico@cheturno.test");
        user.setRole(Role.MEDICO);
        user.setHashedPassword("hash");
        return user;
    }
}
