package unpsjb.labprog.backend.business.receta;

/**
 * Punto de extensión para una plataforma registrada en ReNaPDiS y el
 * repositorio nacional de recetas. No tiene credenciales ni cliente HTTP:
 * se selecciona con {@code recetas.plataforma=renapdis} y rechaza la operación
 * para no inventar un CUIR.
 */
public class RepositorioNacionalStub implements PlataformaRecetaElectronica {

    static final String PENDIENTE = "La plataforma del repositorio nacional no está conectada. "
            + "Falta el alta en ReNaPDiS y las credenciales del entorno.";

    @Override
    public ResultadoEmision emitir(SolicitudEmision solicitud) {
        throw new IllegalStateException(PENDIENTE);
    }

    @Override
    public void anular(String identificador, String motivo) {
        throw new IllegalStateException(PENDIENTE);
    }

    @Override
    public EstadoRemoto consultarEstado(String identificador) {
        throw new IllegalStateException(PENDIENTE);
    }

    @Override
    public void registrarDispensa(String identificador, boolean total, String detalle) {
        throw new IllegalStateException(PENDIENTE);
    }
}
