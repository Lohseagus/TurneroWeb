package unpsjb.labprog.backend.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.model.User;

/**
 * Componente que se ejecuta al inicio de la aplicación para crear 
 * el usuario SUPERADMIN inicial si no existe.
 * 
 * SUPERADMIN tiene acceso global sin restricciones de centro.
 */
@Component
public class AdminInitializer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);

    static final int MIN_PASSWORD_LENGTH = 12;

    // SHA-256 de la contraseña del seed que estuvo publicada en este repositorio.
    private static final String PASSWORD_PUBLICADA_SHA256 =
            "e9aaf686e6b2039b0b777c5c763f0c0b5b7fd37c354296b6e98fd9531dcd9c26";
    
    @Value("${admin.seed.enabled:false}")
    private Boolean adminSeedEnabled;
    
    @Value("${admin.seed.email:admin@turneroweb.com}")
    private String adminEmail;
    
    @Value("${admin.seed.password:}")
    private String adminPassword;
    
    @Value("${admin.seed.nombre:Administrador}")
    private String adminNombre;
    
    @Value("${admin.seed.apellido:Sistema}")
    private String adminApellido;
    
    @Value("${admin.seed.dni:99999999}")
    private Long adminDni;
    
    @Value("${admin.seed.telefono:+54-9-11-0000-0000}")
    private String adminTelefono;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) throws Exception {
        if (!adminSeedEnabled) {
            logger.info("🔧 Admin seed está deshabilitado (admin.seed.enabled=false)");
            return;
        }
        
        String motivo = motivoDeRechazo(adminPassword);
        if (motivo != null) {
            logger.error("❌ No se crea el SUPERADMIN inicial: {}", motivo);
            return;
        }

        try {
            logger.info("🚀 Verificando SUPERADMIN inicial...");
            
            // Verificar si ya existe el usuario SUPERADMIN
            if (userService.existsByEmail(adminEmail)) {
                logger.info("✅ El SUPERADMIN inicial ya existe: {}", adminEmail);
                return;
            }
            
            logger.info("👤 Creando usuario SUPERADMIN inicial...");
            
            // Crear el usuario SUPERADMIN usando UserService
            String hashedPassword = passwordEncoder.encode(adminPassword);
            
            // Crear User con rol SUPERADMIN (sin centro asignado)
            User superAdmin = new User();
            superAdmin.setNombre(adminNombre);
            superAdmin.setApellido(adminApellido);
            superAdmin.setDni(adminDni);
            superAdmin.setEmail(adminEmail);
            superAdmin.setHashedPassword(hashedPassword);
            superAdmin.setTelefono(adminTelefono);
            superAdmin.setRole(unpsjb.labprog.backend.model.Role.SUPERADMIN);
            superAdmin.setCentroAtencion(null); // SUPERADMIN no tiene centro asignado
            superAdmin.setEmailVerified(true);
            superAdmin.setEmailVerifiedAt(java.time.LocalDateTime.now());
            
            userService.save(superAdmin);
            
            logger.info("✅ SUPERADMIN inicial creado exitosamente:");
            logger.info("   📧 Email: {}", adminEmail);
            logger.info("   🆔 DNI: {}", adminDni);
            logger.info("   🌐 Acceso: GLOBAL (sin restricciones de centro)");
            logger.warn("⚠️  IMPORTANTE: El SUPERADMIN debe cambiar su contraseña en el primer login");
            logger.info("🔐 Credenciales temporales configuradas desde variables de entorno");
            
        } catch (Exception e) {
            logger.error("❌ Error al crear el SUPERADMIN inicial: {}", e.getMessage());
            logger.error("   Verifique las variables de entorno y la configuración de la base de datos");
            // No lanzar excepción para no impedir el startup de la aplicación
        }
    }
    
    /**
     * Devuelve por qué la contraseña configurada no sirve para el seed, o null si sirve.
     * Nunca incluye la contraseña en el mensaje.
     */
    static String motivoDeRechazo(String password) {
        if (password == null || password.isBlank()) {
            return "admin.seed.enabled=true pero falta ADMIN_SEED_PASSWORD.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "ADMIN_SEED_PASSWORD tiene que tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.";
        }
        if (PASSWORD_PUBLICADA_SHA256.equals(sha256Hex(password))) {
            return "ADMIN_SEED_PASSWORD es la contraseña que estuvo publicada en el repositorio. Elegí otra.";
        }
        return null;
    }

    private static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }

    /**
     * Información sobre la configuración del administrador inicial
     */
    public void logConfiguration() {
        if (adminSeedEnabled) {
            logger.info("🔧 Configuración del administrador inicial:");
            logger.info("   📧 Email: {}", adminEmail);
            logger.info("   🆔 DNI: {}", adminDni);
            logger.info("   📱 Teléfono: {}", adminTelefono);
        }
    }
}