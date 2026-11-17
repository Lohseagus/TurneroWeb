package unpsjb.labprog.backend.business.receta;

/**
 * Emite dentro de CheTurno. El identificador es propio; el CUIR queda vacío
 * hasta que una plataforma inscripta en ReNaPDiS lo asigne.
 */
public class LocalPlataforma implements PlataformaRecetaElectronica {

    @Override
    public ResultadoEmision emitir(SolicitudEmision solicitud) {
        return new ResultadoEmision(solicitud.identificador(), null, "local");
    }

    @Override
    public void anular(String identificador, String motivo) {
        // La anulación queda en el registro local. No hay repositorio remoto que notificar.
    }

    @Override
    public EstadoRemoto consultarEstado(String identificador) {
        return new EstadoRemoto(identificador, null, "local");
    }

    @Override
    public void registrarDispensa(String identificador, boolean total, String detalle) {
        // La farmacia autenticada todavía no existe. El servicio puede registrar la dispensa.
    }
}
