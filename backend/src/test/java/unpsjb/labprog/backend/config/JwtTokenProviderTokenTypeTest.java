package unpsjb.labprog.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class JwtTokenProviderTokenTypeTest {

    private static final byte[] CLAVE = new byte[48];

    private JwtTokenProvider provider;
    private User user;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", Base64.getEncoder().encodeToString(CLAVE));
        ReflectionTestUtils.setField(provider, "devMode", false);
        ReflectionTestUtils.setField(provider, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpiration", 604_800_000L);
        provider.afterPropertiesSet();

        user = new User();
        user.setId(7L);
        user.setEmail("medico@cheturno.test");
        user.setRole(Role.MEDICO);
        user.setHashedPassword("hash");
    }

    @Test
    void elAccessTokenValidaComoAccessYNoComoRefresh() {
        String access = provider.generateAccessToken(user);

        assertTrue(provider.validateToken(access, user));
        assertFalse(provider.validateRefreshToken(access));
    }

    @Test
    void elRefreshTokenValidaComoRefreshYNoComoAccess() {
        String refresh = provider.generateRefreshToken(user);

        assertTrue(provider.validateRefreshToken(refresh));
        assertFalse(provider.validateToken(refresh, user));
    }

    @Test
    void unAccessEmitidoAntesDelClaimSigueValiendo() {
        String sinTipo = Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", 7L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE), Jwts.SIG.HS256)
                .compact();

        assertTrue(provider.validateToken(sinTipo, user));
        assertFalse(provider.validateRefreshToken(sinTipo));
    }

    @Test
    void unTipoDesconocidoNoPasaPorAccess() {
        String raro = Jwts.builder()
                .subject(user.getEmail())
                .claim("tokenType", "otro")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE), Jwts.SIG.HS256)
                .compact();

        assertFalse(provider.validateToken(raro, user));
    }
}
