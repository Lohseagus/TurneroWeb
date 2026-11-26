package unpsjb.labprog.backend.config;

import java.sql.Connection;
import java.sql.SQLException;

import org.h2.api.Trigger;

/**
 * Disparador de H2 para los tests. En PostgreSQL el rechazo lo hace la función
 * {@code audit_log_rechazar_mutacion}.
 */
public class AuditLogH2Guard implements Trigger {

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("audit_log es inmutable");
    }
}
