package unpsjb.labprog.backend.saas;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class RelojArgentina {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private RelojArgentina() {
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA);
    }
}
