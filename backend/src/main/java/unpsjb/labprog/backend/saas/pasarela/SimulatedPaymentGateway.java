package unpsjb.labprog.backend.saas.pasarela;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import unpsjb.labprog.backend.saas.CicloFacturacion;

/**
 * Pasarela de desarrollo. Autoriza el cobro al instante y no llama a ninguna red.
 * Se activa con saas.payment.provider=simulated (valor por defecto).
 */
@Component
@ConditionalOnProperty(name = "saas.payment.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PasarelaPago {

    @Override
    public String proveedor() {
        return "simulated";
    }

    @Override
    public ResultadoPlanExterno asegurarPlan(SolicitudPlanExterno solicitud) {
        if (solicitud.preapprovalPlanIdExistente() != null && !solicitud.preapprovalPlanIdExistente().isBlank()) {
            return new ResultadoPlanExterno(solicitud.preapprovalPlanIdExistente(), proveedor());
        }
        String ciclo = solicitud.ciclo() == null ? CicloFacturacion.MENSUAL.name() : solicitud.ciclo().name();
        return new ResultadoPlanExterno("sim_plan_" + solicitud.codigoPlan() + "_" + ciclo, proveedor());
    }

    @Override
    public ResultadoPreapproval crearPreapproval(SolicitudPreapproval solicitud) {
        return autorizado(solicitud, "sim_pre_" + UUID.randomUUID());
    }

    @Override
    public ResultadoPreapproval actualizarMonto(String preapprovalId, SolicitudPreapproval solicitud) {
        return new ResultadoPreapproval(
                preapprovalId,
                "authorized",
                null,
                solicitud.montoConIva(),
                solicitud.moneda(),
                "Monto simulado actualizado");
    }

    @Override
    public ResultadoPreapproval pausar(String preapprovalId) {
        return new ResultadoPreapproval(preapprovalId, "paused", null, null, "ARS", "Pausada en la pasarela simulada");
    }

    @Override
    public ResultadoPreapproval reactivar(String preapprovalId) {
        return new ResultadoPreapproval(preapprovalId, "authorized", null, null, "ARS", "Reactivada en la pasarela simulada");
    }

    @Override
    public ResultadoPreapproval cancelar(String preapprovalId) {
        return new ResultadoPreapproval(preapprovalId, "cancelled", null, null, "ARS", "Cancelada en la pasarela simulada");
    }

    @Override
    public boolean validarFirma(String payload, String xSignature, String xRequestId) {
        return false;
    }

    @Override
    public EventoPasarela interpretarNotificacion(String payload, Map<String, String> headers) {
        return new EventoPasarela(TipoNotificacionPasarela.IGNORADA, null, null, null,
                "La pasarela simulada no procesa webhooks de Mercado Pago.");
    }

    private ResultadoPreapproval autorizado(SolicitudPreapproval solicitud, String preapprovalId) {
        return new ResultadoPreapproval(
                preapprovalId,
                "authorized",
                "sim_pay_" + UUID.randomUUID(),
                solicitud.montoConIva(),
                solicitud.moneda(),
                "Pago simulado autorizado");
    }
}
