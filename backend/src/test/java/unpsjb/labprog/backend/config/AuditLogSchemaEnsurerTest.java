package unpsjb.labprog.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class AuditLogSchemaEnsurerTest {

    @Test
    void rellenaOrigenRolYCentroYRechazaUpdateYDelete() throws Exception {
        try (Connection connection = memoria("audit")) {
            crear(connection);
            AuditLogSchemaEnsurer ensurer = new AuditLogSchemaEnsurer(null);

            assertEquals(1, ensurer.asegurar(connection));
            assertEquals("SISTEMA", origen(connection, 1));
            assertEquals("SISTEMA", origen(connection, 2));
            assertEquals("USUARIO", origen(connection, 3));
            assertEquals("OPERADOR", rol(connection, 3));
            assertEquals(7, centro(connection, 4));
            assertEquals(9, centro(connection, 5));

            assertThrows(SQLException.class, () -> ejecutar(connection,
                    "UPDATE audit_log SET reason = 'tocado' WHERE id = 3"));
            assertThrows(SQLException.class, () -> ejecutar(connection, "DELETE FROM audit_log WHERE id = 3"));
            assertEquals("USUARIO", origen(connection, 3));

            assertEquals(1, ensurer.asegurar(connection));
            assertEquals("SISTEMA", origen(connection, 1));
            assertThrows(SQLException.class, () -> ejecutar(connection, "DELETE FROM audit_log WHERE id = 1"));
        }
    }

    @Test
    void elMensajeDelDisparadorDiceQueEsInmutable() throws Exception {
        try (Connection connection = memoria("msg")) {
            crear(connection);
            new AuditLogSchemaEnsurer(null).asegurar(connection);
            SQLException error = assertThrows(SQLException.class,
                    () -> ejecutar(connection, "UPDATE audit_log SET action = 'UPDATE' WHERE id = 1"));
            assertTrue(error.getMessage().toLowerCase().contains("inmutable"), error.getMessage());
        }
    }

    private static Connection memoria(String nombre) throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:" + nombre + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static void crear(Connection connection) throws SQLException {
        ejecutar(connection, """
                CREATE TABLE audit_log (
                  id INT PRIMARY KEY,
                  turno_id INT,
                  entity_type VARCHAR(50) NOT NULL,
                  entity_id BIGINT,
                  action VARCHAR(50) NOT NULL,
                  performed_at TIMESTAMP NOT NULL,
                  performed_by VARCHAR(100) NOT NULL,
                  reason VARCHAR(200)
                )
                """);
        ejecutar(connection, """
                CREATE TABLE users (
                  id INT PRIMARY KEY,
                  email VARCHAR(120),
                  role VARCHAR(30),
                  centro_atencion_id INT
                )
                """);
        ejecutar(connection, """
                CREATE TABLE turno (
                  id INT PRIMARY KEY,
                  centro_atencion_id INT
                )
                """);
        ejecutar(connection, "INSERT INTO users VALUES (3, 'ana@turnero.com', 'OPERADOR', 7)");
        ejecutar(connection, "INSERT INTO turno VALUES (40, 9)");
        ejecutar(connection, """
                INSERT INTO audit_log (id, entity_type, entity_id, action, performed_at, performed_by)
                VALUES
                  (1, 'TURNO', 1, 'CANCELLED_AUTO', TIMESTAMP '2026-01-01 10:00:00', 'SISTEMA'),
                  (2, 'LISTA_ESPERA', 2, 'OFERTA', TIMESTAMP '2026-01-02 10:00:00', 'SISTEMA_AUTOMATICO'),
                  (3, 'TURNO', 3, 'CANCEL', TIMESTAMP '2026-01-03 10:00:00', 'ana@turnero.com'),
                  (4, 'USUARIO', 3, 'UPDATE', TIMESTAMP '2026-01-04 10:00:00', 'admin@turnero.com'),
                  (5, 'TURNO', 40, 'CREATE', TIMESTAMP '2026-01-05 10:00:00', 'SISTEMA')
                """);
    }

    private static void ejecutar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String origen(Connection connection, int id) throws SQLException {
        return texto(connection, "SELECT source FROM audit_log WHERE id = " + id);
    }

    private static String rol(Connection connection, int id) throws SQLException {
        return texto(connection, "SELECT actor_role FROM audit_log WHERE id = " + id);
    }

    private static int centro(Connection connection, int id) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT centro_atencion_id FROM audit_log WHERE id = " + id)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static String texto(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
