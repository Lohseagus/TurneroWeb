package unpsjb.labprog.backend.business.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.RefreshSessionRepository;
import unpsjb.labprog.backend.business.repository.UserRepository;
import unpsjb.labprog.backend.config.JwtTokenProvider;
import unpsjb.labprog.backend.config.JwtTokenProvider.RefreshTokenData;
import unpsjb.labprog.backend.model.RefreshSession;
import unpsjb.labprog.backend.model.User;

/**
 * Emite, rota y revoca las sesiones (par access + refresh token).
 *
 * El refresh token es de un solo uso: cada renovación entrega uno nuevo y deja
 * marcado el anterior. Los métodos devuelven vacío en vez de lanzar una
 * excepción para que la transacción confirme también cuando hay que borrar una
 * familia comprometida.
 */
@Service
public class SesionService {

    private static final Logger logger = LoggerFactory.getLogger(SesionService.class);

    /**
     * Dos pestañas, o el timer y un pedido, pueden renovar a la vez con el mismo
     * refresh. Durante este margen el token recién rotado todavía se acepta.
     */
    static final Duration GRACIA_ROTACION = Duration.ofSeconds(60);

    public record Tokens(String accessToken, String refreshToken, User user) {
    }

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private UserRepository userRepository;

    /** Abre una sesión nueva (login, Google, deep link). */
    @Transactional
    public Tokens abrir(User user) {
        return emitir(user, UUID.randomUUID().toString());
    }

    /**
     * Cambia un refresh token por un par nuevo.
     *
     * @return vacío si el token no sirve: firma, tipo o vencimiento inválidos, cuenta
     *         deshabilitada, sesiones invalidadas, token desconocido o ya usado
     */
    @Transactional
    public Optional<Tokens> renovar(String refreshToken) {
        Optional<RefreshTokenData> leido = jwtTokenProvider.leerRefreshToken(refreshToken);
        if (leido.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenData data = leido.get();

        Optional<User> userOpt = userRepository.findByEmail(data.email());
        if (userOpt.isEmpty() || !jwtTokenProvider.sesionVigente(data.version(), userOpt.get())) {
            return Optional.empty();
        }
        User user = userOpt.get();

        if (data.jti() == null) {
            // Refresh emitido antes de registrar sesiones. No hay fila que rotar: entra
            // una vez a una familia nueva. Dejan de existir 7 días después del deploy,
            // y un cambio de contraseña los invalida igual (versión 0).
            return Optional.of(emitir(user, UUID.randomUUID().toString()));
        }

        Optional<RefreshSession> sesionOpt = refreshSessionRepository.findById(data.jti());
        if (sesionOpt.isEmpty() || !sesionOpt.get().getUserId().equals(user.getId())) {
            return Optional.empty();
        }
        RefreshSession sesion = sesionOpt.get();
        Instant ahora = Instant.now();

        if (sesion.getRotatedAt() == null) {
            sesion.setRotatedAt(ahora);
            refreshSessionRepository.save(sesion);
        } else if (sesion.getRotatedAt().plus(GRACIA_ROTACION).isBefore(ahora)) {
            int borradas = refreshSessionRepository.deleteByFamilyId(sesion.getFamilyId());
            logger.warn("Reuso de un refresh token ya rotado. Se cerró la sesión del usuario ID {} ({} tokens).",
                    user.getId(), borradas);
            return Optional.empty();
        }

        return Optional.of(emitir(user, sesion.getFamilyId()));
    }

    /**
     * Cierra la sesión a la que pertenece el refresh token (logout). El access token
     * ya emitido sigue valiendo hasta vencer, como mucho 15 minutos.
     */
    @Transactional
    public void cerrar(String refreshToken) {
        jwtTokenProvider.leerRefreshToken(refreshToken)
                .map(RefreshTokenData::jti)
                .flatMap(jti -> jti == null ? Optional.empty() : refreshSessionRepository.findById(jti))
                .ifPresent(sesion -> refreshSessionRepository.deleteByFamilyId(sesion.getFamilyId()));
    }

    /**
     * Corta todas las sesiones del usuario, en todos los dispositivos. Quien llama
     * guarda el usuario.
     */
    @Transactional
    public void invalidarTodas(User user) {
        user.invalidarSesiones();
        if (user.getId() != null) {
            refreshSessionRepository.deleteByUserId(user.getId());
        }
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void limpiarVencidas() {
        try {
            refreshSessionRepository.deleteExpired(Instant.now());
        } catch (Exception e) {
            logger.error("Error al limpiar sesiones vencidas: {}", e.getMessage());
        }
    }

    private Tokens emitir(User user, String familyId) {
        String jti = UUID.randomUUID().toString();
        Instant vence = Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMillis());
        refreshSessionRepository.save(new RefreshSession(jti, familyId, user.getId(), vence));
        return new Tokens(
                jwtTokenProvider.generateAccessToken(user),
                jwtTokenProvider.generateRefreshToken(user, jti),
                user);
    }
}
