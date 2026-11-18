package unpsjb.labprog.backend.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Descubre las columnas {@code @Enumerated(STRING)} a partir de las entidades.
 * Los nombres siguen {@code CamelCaseToUnderscoresNamingStrategy}, que es la
 * estrategia física de este proyecto: {@code ddl-auto=update} no cambia un
 * CHECK ya creado, así que el arranque tiene que encontrar la columna real.
 */
public final class EnumColumnCatalog {

    private EnumColumnCatalog() {
    }

    public static List<ColumnaEnumerada> de(Iterable<Class<?>> entidades) {
        List<ColumnaEnumerada> columnas = new ArrayList<>();
        for (Class<?> entidad : entidades) {
            if (entidad == null || !entidad.isAnnotationPresent(Entity.class)) {
                continue;
            }
            String tabla = tabla(entidad);
            for (Field field : camposPersistentes(entidad)) {
                Enumerated enumerated = field.getAnnotation(Enumerated.class);
                if (enumerated == null || enumerated.value() != EnumType.STRING || !field.getType().isEnum()) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                int largo = column == null ? 255 : column.length();
                @SuppressWarnings("unchecked")
                Class<? extends Enum<?>> tipo = (Class<? extends Enum<?>>) field.getType();
                columnas.add(new ColumnaEnumerada(tabla, columna(field), largo, tipo));
            }
        }
        return List.copyOf(columnas);
    }

    /**
     * Un valor del enum no entra en el varchar. Hibernate no agranda la columna
     * si el dato viejo ya cabe, y el insert falla distinto al CHECK.
     */
    public static List<String> valoresQueNoEntran(List<ColumnaEnumerada> columnas) {
        List<String> noEntran = new ArrayList<>();
        for (ColumnaEnumerada columna : columnas) {
            if (columna.largo() <= 0) {
                continue;
            }
            for (Enum<?> valor : columna.tipo().getEnumConstants()) {
                if (valor.name().length() > columna.largo()) {
                    noEntran.add(columna.tabla() + "." + columna.columna() + " " + valor.name()
                            + " (" + valor.name().length() + " > " + columna.largo() + ")");
                }
            }
        }
        return noEntran;
    }

    private static List<Field> camposPersistentes(Class<?> entidad) {
        List<Field> campos = new ArrayList<>();
        for (Class<?> tipo = entidad; tipo != null && tipo != Object.class; tipo = tipo.getSuperclass()) {
            if (tipo != entidad && tipo.isAnnotationPresent(Entity.class)) {
                break;
            }
            if (tipo != entidad && !tipo.isAnnotationPresent(MappedSuperclass.class)) {
                break;
            }
            for (Field field : tipo.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                campos.add(field);
            }
        }
        return campos;
    }

    private static String tabla(Class<?> entidad) {
        Table table = entidad.getAnnotation(Table.class);
        String nombre = table != null && table.name() != null && !table.name().isBlank()
                ? table.name()
                : entidad.getSimpleName();
        return snake(nombre);
    }

    private static String columna(Field field) {
        Column column = field.getAnnotation(Column.class);
        String nombre = column != null && column.name() != null && !column.name().isBlank()
                ? column.name()
                : field.getName();
        return snake(nombre);
    }

    /**
     * Misma regla que {@code CamelCaseToUnderscoresNamingStrategy}: un "_" entre
     * una minúscula, una mayúscula y otra minúscula, y después todo en minúscula.
     */
    static String snake(String nombre) {
        StringBuilder builder = new StringBuilder(nombre.replace('.', '_'));
        for (int i = 1; i < builder.length() - 1; i++) {
            if (esSeparable(builder.charAt(i - 1), builder.charAt(i), builder.charAt(i + 1))) {
                builder.insert(i++, '_');
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean esSeparable(char antes, char actual, char despues) {
        return Character.isLowerCase(antes) && Character.isUpperCase(actual) && Character.isLowerCase(despues);
    }
}
