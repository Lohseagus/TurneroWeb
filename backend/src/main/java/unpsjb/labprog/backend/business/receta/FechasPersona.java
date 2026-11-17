package unpsjb.labprog.backend.business.receta;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public final class FechasPersona {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private FechasPersona() {
    }

    public static LocalDate aLocal(Date fecha) {
        if (fecha == null) {
            return null;
        }
        if (fecha instanceof java.sql.Date sql) {
            return sql.toLocalDate();
        }
        return fecha.toInstant().atZone(AR).toLocalDate();
    }

    public static Date aDate(LocalDate fecha) {
        return fecha == null ? null : java.sql.Date.valueOf(fecha);
    }
}
