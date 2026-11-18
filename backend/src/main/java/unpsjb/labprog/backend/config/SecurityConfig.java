package unpsjb.labprog.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import unpsjb.labprog.backend.business.service.UserService;
import unpsjb.labprog.backend.saas.CentroObjetivoResolver;
import unpsjb.labprog.backend.saas.SaasEnforcement;
import unpsjb.labprog.backend.saas.SuscripcionAccessService;
import unpsjb.labprog.backend.saas.SuscripcionSoloLecturaFilter;

import jakarta.servlet.DispatcherType;

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
 * Por defecto se aplica el control de roles. El modo desarrollo se pide de forma
 * explícita, y solo en una máquina local o en la suite e2e:
 * 
 * 1. Variable de entorno:
 * export SECURITY_DEV_MODE=true
 * 
 * 2. Parámetro JVM al ejecutar:
 * java -Dsecurity.dev.mode=true -jar app.jar
 * 
 * - devMode=false (por defecto): PRODUCCIÓN - Control de roles aplicado
 * - devMode=true: DESARROLLO - Todos los endpoints públicos
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Habilita @PreAuthorize en controladores
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

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
     * El filtro es un {@code @Component} para poder inyectarlo en la cadena.
     * Boot también lo publicaría como filtro del servlet, delante o después de
     * la cadena. OncePerRequestFilter corre una sola vez: si gana esa copia,
     * la de la cadena se saltea y el usuario queda anónimo (403).
     */
    /**
     * El filtro no es un {@code @Component}: si lo fuera, Boot lo publicaría
     * también como filtro del servlet, fuera de la cadena, y correría sin el usuario.
     * En los slices de seguridad que no cargan el servicio SaaS queda en no-op.
     */
    @Bean
    public SuscripcionSoloLecturaFilter suscripcionSoloLecturaFilter(
            ObjectProvider<SuscripcionAccessService> access,
            ObjectProvider<CentroObjetivoResolver> resolver,
            ObjectProvider<SaasEnforcement> enforcement) {
        return new SuscripcionSoloLecturaFilter(access, resolver, enforcement);
    }

    @Bean
    public FilterRegistrationBean<SuscripcionSoloLecturaFilter> suscripcionSoloLecturaFilterRegistration(
            SuscripcionSoloLecturaFilter filter) {
        FilterRegistrationBean<SuscripcionSoloLecturaFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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
     * - false (por defecto): Modo producción (control de roles aplicado)
     * - true: Modo desarrollo (todos los endpoints públicos)
     * 
     * Si la variable falta, el backend arranca cerrado. Para abrirlo en desarrollo:
     * 1. Variable de entorno: SECURITY_DEV_MODE=true
     * 2. Parámetro JVM: -Dsecurity.dev.mode=true
     */
    @Value("${security.dev.mode:false}")
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
    public SecurityFilterChain filterChain(HttpSecurity http, SuscripcionSoloLecturaFilter suscripcionSoloLecturaFilter)
            throws Exception {
        if (devMode) {
            logger.warn("security.dev.mode=true: TODOS los endpoints quedan públicos, sin control de roles. "
                    + "Usalo solo en desarrollo local o en la suite e2e, nunca con datos reales.");
        }
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
                                // Un error del controller no debe volver a autorizarse como anónimo:
                                // si no, el cliente recibe 403 y el frontend dice "no tenés permiso".
                                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                                .requestMatchers("/", "/health", "/actuator/**").permitAll()
                                // Operan sobre la cuenta de quien llama: necesitan un usuario autenticado.
                                .requestMatchers("/api/auth/change-password", "/api/auth/update-profile").authenticated()
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/agenda/publica", "/api/agenda/publica/**").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/deep-links/validate").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/saas/aviso").authenticated()
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
                                // Consultan o actúan sobre toda la plataforma, no sobre un centro.
                                .requestMatchers(HttpMethod.GET, "/api/admins/check-email").hasRole("SUPERADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/admins/test-encuesta-invitaciones").hasRole("SUPERADMIN")
                                .requestMatchers("/api/admins/**").hasRole("ADMINISTRADOR")

                                // Autoservicio del paciente (y de quien hereda PACIENTE):
                                // la ficha propia y las preferencias. Va antes del catálogo,
                                // que sigue siendo solo de administrador y operador.
                                .requestMatchers("/api/pacientes/me", "/api/pacientes/me/**").hasRole("PACIENTE")
                                .requestMatchers("/api/pacientes/sync-current-user").authenticated()
                                .requestMatchers("/api/notificaciones/**").hasRole("PACIENTE")
                                .requestMatchers("/api/encuestas/**").hasRole("PACIENTE")
                                .requestMatchers("/api/turno/historial/**").hasRole("PACIENTE")
                                // El paciente lee sus turnos; PacienteAccessGuard exige que el id sea el suyo.
                                .requestMatchers(HttpMethod.GET, "/api/turno/paciente/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.POST, "/api/turno/asignar").hasRole("PACIENTE")
                                // El paciente ve, confirma, cancela y reagenda sus turnos. CentroAccessGuard
                                // exige que sea el titular; al personal, que el turno sea de su centro.
                                // El id es numérico para no abrir /api/turno/page, /search y demás.
                                .requestMatchers(HttpMethod.GET, "/api/turno/{id:\\d+}").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.PUT,
                                        "/api/turno/{id:\\d+}/confirmar",
                                        "/api/turno/{id:\\d+}/cancelar",
                                        "/api/turno/{id:\\d+}/reagendar").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.POST, "/api/lista-espera").hasRole("PACIENTE")
                                // Ausencias: el médico solo las suyas, admin y operador las de su centro.
                                // Va antes del GET general de agenda, que no incluye al operador.
                                .requestMatchers("/api/agenda/ausencias", "/api/agenda/ausencias/**")
                                        .hasAnyRole("MEDICO", "OPERADOR", "ADMINISTRADOR")
                                .requestMatchers(HttpMethod.GET, "/api/agenda/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/especialidades", "/api/especialidades/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/obra-social", "/api/obra-social/**").hasRole("PACIENTE")
                                .requestMatchers(HttpMethod.GET, "/api/centrosAtencion", "/api/centrosAtencion/**").hasRole("PACIENTE")
                                // La ficha del profesional se resuelve con el principal del JWT.
                                // Va antes que el catálogo: un MEDICO autenticado tiene que poder
                                // pedir /api/medicos/me aunque no haya guardado un id en el cliente.
                                .requestMatchers(HttpMethod.GET, "/api/medicos/me").authenticated()
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
                                // Verificación de farmacia o laboratorio, sin sesión.
                                // El código es un token largo; el servicio no devuelve la historia.
                                .requestMatchers(HttpMethod.GET, "/api/recetas/verificar/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/ordenes-estudio/verificar/**").permitAll()
                                .requestMatchers("/api/recetas/**", "/api/ordenes-estudio/**").authenticated()

                                .anyRequest().authenticated();
                    }
                })
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(suscripcionSoloLecturaFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

}
