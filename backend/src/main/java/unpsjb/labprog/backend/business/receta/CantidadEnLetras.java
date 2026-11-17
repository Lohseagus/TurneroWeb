package unpsjb.labprog.backend.business.receta;

public final class CantidadEnLetras {

    private static final String[] HASTA_19 = {
            "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
            "dieciocho", "diecinueve"
    };
    private static final String[] DECENAS = {
            "", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"
    };
    private static final String[] VEINTI = {
            "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco",
            "veintiséis", "veintisiete", "veintiocho", "veintinueve"
    };
    private static final String[] CENTENAS = {
            "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"
    };

    private CantidadEnLetras() {
    }

    public static String de(int cantidad) {
        if (cantidad < 0 || cantidad > 999) {
            throw new IllegalArgumentException("La cantidad tiene que estar entre 0 y 999");
        }
        if (cantidad == 100) {
            return "cien";
        }
        if (cantidad < 20) {
            return HASTA_19[cantidad];
        }
        if (cantidad < 30) {
            return VEINTI[cantidad - 20];
        }
        if (cantidad < 100) {
            int decena = cantidad / 10;
            int unidad = cantidad % 10;
            return unidad == 0 ? DECENAS[decena] : DECENAS[decena] + " y " + HASTA_19[unidad];
        }
        int centena = cantidad / 100;
        int resto = cantidad % 100;
        return resto == 0 ? CENTENAS[centena] : CENTENAS[centena] + " " + de(resto);
    }
}
