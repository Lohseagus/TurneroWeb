package unpsjb.labprog.backend.presenter;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import unpsjb.labprog.backend.Response;
import unpsjb.labprog.backend.saas.ReglaSuscripcionException;
import unpsjb.labprog.backend.saas.SaasDtos.AddonDTO;
import unpsjb.labprog.backend.saas.SaasDtos.CambioComercialRequest;
import unpsjb.labprog.backend.saas.SaasDtos.ConfiguracionDTO;
import unpsjb.labprog.backend.saas.SaasDtos.CotizacionRequest;
import unpsjb.labprog.backend.saas.SaasDtos.ExtensionTrialRequest;
import unpsjb.labprog.backend.saas.SaasDtos.MotivoRequest;
import unpsjb.labprog.backend.saas.SaasDtos.PagoRequest;
import unpsjb.labprog.backend.saas.SaasDtos.PlanDTO;
import unpsjb.labprog.backend.saas.SaasDtos.RegistroRequest;
import unpsjb.labprog.backend.saas.SaasDtos.SedeRequest;
import unpsjb.labprog.backend.saas.SaasDtos.VincularCentroRequest;
import unpsjb.labprog.backend.saas.SaasService;
import unpsjb.labprog.backend.saas.pasarela.EventoPasarela;
import unpsjb.labprog.backend.saas.pasarela.PasarelaPago;

@RestController
@RequestMapping("/api/saas")
public class SaasPresenter {

    @Autowired
    private SaasService saasService;

    @Autowired
    private PasarelaPago pasarelaPago;

    @GetMapping("/planes")
    public ResponseEntity<Object> planes() {
        return Response.ok(saasService.catalogoPublico(), "Planes de CheTurno");
    }

