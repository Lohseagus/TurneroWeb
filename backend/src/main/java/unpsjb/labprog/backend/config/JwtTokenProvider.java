package unpsjb.labprog.backend.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.business.service.PacienteService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proveedor de tokens JWT para autenticación
 * Maneja la creación, validación y extracción de datos de tokens JWT
 */
@Component
public class JwtTokenProvider implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    static final String CLAIM_TOKEN_TYPE = "tokenType";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    // HS256 necesita una clave de 256 bits como mínimo.
    private static final int MIN_KEY_BYTES = 32;

    // SHA-256 de la clave de ejemplo que estuvo publicada en este repositorio.
    // Cualquiera puede firmar tokens con ella, así que no se acepta en ningún ambiente.
    private static final String CLAVE_PUBLICADA_SHA256 =
            "3354cfcf65c7b2e6840ea6f880aede239750cb1d063950c4e8fd9fbf9ec73a32";

    // Clave en Base64 para firmar los tokens. Sin valor por defecto: viene de JWT_SECRET.
    @Value("${jwt.secret:}")
    private String secretKey;

    // Solo con security.dev.mode=true se permite arrancar sin JWT_SECRET.
    @Value("${security.dev.mode:false}")
    private boolean devMode;

    private SecretKey signKey;

    // Tiempo de expiración del access token (15 minutos)
    @Value("${jwt.access-token-expiration:900000}")
    private long accessTokenExpiration;

    // Tiempo de expiración del refresh token (7 días)
    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    // Servicio para buscar pacientes por email
    @Autowired
    @Lazy
    private PacienteService pacienteService;

    /**
     * Extrae el username (email) del token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrae la fecha de expiración del token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extrae el userId del token
     */
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        return null;
    }

    /**
     * Extrae el rol del token
     */
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * Extrae el centroAtencionId del token (MULTI-TENENCIA)
     * @param token JWT token
     * @return Integer centroAtencionId o null si el usuario no está asociado a un centro
     */
    public Integer extractCentroAtencionId(String token) {
        Claims claims = extractAllClaims(token);
        Object centroIdObj = claims.get("centroAtencionId");
        if (centroIdObj instanceof Number) {
            return ((Number) centroIdObj).intValue();
        }
        return null;
    }

    /**
     * Extrae un claim específico del token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extrae todos los claims del token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Genera un access token para el usuario
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TOKEN_TYPE, TYPE_ACCESS);
        if (userDetails instanceof User) {
            User user = (User) userDetails;
            claims.put("userId", user.getId());
            claims.put("role", user.getRole() != null ? user.getRole().getName() : "USER");

            // MULTI-TENENCIA: Inyectar centroAtencionId en el token JWT
            // Solo para roles que están limitados a un centro (ADMINISTRADOR, MEDICO, OPERADOR)
            if (user.getCentroAtencion() != null) {
                claims.put("centroAtencionId", user.getCentroAtencion().getId());
            }

            // Si el usuario tiene acceso a PACIENTE (cualquier rol en la jerarquía), verificar paciente
            // Usa lógica centralizada de jerarquía de roles: user.getRole().hasAccessTo(Role.PACIENTE)
            if (user.getRole() != null && user.getRole().hasAccessTo(Role.PACIENTE)) {
                try {
                    // Buscar el paciente asociado por email usando el servicio
                    // Suponiendo que el email es único y coincide entre User y Paciente
                    var optionalPacienteDTO = pacienteService.findByEmail(user.getEmail());
                    System.out.println("Optional pacienteDTO: " + optionalPacienteDTO);
                    if (optionalPacienteDTO.isPresent()) {
                        var pacienteDTO = optionalPacienteDTO.get();
                        if (pacienteDTO.getId() != null) {
                            claims.put("pacienteId", pacienteDTO.getId());
                            // Añadir claim de profileCompleted
                            claims.put("profileCompleted", pacienteDTO.isProfileCompleted());
                        }
                    }
                } catch (Exception e) {
                    // Ignorar si no se puede obtener el paciente
                }
            }
        }
        return createToken(claims, userDetails.getUsername(), accessTokenExpiration);
    }

    /**
     * Genera un refresh token para el usuario
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TOKEN_TYPE, TYPE_REFRESH);
        return createToken(claims, userDetails.getUsername(), refreshTokenExpiration);
    }

    /**
     * Crea un token JWT con los claims especificados
     */
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida un access token para el usuario. Un refresh token no pasa: vive 7 días
     * y solo sirve para pedir un access nuevo en /api/auth/refresh.
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        return esAccessToken(claims)
                && userDetails.getUsername().equals(claims.getSubject())
                && !claims.getExpiration().before(new Date());
    }

    /**
     * Los access tokens emitidos antes de agregar el claim no traen tipo. Se aceptan
     * porque el refresh siempre llevó tokenType=refresh: un token sin tipo solo puede
     * ser un access, y vence a los 15 minutos de emitido.
     */
    private boolean esAccessToken(Claims claims) {
        String tipo = claims.get(CLAIM_TOKEN_TYPE, String.class);
        return tipo == null || TYPE_ACCESS.equals(tipo);
    }

    /**
     * Valida si el refresh token es válido
     */
    public Boolean validateRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return TYPE_REFRESH.equals(tokenType) && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtiene la clave de firma para los tokens
     */
    private SecretKey getSignKey() {
        return signKey;
    }

    /**
     * Valida la clave de firma al arrancar. Sin una clave propia y suficientemente larga
     * la aplicación no levanta: es preferible a firmar tokens con una clave conocida.
     */
    @Override
    public void afterPropertiesSet() {
        String configurada = secretKey == null ? "" : secretKey.trim();

        if (configurada.isEmpty()) {
            if (!devMode) {
                throw new IllegalStateException(
                        "Falta JWT_SECRET. Definí una clave propia en Base64 de al menos 32 bytes "
                                + "(por ejemplo con: openssl rand -base64 48).");
            }
            byte[] efimera = new byte[48];
            new SecureRandom().nextBytes(efimera);
            signKey = Keys.hmacShaKeyFor(efimera);
            logger.warn("JWT_SECRET no está definido y security.dev.mode=true: se generó una clave "
                    + "temporal. Los tokens dejan de valer cuando se reinicia el backend. "
                    + "Definí JWT_SECRET para conservarlos.");
            return;
        }

        if (CLAVE_PUBLICADA_SHA256.equals(sha256Hex(configurada))) {
            throw new IllegalStateException(
                    "JWT_SECRET es la clave de ejemplo que estuvo publicada en el repositorio. "
                            + "Generá una propia (por ejemplo con: openssl rand -base64 48).");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(configurada);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "JWT_SECRET no es Base64 válido. Generá una clave con: openssl rand -base64 48.");
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET es demasiado corta: decodificada tiene " + keyBytes.length
                            + " bytes y hacen falta al menos " + MIN_KEY_BYTES + ".");
        }
        signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}
