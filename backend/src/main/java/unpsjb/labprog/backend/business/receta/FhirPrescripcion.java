package unpsjb.labprog.backend.business.receta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import unpsjb.labprog.backend.model.hce.CodigoCie10;
import unpsjb.labprog.backend.model.hce.EstadoPrescripcion;
import unpsjb.labprog.backend.model.hce.OrdenEstudio;
import unpsjb.labprog.backend.model.hce.OrdenEstudioItem;
import unpsjb.labprog.backend.model.hce.Receta;
import unpsjb.labprog.backend.model.hce.RecetaItem;

/**
 * MedicationRequest y ServiceRequest de lectura. No valida contra el perfil nacional.
 */
@Component
public class FhirPrescripcion {

    public List<Map<String, Object>> receta(Receta receta) {
        List<Map<String, Object>> recursos = new ArrayList<>();
        int n = 0;
        for (RecetaItem item : receta.getItems()) {
            n++;
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("resourceType", "MedicationRequest");
            resource.put("id", "receta-" + receta.getId() + "-" + n);
            resource.put("status", estado(receta.getEstado()));
            resource.put("intent", "order");
            List<Map<String, Object>> identifiers = new ArrayList<>();
            if (receta.getIdentificador() != null) {
                identifiers.add(Map.of("system", "urn:cheturno:receta", "value", receta.getIdentificador()));
            }
            if (receta.getCuir() != null && !receta.getCuir().isBlank()) {
                identifiers.add(Map.of("system", "https://recetas.msal.gob.ar/cuir", "value", receta.getCuir()));
            }
            if (!identifiers.isEmpty()) {
                resource.put("identifier", identifiers);
            }
            resource.put("medicationCodeableConcept", Map.of("text", textoMedicamento(item)));
            resource.put("subject", ref("Patient", "paciente-" + receta.getPaciente().getId()));
            resource.put("encounter", ref("Encounter", "consulta-" + receta.getConsulta().getId()));
            resource.put("requester", ref("Practitioner", "medico-" + receta.getMedico().getId()));
            if (receta.getFechaEmision() != null) {
                resource.put("authoredOn", receta.getFechaEmision().toString());
            }
            String dosis = (item.getDosis() == null ? "" : item.getDosis())
                    + (item.getFrecuencia() == null ? "" : " " + item.getFrecuencia());
            if (!dosis.isBlank()) {
                resource.put("dosageInstruction", List.of(Map.of("text", dosis.trim(), "route",
                        Map.of("text", item.getVia() == null ? "" : item.getVia()))));
            }
            if (item.getCantidad() != null) {
                Map<String, Object> dispense = new LinkedHashMap<>();
                dispense.put("quantity", Map.of("value", item.getCantidad()));
                if (receta.getFechaEmision() != null && receta.getFechaVencimiento() != null) {
                    dispense.put("validityPeriod", Map.of(
                            "start", receta.getFechaEmision().toString(),
                            "end", receta.getFechaVencimiento().toString()));
                }
                resource.put("dispenseRequest", dispense);
            }
            if (item.isNoSustituir()) {
                resource.put("substitution", Map.of("allowedBoolean", false));
            }
            if (receta.getDiagnosticoCode() != null) {
                resource.put("reasonCode", List.of(Map.of(
                        "coding", List.of(Map.of(
                                "system", receta.getDiagnosticoSystem() == null
                                        ? CodigoCie10.SISTEMA_CIE10
                                        : receta.getDiagnosticoSystem(),
                                "code", receta.getDiagnosticoCode(),
                                "display", receta.getDiagnosticoDisplay() == null ? "" : receta.getDiagnosticoDisplay())),
                        "text", receta.getDiagnosticoDisplay() == null ? receta.getDiagnosticoCode()
                                : receta.getDiagnosticoDisplay())));
            }
            recursos.add(resource);
        }
        return recursos;
    }

    public List<Map<String, Object>> orden(OrdenEstudio orden) {
        List<Map<String, Object>> recursos = new ArrayList<>();
        int n = 0;
        for (OrdenEstudioItem item : orden.getItems()) {
            n++;
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("resourceType", "ServiceRequest");
            resource.put("id", "orden-" + orden.getId() + "-" + n);
            resource.put("status", estado(orden.getEstado()));
            resource.put("intent", "order");
            resource.put("priority", prioridad(orden));
            Map<String, Object> code = new LinkedHashMap<>();
            code.put("text", item.getNombrePractica());
            if (item.getCode() != null) {
                code.put("coding", List.of(Map.of(
                        "system", item.getSystemUri() == null ? "urn:cheturno:nomenclador" : item.getSystemUri(),
                        "code", item.getCode(),
                        "display", item.getNombrePractica())));
            }
            resource.put("code", code);
            resource.put("subject", ref("Patient", "paciente-" + orden.getPaciente().getId()));
            resource.put("encounter", ref("Encounter", "consulta-" + orden.getConsulta().getId()));
            resource.put("requester", ref("Practitioner", "medico-" + orden.getMedico().getId()));
            if (orden.getIndicacionClinica() != null) {
                resource.put("note", List.of(Map.of("text", orden.getIndicacionClinica())));
            }
            if (orden.getDiagnosticoCode() != null) {
                resource.put("reasonCode", List.of(Map.of("text",
                        orden.getDiagnosticoCode() + " " + (orden.getDiagnosticoDisplay() == null
                                ? ""
                                : orden.getDiagnosticoDisplay()))));
            }
            if (orden.getIdentificador() != null) {
                resource.put("identifier", List.of(Map.of("system", "urn:cheturno:orden", "value", orden.getIdentificador())));
            }
            recursos.add(resource);
        }
        return recursos;
    }

    private String textoMedicamento(RecetaItem item) {
        String texto = item.getNombreGenerico();
        if (item.getConcentracion() != null) {
            texto += " " + item.getConcentracion();
        }
        if (item.getFormaFarmaceutica() != null) {
            texto += " " + item.getFormaFarmaceutica();
        }
        return texto;
    }

    private String estado(EstadoPrescripcion estado) {
        if (estado == null) {
            return "draft";
        }
        return switch (estado) {
            case BORRADOR -> "draft";
            case EMITIDA, DISPENSADA_PARCIAL -> "active";
            case DISPENSADA_TOTAL -> "completed";
            case ANULADA -> "cancelled";
            case VENCIDA -> "stopped";
        };
    }

    private String prioridad(OrdenEstudio orden) {
        if (orden.getUrgencia() == null) {
            return "routine";
        }
        return switch (orden.getUrgencia()) {
            case NORMAL -> "routine";
            case PREFERENTE -> "urgent";
            case URGENTE -> "stat";
        };
    }

    private Map<String, Object> ref(String type, String id) {
        return Map.of("reference", type + "/" + id);
    }
}
