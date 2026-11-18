package unpsjb.labprog.backend.config;

/**
 * Columna persistida con {@code @Enumerated(STRING)}.
 * El nombre ya está en la forma física de Hibernate (snake_case).
 */
public record ColumnaEnumerada(String tabla, String columna, int largo, Class<? extends Enum<?>> tipo) {
}