    @PostMapping("/cotizar")
    public ResponseEntity<Object> cotizar(@RequestBody CotizacionRequest request) {
        try {
            return Response.ok(saasService.cotizar(request), "Cotización calculada");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/registro")
    public ResponseEntity<Object> registro(@RequestBody RegistroRequest request) {
        try {
            return Response.ok(saasService.registrar(request),
                    "Prueba iniciada. Ya podés ingresar con el email y la contraseña que elegiste.");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    /**
     * Webhook de Mercado Pago. La firma (x-signature) todavía no se valida:
     * MercadoPagoGateway.validarFirma rechaza hasta implementar el HMAC.
     */
    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Object> webhookMercadoPago(
            @RequestBody String payload,
            @RequestHeader(value = "x-signature", required = false) String firma,
            @RequestHeader(value = "x-request-id", required = false) String requestId) {
        if (!"mercadopago".equals(pasarelaPago.proveedor())) {
            return ResponseEntity.ok(Map.of(
                    "aplicado", false,
                    "motivo", "La pasarela activa es simulada. Esta notificación no se aplica."));
        }
        // TODO: validar HMAC-SHA256 del manifest id:{data.id};request-id:{x-request-id};ts:{ts}
        // con MP_WEBHOOK_SECRET antes de tocar la suscripción. Ver docs/SAAS.md.
        if (!pasarelaPago.validarFirma(payload, firma, requestId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "aplicado", false,
                    "motivo", "Firma no válida o validación pendiente. No se aplicó ningún cambio."));
        }
        EventoPasarela evento = pasarelaPago.interpretarNotificacion(payload, Map.of(
                "x-signature", firma == null ? "" : firma,
                "x-request-id", requestId == null ? "" : requestId));
        saasService.aplicarEventoPasarela(evento);
        return ResponseEntity.ok(Map.of("aplicado", true, "tipo", evento.tipo().name()));
    }

    @GetMapping("/mi-suscripcion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Object> miSuscripcion() {
        try {
            return Response.ok(saasService.miSuscripcion(), "Suscripción del centro");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/mi-suscripcion/pagar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Object> pagar(@RequestBody(required = false) PagoRequest request) {
        try {
            String ciclo = request == null ? null : request.ciclo;
            return Response.ok(saasService.pagarMiSuscripcion(ciclo), "Pago registrado");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/mi-suscripcion/cambiar-plan")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Object> cambiarPlan(@RequestBody CambioComercialRequest request) {
        try {
            return Response.ok(saasService.actualizarMiPlan(request), "Plan actualizado");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/mi-suscripcion/sedes")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Object> crearSede(@RequestBody SedeRequest request) {
        try {
            return Response.ok(saasService.crearSede(request), "Sede creada");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @GetMapping("/mi-suscripcion/reporte-consolidado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Object> reporteConsolidado() {
        try {
            return Response.ok(saasService.reporteConsolidado(), "Reporte consolidado");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @GetMapping("/admin/resumen")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> resumen() {
        return Response.ok(saasService.resumen(), "Tablero SaaS");
    }

    @GetMapping("/admin/suscripciones")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> suscripciones(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String q) {
        try {
            return Response.ok(saasService.listar(estado, plan, q), "Suscripciones");
        } catch (IllegalArgumentException e) {
            return Response.error(null, "El filtro de estado o plan no es válido.");
        }
    }

    @GetMapping("/admin/suscripciones/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> detalle(@PathVariable Integer id) {
        try {
            return Response.ok(saasService.detalleAdmin(id), "Detalle de suscripción");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/admin/suscripciones/{id}/extender-trial")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> extender(@PathVariable Integer id, @RequestBody ExtensionTrialRequest request) {
        try {
            int dias = request == null ? 0 : request.dias;
            return Response.ok(saasService.extenderTrial(id, dias), "Prueba extendida");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/admin/suscripciones/{id}/suspender")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> suspender(@PathVariable Integer id, @RequestBody(required = false) MotivoRequest request) {
        try {
            return Response.ok(saasService.suspender(id, request == null ? null : request.motivo), "Suscripción suspendida");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/admin/suscripciones/{id}/reactivar")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> reactivar(@PathVariable Integer id) {
        try {
            return Response.ok(saasService.reactivar(id), "Suscripción reactivada");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/admin/suscripciones/{id}/cancelar")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> cancelar(@PathVariable Integer id, @RequestBody(required = false) MotivoRequest request) {
        try {
            return Response.ok(saasService.cancelar(id, request == null ? null : request.motivo), "Suscripción cancelada");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/admin/suscripciones/{id}/cambiar-plan")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> cambiarPlanAdmin(@PathVariable Integer id, @RequestBody CambioComercialRequest request) {
        try {
            return Response.ok(saasService.cambiarPlanAdmin(id, request), "Plan actualizado");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PostMapping("/admin/suscripciones/{id}/vincular-centro")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> vincular(@PathVariable Integer id, @RequestBody VincularCentroRequest request) {
        try {
            saasService.vincularCentro(id, request == null ? null : request.centroId);
            return Response.ok(saasService.detalleAdmin(id), "Centro vinculado");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @GetMapping("/admin/planes")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> planesAdmin() {
        return Response.ok(saasService.catalogoAdmin(), "Planes y configuración");
    }

    @PutMapping("/admin/planes/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> actualizarPlan(@PathVariable Integer id, @RequestBody PlanDTO request) {
        try {
            return Response.ok(saasService.actualizarPlan(id, request), "Plan actualizado");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PutMapping("/admin/configuracion")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> actualizarConfig(@RequestBody ConfiguracionDTO request) {
        try {
            return Response.ok(saasService.actualizarConfiguracion(request), "Configuración actualizada");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @PutMapping("/admin/addons/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> actualizarAddon(@PathVariable Integer id, @RequestBody AddonDTO request) {
        try {
            return Response.ok(saasService.actualizarAddon(id, request), "Complemento actualizado");
        } catch (ReglaSuscripcionException e) {
            return Response.error(null, e.getMessage());
        }
    }

    @GetMapping("/admin/pagos")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Object> pagos() {
        return Response.ok(saasService.listarPagos(), "Pagos");
    }
}
