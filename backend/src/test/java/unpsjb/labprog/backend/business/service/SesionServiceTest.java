package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import unpsjb.labprog.backend.business.repository.RefreshSessionRepository;
import unpsjb.labprog.backend.business.repository.UserRepository;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.model.RefreshSession;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class SesionServiceTest {

    private static final byte[] CLAVE = new byte[48];
    private static final String EMAIL = "paciente@cheturno.test";

    private final Map<String, RefreshSession> filas = new HashMap<>();
    private JwtTokenProvider provider;
    private SesionService service;
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
        user.setEmail(EMAIL);
        user.setRole(Role.PACIENTE);
        user.setHashedPassword("hash");

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        RefreshSessionRepository repo = mock(RefreshSessionRepository.class);
        when(repo.save(any(RefreshSession.class))).thenAnswer(inv -> {
            RefreshSession fila = inv.getArgument(0);
            filas.put(fila.getJti(), fila);
            return fila;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(filas.get(inv.<String>getArgument(0))));
        when(repo.deleteByFamilyId(anyString())).thenAnswer(inv -> {
            String familia = inv.getArgument(0);
            int antes = filas.size();
            filas.values().removeIf(fila -> fila.getFamilyId().equals(familia));
            return antes - filas.size();
        });
        when(repo.deleteByUserId(anyLong())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            int antes = filas.size();
            filas.values().removeIf(fila -> fila.getUserId().equals(userId));
            return antes - filas.size();
        });

        service = new SesionService();
        ReflectionTestUtils.setField(service, "jwtTokenProvider", provider);
        ReflectionTestUtils.setField(service, "refreshSessionRepository", repo);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
    }

    @Test
    void renovarEntregaOtroRefreshDeLaMismaFamilia() {
        SesionService.Tokens login = service.abrir(user);

        SesionService.Tokens renovado = service.renovar(login.refreshToken()).orElseThrow();

        assertNotEquals(login.refreshToken(), renovado.refreshToken());
        assertTrue(provider.validateToken(renovado.accessToken(), user));
        assertEquals(2, filas.size());
        assertEquals(1, filas.values().stream().map(RefreshSession::getFamilyId).distinct().count());
        assertEquals(1, filas.values().stream().filter(fila -> fila.getRotatedAt() != null).count());
    }

    @Test
    void dosRenovacionesSeguidasConElMismoRefreshEntranPorElMargenDeGracia() {
        SesionService.Tokens login = service.abrir(user);

        assertTrue(service.renovar(login.refreshToken()).isPresent());
        assertTrue(service.renovar(login.refreshToken()).isPresent());
    }

    @Test
    void reusarUnRefreshRotadoFueraDelMargenCierraTodaLaSesion() {
        SesionService.Tokens login = service.abrir(user);
        SesionService.Tokens renovado = service.renovar(login.refreshToken()).orElseThrow();
        String jtiViejo = provider.leerRefreshToken(login.refreshToken()).orElseThrow().jti();
        filas.get(jtiViejo).setRotatedAt(Instant.now().minus(SesionService.GRACIA_ROTACION).minusSeconds(5));

        assertTrue(service.renovar(login.refreshToken()).isEmpty());
        assertTrue(filas.isEmpty());
        assertTrue(service.renovar(renovado.refreshToken()).isEmpty(),
                "el token vigente de esa familia también tiene que caer");
    }

    @Test
    void otraSesionDelMismoUsuarioNoSeVeAfectadaPorElReuso() {
        SesionService.Tokens celular = service.abrir(user);
        SesionService.Tokens notebook = service.abrir(user);
        service.renovar(celular.refreshToken()).orElseThrow();
        String jtiViejo = provider.leerRefreshToken(celular.refreshToken()).orElseThrow().jti();
        filas.get(jtiViejo).setRotatedAt(Instant.now().minus(SesionService.GRACIA_ROTACION).minusSeconds(5));

        assertTrue(service.renovar(celular.refreshToken()).isEmpty());
        assertTrue(service.renovar(notebook.refreshToken()).isPresent());
    }

    @Test
    void logoutRevocaElRefresh() {
        SesionService.Tokens login = service.abrir(user);
        SesionService.Tokens renovado = service.renovar(login.refreshToken()).orElseThrow();

        service.cerrar(renovado.refreshToken());

        assertTrue(service.renovar(renovado.refreshToken()).isEmpty());
        assertTrue(service.renovar(login.refreshToken()).isEmpty());
    }

    @Test
    void cambiarLaPasswordCortaAccessYRefresh() {
        SesionService.Tokens login = service.abrir(user);

        service.invalidarTodas(user);

        assertFalse(provider.validateToken(login.accessToken(), user));
        assertTrue(service.renovar(login.refreshToken()).isEmpty());
        assertTrue(provider.validateToken(service.abrir(user).accessToken(), user));
    }

    @Test
    void unaCuentaDeshabilitadaNoRenuevaNiAunqueLaVuelvanAHabilitar() {
        SesionService.Tokens login = service.abrir(user);

        user.disable();
        assertFalse(provider.validateToken(login.accessToken(), user));
        assertTrue(service.renovar(login.refreshToken()).isEmpty());

        user.enable();
        assertFalse(provider.validateToken(login.accessToken(), user));
        assertTrue(service.renovar(login.refreshToken()).isEmpty());
    }

    @Test
    void unAccessTokenNoSirveParaRenovar() {
        SesionService.Tokens login = service.abrir(user);

        assertTrue(service.renovar(login.accessToken()).isEmpty());
        assertTrue(service.renovar("no-es-un-jwt").isEmpty());
        assertTrue(service.renovar(null).isEmpty());
    }

    @Test
    void unRefreshConJtiDesconocidoNoRenueva() {
        String inventado = provider.generateRefreshToken(user, "jti-que-nadie-registro");

        assertTrue(service.renovar(inventado).isEmpty());
    }

    @Test
    void unRefreshAnteriorAlRegistroDeSesionesEntraYQuedaRegistrado() {
        String viejo = Jwts.builder()
                .subject(EMAIL)
                .claim("tokenType", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE), Jwts.SIG.HS256)
                .compact();

        SesionService.Tokens renovado = service.renovar(viejo).orElseThrow();

        assertEquals(1, filas.size());
        assertTrue(service.renovar(renovado.refreshToken()).isPresent());

        user.invalidarSesiones();
        assertTrue(service.renovar(viejo).isEmpty(), "un cambio de contraseña también invalida los viejos");
    }
}
