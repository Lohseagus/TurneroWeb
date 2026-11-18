package unpsjb.labprog.backend.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

/**
 * Saca los CHECK que Hibernate arma para un {@code @Enumerated(STRING)}.
 *
 * <p>Con {@code spring.jpa.hibernate.ddl-auto=update}, Hibernate crea el CHECK
 * la primera vez ({@code hce_acceso_accion_check}: {@code accion IN (...)} con
 * los valores de ese arranque) y no lo vuelve a escribir cuando el enum gana
 * constantes. En producción eso rechazó {@code VER_RECETA} y el resto de las
 * acciones de receta y orden, y la lectura de "Mis recetas" terminó en
 * "No se pudo completar la operación".
 *
 * <p>Decisión: en cada arranque se eliminan esos CHECK y la validación queda
 * en Java, que es la que ya rechaza un valor fuera del enum antes de llegar a
 * la base. Recrear el CHECK con la lista nueva también funcionaría, pero
 * {@code ddl-auto=update} lo volvería a dejar viejo en el próximo deploy si
 * este paso no corre después, y un CHECK de 30 valores no aporta nada que el
 * enum no esté controlando. El paso es idempotente: si el CHECK ya no está,
 * no hace nada. No toca otros CHECK (por ejemplo {@code paciente_id > 0}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EnumCheckConstraintRelaxer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(EnumCheckConstraintRelaxer.class);

    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    public EnumCheckConstraintRelaxer(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<ColumnaEnumerada> columnas = columnasMapeadas();
            List<String> noEntran = EnumColumnCatalog.valoresQueNoEntran(columnas);
            if (!noEntran.isEmpty()) {
                logger.error("Hay valores de enum más largos que la columna: {}", noEntran);
            }
            int eliminados;
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                eliminados = relajar(connection, columnas);
            }
            logger.info("CHECK de enums alineados. Columnas revisadas: {}. Restricciones eliminadas: {}.",
                    columnas.size(), eliminados);
        } catch (RuntimeException | SQLException ex) {
            // Un fallo acá no puede tirar abajo el proceso: el sitio sigue con el
            // CHECK viejo, que es el bug que ya tenía, y el log queda para verlo.
            logger.error("No se pudieron sacar los CHECK de los enums. La validación de la base puede seguir desactualizada.", ex);
        }
    }

    List<ColumnaEnumerada> columnasMapeadas() {
        List<Class<?>> entidades = new ArrayList<>();
        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            entidades.add(entity.getJavaType());
        }
        return EnumColumnCatalog.de(entidades);
    }

    /**
     * @return cantidad de CHECK eliminados
     */
    public int relajar(Connection connection, List<ColumnaEnumerada> columnas) throws SQLException {
        String producto = connection.getMetaData().getDatabaseProductName();
        if (producto == null) {
            return 0;
        }
        String normalizado = producto.toLowerCase(Locale.ROOT);
        if (!normalizado.contains("postgresql") && !normalizado.contains("h2")) {
            logger.warn("No se ajustan CHECK de enums en {}.", producto);
            return 0;
        }
        int eliminados = 0;
        for (ColumnaEnumerada columna : columnas) {
            for (Restriccion restriccion : restricciones(connection, columna.tabla())) {
                if (!esCheckDeEnum(columna.tabla(), columna.columna(), restriccion.nombre(), restriccion.definicion())) {
                    continue;
                }
                eliminar(connection, restriccion.tabla(), restriccion.nombre());
                eliminados++;
                logger.info("Se eliminó el CHECK {} de {}.{} ({})",
                        restriccion.nombre(), columna.tabla(), columna.columna(), abreviar(restriccion.definicion()));
            }
        }
        return eliminados;
    }

    /**
     * Un CHECK es de enum si Hibernate/Postgres lo nombró {@code tabla_columna_check}
     * o si la definición obliga a la columna a una lista ({@code IN} o {@code = ANY}).
     */
    static boolean esCheckDeEnum(String tabla, String columna, String nombre, String definicion) {
        String esperado = (tabla + "_" + columna + "_check").toLowerCase(Locale.ROOT);
        String nombreNormalizado = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        if (nombreNormalizado.matches(Pattern.quote(esperado) + "\\d*")) {
            return true;
        }
        if (definicion == null || columna == null) {
            return false;
        }
        String def = definicion.toLowerCase(Locale.ROOT).replace("\"", "");
        if (!contieneIdentificador(def, columna.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return def.contains(" in(") || def.contains(" in (") || def.contains("= any") || def.contains("=any");
    }

    private static boolean contieneIdentificador(String definicion, String columna) {
        int desde = 0;
        while (desde < definicion.length()) {
            int en = definicion.indexOf(columna, desde);
            if (en < 0) {
                return false;
            }
            boolean izquierda = en == 0 || !Character.isLetterOrDigit(definicion.charAt(en - 1));
            int fin = en + columna.length();
            boolean derecha = fin >= definicion.length() || !Character.isLetterOrDigit(definicion.charAt(fin));
            if (izquierda && derecha) {
                return true;
            }
            desde = en + 1;
        }
        return false;
    }

    private static List<Restriccion> restricciones(Connection connection, String tabla) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String producto = meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (producto.contains("postgresql")) {
            return restriccionesPostgres(connection, tabla);
        }
        return restriccionesH2(connection, tabla);
    }

    private static List<Restriccion> restriccionesPostgres(Connection connection, String tabla) throws SQLException {
        String sql = """
                SELECT nsp.nspname, rel.relname, con.conname, pg_get_constraintdef(con.oid)
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                WHERE con.contype = 'c'
                  AND nsp.nspname = current_schema()
                  AND lower(rel.relname) = lower(?)
                """;
        List<Restriccion> encontradas = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tabla);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    encontradas.add(new Restriccion(rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        }
        return encontradas;
    }

    private static List<Restriccion> restriccionesH2(Connection connection, String tabla) throws SQLException {
        String sql = """
                SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_TYPE = 'CHECK'
                  AND UPPER(tc.TABLE_NAME) = UPPER(?)
                """;
        List<Restriccion> encontradas = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tabla);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String expresion = rs.getString(3);
                    if (expresion == null || expresion.isBlank()) {
                        continue;
                    }
                    encontradas.add(new Restriccion(rs.getString(1), rs.getString(2), expresion));
                }
            }
        }
        return encontradas;
    }

    private static void eliminar(Connection connection, String tabla, String nombre) throws SQLException {
        String sql = "ALTER TABLE " + citar(tabla) + " DROP CONSTRAINT " + citar(nombre);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String citar(String identificador) {
        return "\"" + identificador.replace("\"", "\"\"") + "\"";
    }

    private static String abreviar(String definicion) {
        if (definicion == null) {
            return "";
        }
        return definicion.length() <= 180 ? definicion : definicion.substring(0, 180) + "…";
    }

    private record Restriccion(String tabla, String nombre, String definicion) {
    }
}
