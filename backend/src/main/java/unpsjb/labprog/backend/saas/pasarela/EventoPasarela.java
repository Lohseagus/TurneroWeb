package unpsjb.labprog.backend.saas.pasarela;

public record EventoPasarela(
        TipoNotificacionPasarela tipo,
        String recursoId,
        String estadoMp,
        String externalReference,
        String detalle) {
}
