package unpsjb.labprog.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.EncuestaInvitacion;
import unpsjb.labprog.backend.model.ListaEspera;
import unpsjb.labprog.backend.model.Notificacion;
import unpsjb.labprog.backend.model.PagoSuscripcion;
import unpsjb.labprog.backend.model.PreferenciaHoraria;
import unpsjb.labprog.backend.model.Pregunta;
import unpsjb.labprog.backend.model.Suscripcion;
import unpsjb.labprog.backend.model.SuscripcionEvento;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.AccesoHistoriaClinica;
import unpsjb.labprog.backend.model.hce.AdjuntoClinico;
import unpsjb.labprog.backend.model.hce.Alergia;
import unpsjb.labprog.backend.model.hce.CatalogoMedicamento;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.DiagnosticoConsulta;
import unpsjb.labprog.backend.model.hce.OrdenEstudio;
import unpsjb.labprog.backend.model.hce.PerfilClinico;
import unpsjb.labprog.backend.model.hce.Receta;
import unpsjb.labprog.backend.model.hce.RecetaItem;

class EnumCheckConstraintRelaxerTest {

    @Test
    void elCatalogoCubreLasColumnasStringYLosValoresEntran() {
        List<ColumnaEnumerada> columnas = EnumColumnCatalog.de(List.of(
                AccesoHistoriaClinica.class,
                Notificacion.class,
                Turno.class,
                Suscripcion.class,
                SuscripcionEvento.class,
                PagoSuscripcion.class,
                ConfiguracionExcepcional.class,
                PreferenciaHoraria.class,
                Pregunta.class,
                EncuestaInvitacion.class,
                ListaEspera.class,
                User.class,
                Receta.class,
                OrdenEstudio.class,
                Consulta.class,
                PerfilClinico.class,
                Alergia.class,
                AdjuntoClinico.class,
                DiagnosticoConsulta.class,
                RecetaItem.class,
                CatalogoMedicamento.class));

        assertTrue(tiene(columnas, "hce_acceso", "accion"));
        assertTrue(tiene(columnas, "notificaciones", "tipo"));
        assertTrue(tiene(columnas, "turno", "estado"));
        assertTrue(tiene(columnas, "suscripcion", "estado"));
        assertTrue(tiene(columnas, "suscripcion", "ciclo"));
        assertTrue(tiene(columnas, "suscripcion", "estado_anterior"));
        assertTrue(tiene(columnas, "pago_suscripcion", "estado"));
        assertTrue(tiene(columnas, "suscripcion_evento", "tipo"));
        assertTrue(tiene(columnas, "configuracion_excepcional", "tipo"));
        assertTrue(columnas.stream().anyMatch(item -> item.tabla().equals("configuracion_excepcional")
                && item.columna().equals("tipo")
                && java.util.Arrays.stream(item.tipo().getEnumConstants()).anyMatch(valor -> valor.name().equals("AUSENCIA_MEDICO"))));
        assertTrue(tiene(columnas, "preferencia_horaria", "dia_de_la_semana"));
        assertTrue(tiene(columnas, "hce_receta", "estado"));
        assertTrue(tiene(columnas, "hce_receta", "tipo"));
        assertTrue(tiene(columnas, "hce_orden_estudio", "estado"));
        assertTrue(tiene(columnas, "hce_orden_estudio", "urgencia"));
        assertTrue(tiene(columnas, "users", "role"));
        assertTrue(tiene(columnas, "lista_espera", "urgencia_medica"));
        assertTrue(EnumColumnCatalog.valoresQueNoEntran(columnas).isEmpty(),
                () -> EnumColumnCatalog.valoresQueNoEntran(columnas).toString());
    }

    @Test
    void reconoceElCheckDeHibernateYNoUnoNumerico() {
        String postgres = "CHECK (((accion)::text = ANY ((ARRAY['VER_PERFIL'::character varying, 'ACCESO_DENEGADO'::character varying])::text[])))";
        assertTrue(EnumCheckConstraintRelaxer.esCheckDeEnum("hce_acceso", "accion", "hce_acceso_accion_check", postgres));
        assertTrue(EnumCheckConstraintRelaxer.esCheckDeEnum("hce_acceso", "accion", "HCE_ACCESO_ACCION_CHECK",
                "CHECK(\"ACCION\" IN('VER_PERFIL', 'ACCESO_DENEGADO'))"));
        assertFalse(EnumCheckConstraintRelaxer.esCheckDeEnum("hce_acceso", "accion", "hce_acceso_paciente_positivo",
                "CHECK (paciente_id > 0)"));
        assertFalse(EnumCheckConstraintRelaxer.esCheckDeEnum("hce_acceso", "accion", "otro",
                "CHECK (actor_email IS NOT NULL)"));
    }

    @Test
    void despuesDeSacarElCheckViejoSePuedeInsertarVerReceta() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:enumcheck;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE hce_acceso (
                          id IDENTITY PRIMARY KEY,
                          paciente_id INT NOT NULL,
                          accion VARCHAR(40) NOT NULL,
                          ocurrido_en TIMESTAMP NOT NULL,
                          CONSTRAINT hce_acceso_accion_check CHECK (accion IN ('VER_PERFIL', 'ACCESO_DENEGADO')),
                          CONSTRAINT hce_acceso_paciente_positivo CHECK (paciente_id > 0)
                        )
                        """);
            }

            assertTrue(insertarFalla(connection), "El CHECK viejo tiene que rechazar VER_RECETA antes del arreglo");

            EnumCheckConstraintRelaxer relaxer = new EnumCheckConstraintRelaxer(null, null);
            List<ColumnaEnumerada> columnas = EnumColumnCatalog.de(List.of(AccesoHistoriaClinica.class));
            int primera = relaxer.relajar(connection, columnas);
            int segunda = relaxer.relajar(connection, columnas);

            assertEquals(1, primera);
            assertEquals(0, segunda);
            insertar(connection, "VER_RECETA", 1);
            assertTrue(insertarFallaConPaciente(connection, 0), "El CHECK que no es de enum tiene que seguir");
            assertEquals(1, contar(connection, "VER_RECETA"));
        }
    }

    private static boolean tiene(List<ColumnaEnumerada> columnas, String tabla, String columna) {
        return columnas.stream().anyMatch(item -> item.tabla().equals(tabla) && item.columna().equals(columna));
    }

    private static boolean insertarFalla(Connection connection) {
        try {
            insertar(connection, "VER_RECETA", 1);
            return false;
        } catch (SQLException ex) {
            return true;
        }
    }

    private static boolean insertarFallaConPaciente(Connection connection, int pacienteId) {
        try {
            insertar(connection, "VER_PERFIL", pacienteId);
            return false;
        } catch (SQLException ex) {
            return true;
        }
    }

    private static void insertar(Connection connection, String accion, int pacienteId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hce_acceso (paciente_id, accion, ocurrido_en) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
            statement.setInt(1, pacienteId);
            statement.setString(2, accion);
            statement.executeUpdate();
        }
    }

    private static int contar(Connection connection, String accion) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM hce_acceso WHERE accion = ?")) {
            statement.setString(1, accion);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
