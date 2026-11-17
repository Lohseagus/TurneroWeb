package unpsjb.labprog.backend.business.receta;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class LimiteVerificacion {

    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public void consumir(String clave, int maxPorMinuto) {
        String id = clave == null || clave.isBlank() ? "desconocida" : clave;
        long minuto = System.currentTimeMillis() / 60_000L;
        Ventana ventana = ventanas.compute(id, (k, actual) -> {
            if (actual == null || actual.minuto != minuto) {
                return new Ventana(minuto, 1);
            }
            actual.conteo++;
            return actual;
        });
        if (ventanas.size() > 10_000) {
            ventanas.entrySet().removeIf(entry -> entry.getValue().minuto != minuto);
        }
        if (ventana.conteo > Math.max(1, maxPorMinuto)) {
            throw new VerificacionLimitadaException();
        }
    }

    private static final class Ventana {
        private final long minuto;
        private int conteo;

        private Ventana(long minuto, int conteo) {
            this.minuto = minuto;
            this.conteo = conteo;
        }
    }
}
