package unpsjb.labprog.backend.business.hce;

import unpsjb.labprog.backend.model.hce.EstadoConsulta;

public final class ConsultaLifecycle {

    private ConsultaLifecycle() {
    }

    public static void assertBorrador(EstadoConsulta estado) {
        if (estado != EstadoConsulta.BORRADOR) {
            throw new HceImmutableException(
                    "La consulta está cerrada. Las correcciones se registran como enmienda, con autor, fecha y motivo.");
        }
    }

    public static void assertCerradaParaEnmienda(EstadoConsulta estado) {
        if (estado != EstadoConsulta.CERRADA) {
            throw new IllegalStateException("Solo una consulta cerrada admite enmienda. Cierre el borrador primero.");
        }
    }

    /**
     * IMC = peso (kg) / talla (m) al cuadrado. Null si falta un dato o no es positivo.
     */
    public static Double calcularImc(Double pesoKg, Double tallaCm) {
        if (pesoKg == null || tallaCm == null || pesoKg <= 0 || tallaCm <= 0) {
            return null;
        }
        double metros = tallaCm / 100.0;
        double valor = pesoKg / (metros * metros);
        return Math.round(valor * 100.0) / 100.0;
    }
}
