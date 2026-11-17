package unpsjb.labprog.backend.business.receta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class CodigosDocumento {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter FECHA = DateTimeFormatter.BASIC_ISO_DATE;

    private CodigosDocumento() {
    }

    /**
     * Identificador local con forma estable: CT + R|O + fecha + 20 caracteres.
     * No es el CUIR del repositorio nacional. Ese valor, cuando exista, se guarda aparte.
     */
    public static String identificador(String letra, LocalDate fecha) {
        return "CT" + letra + FECHA.format(fecha) + aleatorio(20);
    }

    /** 32 bytes en hexadecimal. No es adivinable por fuerza bruta razonable. */
    public static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String aleatorio(int largo) {
        char[] salida = new char[largo];
        for (int i = 0; i < largo; i++) {
            salida[i] = ALFABETO[RANDOM.nextInt(ALFABETO.length)];
        }
        return new String(salida);
    }

    public static String sha256(String canonico) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
