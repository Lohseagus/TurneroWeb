package unpsjb.labprog.backend.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Completa {@code audit_log} después de {@code ddl-auto=update}.
 *
 * <p>La columna {@code source} nace nullable: un NOT NULL en el ALTER rompería
 * las filas que ya están. Este paso rellena las que siguen en null
 * ({@code performed_by} SISTEMA, SISTEMA_AUTOMATICO o SYSTEM_SEED → SISTEMA;
 * el resto → USUARIO), copia el rol desde {@code users} cuando el actor es un
 * email, y copia el centro desde el turno, el consultorio, el staff, la lista
 * de espera o el usuario auditado. Después deja el default USUARIO y un
 * disparador que rechaza UPDATE y DELETE.
 *
 * <p>Es idempotente. Si el disparador ya existe y todavía hay nulls, se baja
 * un momento, se rellena y se vuelve a crear. Un fallo de SQL no frena el
 * arranque: la app sigue y el log queda para verlo.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogSchemaEnsurer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogSchemaEnsurer.class);

    static final String TRIGGER_UPDATE = "audit_log_no_update";
    static final String TRIGGER_DELETE = "audit_log_no_delete";

    private final DataSource dataSource;

    public AuditLogSchemaEnsurer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            asegurar(connection);
        } catch (RuntimeException | SQLException ex) {
            logger.error("No se pudo preparar audit_log. El arranque sigue.", ex);
        }
    }

    /**
     * @return 1 si dejó los disparadores activos, 0 si esta base no aplica
     */
    public int asegurar(Connection connection) {
        try {
            String producto = connection.getMetaData().getDatabaseProductName();
            if (producto == null) {
                return 0;
            }
            String normalizado = producto.toLowerCase(Locale.ROOT);
            boolean postgres = normalizado.contains("postgresql");
            boolean h2 = normalizado.contains("h2");
            if (!postgres && !h2) {
                logger.warn("No se prepara audit_log en {}.", producto);
                return 0;
            }
            if (!existeTabla(connection, "audit_log")) {
                logger.info("Todavía no está audit_log. No se rellena ni se protege.");
                return 0;
            }
            agregarColumnas(connection);
            // El disparador rechaza cualquier UPDATE, incluido el relleno.
            // Se baja solo si hace falta escribir, y se vuelve a crear al final.
            if (hayPendientes(connection)) {
                bajarDisparadores(connection, postgres);
                rellenar(connection);
            }
            definirDefault(connection);
            crearDisparadores(connection, postgres);
            return 1;
        } catch (SQLException ex) {
            logger.error("No se pudo preparar audit_log. El arranque sigue.", ex);
            return 0;
        }
    }

    private static void agregarColumnas(Connection connection) throws SQLException {
        ejecutar(connection, "ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS source VARCHAR(20)");
        ejecutar(connection, "ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS actor_role VARCHAR(30)");
        ejecutar(connection, "ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS centro_atencion_id INTEGER");
    }

    private static boolean hayPendientes(Connection connection) throws SQLException {
        if (contar(connection, "SELECT COUNT(*) FROM audit_log WHERE source IS NULL") > 0) {
            return true;
        }
        if (existeTabla(connection, "users")
                && contar(connection, """
                        SELECT COUNT(*) FROM audit_log a
                        WHERE a.actor_role IS NULL
                          AND EXISTS (
                            SELECT 1 FROM users u
                            WHERE LOWER(u.email) = LOWER(a.performed_by)
                          )
                        """) > 0) {
            return true;
        }
        return centroPendiente(connection);
    }

    private static boolean centroPendiente(Connection connection) throws SQLException {
        if (existeTabla(connection, "turno")
                && contar(connection, """
                        SELECT COUNT(*) FROM audit_log a
                        WHERE a.centro_atencion_id IS NULL
                          AND (
                            (a.turno_id IS NOT NULL AND EXISTS (
                                SELECT 1 FROM turno t WHERE t.id = a.turno_id AND t.centro_atencion_id IS NOT NULL))
                            OR (a.entity_type = 'TURNO' AND a.entity_id IS NOT NULL AND EXISTS (
                                SELECT 1 FROM turno t WHERE t.id = a.entity_id AND t.centro_atencion_id IS NOT NULL))
                          )
                        """) > 0) {
            return true;
        }
        if (existeTabla(connection, "consultorio")
                && contar(connection, """
                        SELECT COUNT(*) FROM audit_log a
                        WHERE a.centro_atencion_id IS NULL AND a.entity_type = 'CONSULTORIO'
                          AND EXISTS (SELECT 1 FROM consultorio c WHERE c.id = a.entity_id)
                        """) > 0) {
            return true;
        }
        if (existeTabla(connection, "staff_medico")
                && contar(connection, """
                        SELECT COUNT(*) FROM audit_log a
                        WHERE a.centro_atencion_id IS NULL AND a.entity_type = 'STAFF_MEDICO'
                          AND EXISTS (SELECT 1 FROM staff_medico s WHERE s.id = a.entity_id)
                        """) > 0) {
            return true;
        }
        if (contar(connection, """
                SELECT COUNT(*) FROM audit_log
                WHERE centro_atencion_id IS NULL AND entity_type = 'CENTRO_ATENCION' AND entity_id IS NOT NULL
                """) > 0) {
            return true;
        }
        if (existeTabla(connection, "lista_espera")
                && contar(connection, """
                        SELECT COUNT(*) FROM audit_log a
                        WHERE a.centro_atencion_id IS NULL AND a.entity_type = 'LISTA_ESPERA'
                          AND EXISTS (SELECT 1 FROM lista_espera l WHERE l.id = a.entity_id AND l.centro_atencion_id IS NOT NULL)
                        """) > 0) {
            return true;
        }
        return existeTabla(connection, "users")
                && contar(connection, """
                        SELECT COUNT(*) FROM audit_log a
                        WHERE a.centro_atencion_id IS NULL AND a.entity_type = 'USUARIO'
                          AND EXISTS (
                            SELECT 1 FROM users u WHERE u.id = a.entity_id AND u.centro_atencion_id IS NOT NULL
                          )
                        """) > 0;
    }

    private static void rellenar(Connection connection) throws SQLException {
        ejecutar(connection, """
                UPDATE audit_log
                SET source = CASE
                    WHEN UPPER(TRIM(performed_by)) IN ('SISTEMA', 'SISTEMA_AUTOMATICO', 'SYSTEM_SEED') THEN 'SISTEMA'
                    ELSE 'USUARIO'
                END
                WHERE source IS NULL
                """);
        if (existeTabla(connection, "users")) {
            ejecutar(connection, """
                    UPDATE audit_log
                    SET actor_role = (
                        SELECT MIN(u.role) FROM users u
                        WHERE LOWER(u.email) = LOWER(audit_log.performed_by)
                    )
                    WHERE actor_role IS NULL
                      AND EXISTS (
                        SELECT 1 FROM users u WHERE LOWER(u.email) = LOWER(audit_log.performed_by)
                      )
                    """);
        }
        if (existeTabla(connection, "turno")) {
            ejecutar(connection, """
                    UPDATE audit_log
                    SET centro_atencion_id = (
                        SELECT t.centro_atencion_id FROM turno t WHERE t.id = audit_log.turno_id
                    )
                    WHERE centro_atencion_id IS NULL
                      AND turno_id IS NOT NULL
                      AND EXISTS (
                        SELECT 1 FROM turno t WHERE t.id = audit_log.turno_id AND t.centro_atencion_id IS NOT NULL
                      )
                    """);
            ejecutar(connection, """
                    UPDATE audit_log
                    SET centro_atencion_id = (
                        SELECT t.centro_atencion_id FROM turno t WHERE t.id = audit_log.entity_id
                    )
                    WHERE centro_atencion_id IS NULL
                      AND entity_type = 'TURNO'
                      AND entity_id IS NOT NULL
                      AND EXISTS (
                        SELECT 1 FROM turno t WHERE t.id = audit_log.entity_id AND t.centro_atencion_id IS NOT NULL
                      )
                    """);
        }
        if (existeTabla(connection, "consultorio")) {
            ejecutar(connection, """
                    UPDATE audit_log
                    SET centro_atencion_id = (
                        SELECT c.centro_atencion_id FROM consultorio c WHERE c.id = audit_log.entity_id
                    )
                    WHERE centro_atencion_id IS NULL
                      AND entity_type = 'CONSULTORIO'
                      AND EXISTS (SELECT 1 FROM consultorio c WHERE c.id = audit_log.entity_id)
                    """);
        }
        if (existeTabla(connection, "staff_medico")) {
            ejecutar(connection, """
                    UPDATE audit_log
                    SET centro_atencion_id = (
                        SELECT s.centro_atencion_id FROM staff_medico s WHERE s.id = audit_log.entity_id
                    )
                    WHERE centro_atencion_id IS NULL
                      AND entity_type = 'STAFF_MEDICO'
                      AND EXISTS (SELECT 1 FROM staff_medico s WHERE s.id = audit_log.entity_id)
                    """);
        }
        ejecutar(connection, """
                UPDATE audit_log
                SET centro_atencion_id = CAST(entity_id AS INTEGER)
                WHERE centro_atencion_id IS NULL
                  AND entity_type = 'CENTRO_ATENCION'
                  AND entity_id IS NOT NULL
                """);
        if (existeTabla(connection, "lista_espera")) {
            ejecutar(connection, """
                    UPDATE audit_log
                    SET centro_atencion_id = (
                        SELECT l.centro_atencion_id FROM lista_espera l WHERE l.id = audit_log.entity_id
                    )
                    WHERE centro_atencion_id IS NULL
                      AND entity_type = 'LISTA_ESPERA'
                      AND EXISTS (
                        SELECT 1 FROM lista_espera l
                        WHERE l.id = audit_log.entity_id AND l.centro_atencion_id IS NOT NULL
                      )
                    """);
        }
        if (existeTabla(connection, "users")) {
            ejecutar(connection, """
                    UPDATE audit_log
                    SET centro_atencion_id = (
                        SELECT u.centro_atencion_id FROM users u WHERE u.id = audit_log.entity_id
                    )
                    WHERE centro_atencion_id IS NULL
                      AND entity_type = 'USUARIO'
                      AND EXISTS (
                        SELECT 1 FROM users u
                        WHERE u.id = audit_log.entity_id AND u.centro_atencion_id IS NOT NULL
                      )
                    """);
        }
    }

    private static void definirDefault(Connection connection) throws SQLException {
        ejecutar(connection, "ALTER TABLE audit_log ALTER COLUMN source SET DEFAULT 'USUARIO'");
    }

    private static void bajarDisparadores(Connection connection, boolean postgres) throws SQLException {
        if (postgres) {
            ejecutar(connection, "DROP TRIGGER IF EXISTS " + TRIGGER_UPDATE + " ON audit_log");
            ejecutar(connection, "DROP TRIGGER IF EXISTS " + TRIGGER_DELETE + " ON audit_log");
            return;
        }
        ejecutar(connection, "DROP TRIGGER IF EXISTS " + TRIGGER_UPDATE);
        ejecutar(connection, "DROP TRIGGER IF EXISTS " + TRIGGER_DELETE);
    }

    private static void crearDisparadores(Connection connection, boolean postgres) throws SQLException {
        bajarDisparadores(connection, postgres);
        if (postgres) {
            ejecutar(connection, """
                    CREATE OR REPLACE FUNCTION audit_log_rechazar_mutacion()
                    RETURNS trigger
                    LANGUAGE plpgsql
                    AS $fn$
                    BEGIN
                      RAISE EXCEPTION 'audit_log es inmutable';
                    END;
                    $fn$
                    """);
            ejecutar(connection, """
                    CREATE TRIGGER audit_log_no_update
                    BEFORE UPDATE ON audit_log
                    FOR EACH ROW EXECUTE FUNCTION audit_log_rechazar_mutacion()
                    """);
            ejecutar(connection, """
                    CREATE TRIGGER audit_log_no_delete
                    BEFORE DELETE ON audit_log
                    FOR EACH ROW EXECUTE FUNCTION audit_log_rechazar_mutacion()
                    """);
            return;
        }
        // La clase vive en el classpath de test (H2 es scope test). En un H2 de
        // desarrollo hay que tenerla; si no está, el arranque sigue y queda el log.
        String clase = "unpsjb.labprog.backend.config.AuditLogH2Guard";
        ejecutar(connection, """
                CREATE TRIGGER audit_log_no_update
                BEFORE UPDATE ON audit_log
                FOR EACH ROW CALL "%s"
                """.formatted(clase));
        ejecutar(connection, """
                CREATE TRIGGER audit_log_no_delete
                BEFORE DELETE ON audit_log
                FOR EACH ROW CALL "%s"
                """.formatted(clase));
    }

    private static void ejecutar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int contar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static boolean existeTabla(Connection connection, String tabla) throws SQLException {
        String sql = """
                SELECT 1 FROM information_schema.tables
                WHERE LOWER(table_name) = ? AND table_schema IN (current_schema(), 'PUBLIC', 'public')
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, tabla.toLowerCase(Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
        } catch (SQLException ex) {
            // H2 a veces no acepta current_schema() en el IN. Se reintenta abajo.
        }
        String fallback = "SELECT 1 FROM information_schema.tables WHERE LOWER(table_name) = ?";
        try (var statement = connection.prepareStatement(fallback)) {
            statement.setString(1, tabla.toLowerCase(Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }
}
