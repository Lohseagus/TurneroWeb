package unpsjb.labprog.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import unpsjb.labprog.backend.business.service.UserService;

import java.util.Arrays;

/**
 * Configuración de seguridad con JWT y autenticación stateless
 * 
 * CONTROL DE ACCESO BASADO EN ROLES:
 * =================================
 * - ADMINISTRADOR: Acceso completo al sistema, gestión de usuarios, auditoría
 * - OPERADOR: Gestión de turnos, pacientes, médicos, obras sociales
 * - MEDICO: Dashboard médico, horarios, estadísticas, perfil
 * - PACIENTE: Dashboard paciente, agenda, notificaciones, perfil
 * 
 * MODO DE DESARROLLO vs PRODUCCIÓN:
 * ================================
 * Para alternar entre modos usar cualquiera de estas opciones:
 * 
 * 1. Variable de entorno:
 * export SECURITY_DEV_MODE=false
 * 
 * 2. Parámetro JVM al ejecutar:
 * java -Dsecurity.dev.mode=false -jar app.jar
 * 
 * 3. En application.properties:
 * security.dev.mode=false
 * 
 * 4. Modificar directamente la anotación @Value en esta clase
 * 
 * - devMode=true: DESARROLLO - Todos los endpoints públicos
 * - devMode=false: PRODUCCIÓN - Control de roles aplicado
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Habilita @PreAuthorize en controladores
public class SecurityConfig {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configuración del encoder de contraseñas
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configuración del proveedor de autenticación
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Configuración del manager de autenticación
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configuración de la jerarquía de roles para Spring Security
     * Permite que roles superiores hereden permisos de roles inferiores
     * 
     * Jerarquía:
     * - ADMINISTRADOR: incluye todos los permisos (MEDICO, OPERADOR, PACIENTE)
     * - MEDICO: incluye permisos de PACIENTE
     * - OPERADOR: incluye permisos de PACIENTE  
     * - PACIENTE: permisos básicos
     * 
     * Nota: MEDICO y OPERADOR son roles independientes, no hay herencia entre ellos.
     * 
     * Esto permite que un ADMINISTRADOR acceda a endpoints marcados con hasRole("PACIENTE")
     * sin necesidad de validaciones manuales adicionales.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        // Misma jerarquía de antes. fromHierarchy reemplaza al constructor y a setHierarchy, deprecados.
        return RoleHierarchyImpl.fromHierarchy(
            "ROLE_SUPERADMIN > ROLE_ADMINISTRADOR\n" +
            "ROLE_ADMINISTRADOR > ROLE_MEDICO\n" +
            "ROLE_ADMINISTRADOR > ROLE_OPERADOR\n" +
            "ROLE_ADMINISTRADOR > ROLE_PACIENTE\n" +
            "ROLE_MEDICO > ROLE_PACIENTE\n" +
            "ROLE_OPERADOR > ROLE_PACIENTE"
        );
    }

    /**
     * Variable para controlar el modo de seguridad
     * - true: Modo desarrollo (todos los endpoints públicos)
     * - false: Modo producción (control de roles aplicado)
     * 
     * Para cambiar el modo:
     * 1. Variable de entorno: SECURITY_DEV_MODE=false
     * 2. Parámetro JVM: -Dsecurity.dev.mode=false
     * 3. Modificar application.properties: security.dev.mode=false
     */
    @Value("${security.dev.mode:true}")
    private boolean devMode;

    /**
     * Orígenes permitidos para CORS (separados por comas)
     * Por defecto: http://localhost:4200 para desarrollo
     * En producción: agregar URL de Netlify/frontend desplegado
     * 
     * Ejemplo en Render:
     * APP_ALLOWED_ORIGINS=http://localhost:4200,https://tu-app.netlify.app
     */
    @Value("${app.allowed.origins:http://localhost:4200}")
    private String allowedOrigins;

    /**
     * Configuración de CORS (Cross-Origin Resource Sharing)
     * Permite que el frontend (Angular) se comunique con el backend desde diferentes dominios
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Convertir string separado por comas en lista de orígenes permitidos
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        
        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Headers permitidos (Authorization para JWT, Content-Type, etc.)
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Permitir envío de credenciales (cookies, headers de autorización)
        configuration.setAllowCredentials(true);
        
        // Tiempo de cache de la respuesta preflight (OPTIONS)
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configuración de la cadena de filtros de seguridad
     * 
     * MODO DESARROLLO (devMode=true):
     * - Todos los endpoints son públicos para facilitar desarrollo
     * 
     * MODO PRODUCCIÓN (devMode=false):
     * - Control de acceso basado en roles JWT
     * - Seguridad completa aplicada según roles del sistema
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Habilitar CORS con configuración
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (devMode) {
                        // =================================
                        // MODO DESARROLLO - ACCESO LIBRE
                        // =================================
                        auth.anyRequest().permitAll();
                    } else {
                        // =================================
                        // MODO PRODUCCIÓN - CONTROL DE ROLES
                        // =================================

                        // Las reglas van de la más específica a la más amplia: gana la primera.
                        // Los controllers viven bajo /api. Las rutas viejas sin ese prefijo
                        // ( /turnos, /pacientes, /agenda ) no matcheaban nada.
                        auth
                                .requestMatchers("/", "/health", "/actuator/**").permitAll()
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/agenda/publica", "/api/agenda/publica/**").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/deep-links/validate").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/saas/planes").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/saas/cotizar").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/saas/registro").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/saas/webhooks/mercadopago").permitAll()
                                .requestMatchers("/api/saas/admin/**").hasRole("SUPERADMIN")
                                .requestMatchers("/api/saas/mi-suscripcion/**").hasRole("ADMINISTRADOR")

                                // SUPERADMIN. El alta de operadores del centro sigue siendo de ADMINISTRADOR.
                                .requestMatchers("/api/email/**").hasRole("SUPERADMIN")
                                .requestMatchers("/api/role-management/**").hasRole("SUPERADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/admins/operadores").hasRole("ADMINISTRADOR")
                                .requestMatchers(HttpMethod.GET, "/api/admins").hasRole("SUPERADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/admins").hasRole("SUPERADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/admins/**").hasRole("SUPERADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/admins/**").hasRole("SUPERADMIN")
                                .requestMatchers("/api/admins/**").hasRole("ADMINISTRADOR")

                                // Autoservicio del paciente (y de quien hereda PACIENTE).
                                .requestMatchers("/api/pacientes/me/**").hasRole("PACIENTE")
                                .requestMatchers("/api/pacientes/sync-current-user").authenticated()
                                .requestMatchers("/api/notificaciones/**").hasRole("PACIENTE")
                                .requestMatchers("/api/encuestas/**").hasRole("PACIENTE")
                                .requestMatchers("/api/turno/historial/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.POST, "/api/turno/asignar").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.POST, "/api/lista-espera").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/agenda/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/especialidades", "/api/especialidades/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/obra-social", "/api/obra-social/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/centrosAtencion", "/api/centrosAtencion/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/medicos", "/api/medicos/**").hasRole("PACIENTE")

                                // Administración
                                .requestMatchers("/api/admin-dashboard/**", "/api/admin/encuestas/**", "/api/audit/**").hasRole("ADMINISTRADOR")
                                .requestMatchers(HttpMethod.POST, "/api/especialidades/**").hasRole("ADMINISTRADOR")
                                .requestMatchers(HttpMethod.PUT, "/api/especialidades/**").hasRole("ADMINISTRADOR")
                                .requestMatchers(HttpMethod.DELETE, "/api/especialidades/**").hasRole("ADMINISTRADOR")
                                .requestMatchers("/api/deep-links/cleanup").hasRole("ADMINISTRADOR")

                                // Médico, operador y administrador operan turnos y horarios.
                                .requestMatchers("/api/disponibilidades-medico/**").hasAnyRole("MEDICO", "OPERADOR", "ADMINISTRADOR")
                                .requestMatchers("/api/turno/**").hasAnyRole("MEDICO", "OPERADOR", "ADMINISTRADOR")

                                // Gestión de catálogo: administrador y operador.
                                .requestMatchers(
                                        "/api/agenda/**",
                                        "/api/pacientes/**",
                                        "/api/obra-social/**",
                                        "/api/centrosAtencion/**",
                                        "/api/consultorios/**",
                                        "/api/medicos/**",
                                        "/api/staff-medico/**",
                                        "/api/esquema-turno/**",
                                        "/api/operadores/**",
                                        "/api/export/**",
                                        "/api/lista-espera/**",
                                        "/api/config/**")
                                .hasAnyRole("ADMINISTRADOR", "OPERADOR")

                                // Historia clínica. El servicio autoriza por rol primario,
                                // centro, consentimiento y titularidad: la jerarquía hace que
                                // ADMINISTRADOR y SUPERADMIN hereden ROLE_MEDICO.
                                .requestMatchers("/api/hce/**").authenticated()

                                .anyRequest().authenticated();
                    }
                })
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
