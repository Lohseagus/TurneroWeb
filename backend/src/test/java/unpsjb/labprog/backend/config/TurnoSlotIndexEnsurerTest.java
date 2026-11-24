package unpsjb.labprog.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class TurnoSlotIndexEnsurerTest {

    @Test
    void conDuplicadosNoCreaElIndiceNiFrena() throws Exception {
        try (Connection connection = memoria("dup")) {
            crearTabla(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO turno VALUES (1, 8, DATE '2026-12-07', TIME '10:00:00', 'PROGRAMADO', FALSE)");
                statement.execute("INSERT INTO turno VALUES (2, 8, DATE '2026-12-07', TIME '10:00:00', 'CONFIRMADO', FALSE)");
            }
            TurnoSlotIndexEnsurer ensurer = new TurnoSlotIndexEnsurer(null);
            assertEquals(0, ensurer.asegurar(connection));
        }
    }

    @Test
    void unCanceladoNoCuentaComoDuplicadoYElArranqueSigue() throws Exception {
        try (Connection connection = memoria("libre")) {
            crearTabla(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO turno VALUES (1, 8, DATE '2026-12-07', TIME '10:00:00', 'PROGRAMADO', FALSE)");
                statement.execute("INSERT INTO turno VALUES (2, 8, DATE '2026-12-07', TIME '10:00:00', 'CANCELADO', FALSE)");
                statement.execute("INSERT INTO turno VALUES (3, 8, DATE '2026-12-07', TIME '10:00:00', 'PROGRAMADO', TRUE)");
            }
            TurnoSlotIndexEnsurer ensurer = new TurnoSlotIndexEnsurer(null);
            int creado = ensurer.asegurar(connection);
            assertTrue(creado == 0 || creado == 1);
            assertTrue(TurnoSlotIndexEnsurer.CREAR_POSTGRES.contains("CANCELADO"));
            assertTrue(TurnoSlotIndexEnsurer.CREAR_POSTGRES.contains("sobreturno"));
        }
    }

    private static Connection memoria(String nombre) throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:" + nombre + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static void crearTabla(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE turno (
                      id INT PRIMARY KEY,
                      staff_medico_id INT,
                      fecha DATE,
                      hora_inicio TIME,
                      estado VARCHAR(20),
                      sobreturno BOOLEAN
                    )
                    """);
        }
    }
}
