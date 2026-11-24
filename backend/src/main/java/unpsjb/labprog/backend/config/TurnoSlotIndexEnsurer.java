package unpsjb.labprog.backend.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

import unpsjb.labprog.backend.business.service.TurnoSlotGuard;

/**
 * Crea el índice único de turnos activos después del {@code ddl-auto=update}.
 *
 * <p>Hibernate no arma un índice parcial. Este paso sí: un solo turno no
 * cancelado y que no sea sobreturno por (staff_medico, fecha, hora_inicio).
 * Si ya hay duplicados, se registra y no se crea el índice: el arranque no
 * tiene que caerse por datos viejos. Cualquier otro error de SQL también se
 * traga y queda en el log.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TurnoSlotIndexEnsurer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TurnoSlotIndexEnsurer.class);

    static final String DUPLICADOS = """
            SELECT staff_medico_id, fecha, hora_inicio
            FROM turno
            WHERE estado <> 'CANCELADO'
              AND COALESCE(sobreturno, FALSE) = FALSE
              AND staff_medico_id IS NOT NULL
            GROUP BY staff_medico_id, fecha, hora_inicio
            HAVING COUNT(*) > 1
            """;

    static final String CREAR_POSTGRES = """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_turno_slot_activo
            ON turno (staff_medico_id, fecha, hora_inicio)
            WHERE estado <> 'CANCELADO' AND COALESCE(sobreturno, false) = false
            """;

    private final DataSource dataSource;

    public TurnoSlotIndexEnsurer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            int creado = asegurar(connection);
            if (creado == 1) {
                logger.info("Índice {} creado.", TurnoSlotGuard.INDICE);
            }
        } catch (RuntimeException | SQLException ex) {
            logger.error("No se pudo revisar el índice de turnos activos. El arranque sigue.", ex);
        }
    }

    /**
     * @return 1 si creó el índice, 0 si no hacía falta o no se pudo
     */
    public int asegurar(Connection connection) {
        try {
            String producto = connection.getMetaData().getDatabaseProductName();
            if (producto == null) {
                return 0;
            }
            String normalizado = producto.toLowerCase(Locale.ROOT);
            if (!normalizado.contains("postgresql") && !normalizado.contains("h2")) {
                logger.warn("No se crea el índice de turnos en {}.", producto);
                return 0;
            }
            if (!existeTabla(connection, normalizado)) {
                logger.info("Todavía no está la tabla turno. No se crea el índice.");
                return 0;
            }
            if (existeIndice(connection, normalizado)) {
                return 0;
            }
            int duplicados = contarDuplicados(connection);
            if (duplicados > 0) {
                logger.warn(
                        "Hay {} horario(s) con más de un turno activo. No se crea {} para no frenar el arranque.",
                        duplicados, TurnoSlotGuard.INDICE);
                return 0;
            }
            try (Statement statement = connection.createStatement()) {
                // El índice es parcial: los cancelados y los sobreturnos no ocupan el horario.
                // H2 no siempre acepta el WHERE; si falla, se loguea y el arranque sigue.
                statement.execute(CREAR_POSTGRES);
            }
            return 1;
        } catch (SQLException ex) {
            logger.error("No se pudo crear {}. El arranque sigue sin ese índice.", TurnoSlotGuard.INDICE, ex);
            return 0;
        }
    }

    private static int contarDuplicados(Connection connection) throws SQLException {
        int grupos = 0;
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(DUPLICADOS)) {
            while (rs.next()) {
                grupos++;
            }
        }
        return grupos;
    }

    private static boolean existeTabla(Connection connection, String producto) throws SQLException {
        String sql = producto.contains("postgresql")
                ? "SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'turno'"
                : "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'TURNO'";
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static boolean existeIndice(Connection connection, String producto) throws SQLException {
        if (producto.contains("postgresql")) {
            String sql = "SELECT 1 FROM pg_indexes WHERE indexname = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, TurnoSlotGuard.INDICE);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next();
                }
            }
        }
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.INDEXES WHERE UPPER(INDEX_NAME) = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TurnoSlotGuard.INDICE.toUpperCase(Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }
}
