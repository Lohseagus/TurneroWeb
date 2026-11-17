package unpsjb.labprog.backend.business.receta;

/**
 * Adaptador de una plataforma de prescripción. Hoy la implementación local
 * no habla con el repositorio nacional: deja el lugar para el CUIR oficial.
 */
public interface PlataformaRecetaElectronica {

    ResultadoEmision emitir(SolicitudEmision solicitud);

    void anular(String identificador, String motivo);

    EstadoRemoto consultarEstado(String identificador);

    void registrarDispensa(String identificador, boolean total, String detalle);

    record SolicitudEmision(String tipoDocumento, String identificador, String hashContenido) {
    }

    record ResultadoEmision(String identificador, String cuir, String plataforma) {
    }

    record EstadoRemoto(String identificador, String cuir, String estado) {
    }
}
