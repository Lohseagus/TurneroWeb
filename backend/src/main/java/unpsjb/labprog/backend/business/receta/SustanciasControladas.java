package unpsjb.labprog.backend.business.receta;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import unpsjb.labprog.backend.model.hce.ListaControlada;
import unpsjb.labprog.backend.model.hce.TipoReceta;

public final class SustanciasControladas {

    public static final String MENSAJE = "Los psicotrópicos y estupefacientes (listas II a IV) requieren receta "
            + "oficial archivada y no se emiten en forma digital por esta plataforma.";

    private static final Set<String> NOMBRES = Set.of(
            "clonazepam", "alprazolam", "diazepam", "lorazepam", "bromazepam", "flunitrazepam",
            "midazolam", "fenobarbital", "zolpidem", "morfina", "codeina", "tramadol", "fentanilo",
            "oxicodona", "metilfenidato", "buprenorfina", "metadona", "anfetamina");

    private SustanciasControladas() {
    }

    public static boolean esControlada(String nombreGenerico, ListaControlada lista, TipoReceta tipoReceta) {
        if (tipoReceta != null && tipoReceta != TipoReceta.COMUN) {
            return true;
        }
        if (lista != null && lista != ListaControlada.NINGUNA) {
            return true;
        }
        return nombreEnLista(nombreGenerico);
    }

    public static boolean nombreEnLista(String nombreGenerico) {
        if (nombreGenerico == null || nombreGenerico.isBlank()) {
            return false;
        }
        String normalizado = normalizar(nombreGenerico);
        for (String nombre : NOMBRES) {
            if (normalizado.contains(nombre)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizar(String texto) {
        String nfd = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "");
    }
}
