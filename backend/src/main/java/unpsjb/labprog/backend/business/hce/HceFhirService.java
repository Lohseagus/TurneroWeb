package unpsjb.labprog.backend.business.hce;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.receta.FhirPrescripcion;
import unpsjb.labprog.backend.business.repository.AdjuntoClinicoRepository;
import unpsjb.labprog.backend.business.repository.OrdenEstudioRepository;
import unpsjb.labprog.backend.business.repository.RecetaRepository;
import unpsjb.labprog.backend.dto.hce.AlergiaDTO;
import unpsjb.labprog.backend.dto.hce.ConsultaDTO;
import unpsjb.labprog.backend.dto.hce.DiagnosticoDTO;
import unpsjb.labprog.backend.dto.hce.MedicacionDTO;
import unpsjb.labprog.backend.dto.hce.PerfilClinicoDTO;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;
import unpsjb.labprog.backend.model.hce.AdjuntoClinico;
import unpsjb.labprog.backend.model.hce.CodigoCie10;

/**
 * Exportación de lectura con forma de Bundle FHIR R4. No es un servidor FHIR:
 * deja los identificadores y los code systems listos para el bus nacional.
 */
@Service
public class HceFhirService {

    static final String DNI_SYSTEM = "http://renaper.gob.ar/dni";
    static final String FEDERADO_SYSTEM = "https://bus-nacional.msal.gob.ar/fhir/NamingSystem/paciente";
    static final String MATRICULA_SYSTEM = "urn:cheturno:matricula";
    static final String REFEPS_SYSTEM = "https://sisa.msal.gov.ar/refeps";

    private final HistoriaClinicaService historia;
    private final HceAccessGuard guard;
    private final AdjuntoClinicoRepository adjuntoRepository;
    private final RecetaRepository recetaRepository;
    private final OrdenEstudioRepository ordenRepository;
    private final FhirPrescripcion fhirPrescripcion;

    public HceFhirService(HistoriaClinicaService historia, HceAccessGuard guard,
            AdjuntoClinicoRepository adjuntoRepository, RecetaRepository recetaRepository,
            OrdenEstudioRepository ordenRepository, FhirPrescripcion fhirPrescripcion) {
        this.historia = historia;
        this.guard = guard;
        this.adjuntoRepository = adjuntoRepository;
        this.recetaRepository = recetaRepository;
        this.ordenRepository = ordenRepository;
        this.fhirPrescripcion = fhirPrescripcion;
    }

    @Transactional
    public Map<String, Object> bundle(Integer pacienteId) {
        Paciente paciente = historia.cargarPaciente(pacienteId);
        guard.assertLeePerfil(paciente);
        PerfilClinicoDTO perfil = historia.obtenerPerfil(pacienteId);
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(patient(perfil)));
        for (AlergiaDTO alergia : perfil.getAlergias()) {
            entries.add(entry(allergy(pacienteId, alergia)));
        }
        for (MedicacionDTO medicacion : perfil.getMedicaciones()) {
            if (medicacion.isActiva()) {
                entries.add(entry(medication(pacienteId, medicacion)));
            }
        }
        for (ConsultaDTO consulta : historia.consultasVisibles(pacienteId)) {
            entries.add(entry(practitioner(consulta)));
            entries.add(entry(encounter(consulta)));
            for (DiagnosticoDTO diagnostico : consulta.getDiagnosticos()) {
                entries.add(entry(condition(consulta, diagnostico)));
            }
            entries.add(entry(observation(consulta, "8480-6", "Tensión sistólica", consulta.getTensionSistolica(), "mmHg")));
            entries.add(entry(observation(consulta, "8462-4", "Tensión diastólica", consulta.getTensionDiastolica(), "mmHg")));
            entries.add(entry(observation(consulta, "8867-4", "Frecuencia cardíaca", consulta.getFrecuenciaCardiaca(), "/min")));
            entries.add(entry(observation(consulta, "8310-5", "Temperatura", consulta.getTemperatura(), "Cel")));
            entries.add(entry(observation(consulta, "29463-7", "Peso", consulta.getPesoKg(), "kg")));
            entries.add(entry(observation(consulta, "8302-2", "Talla", consulta.getTallaCm(), "cm")));
            entries.add(entry(observation(consulta, "39156-5", "IMC", consulta.getImc(), "kg/m2")));
            for (var receta : recetaRepository.findByConsulta_IdOrderByCreadaEnDesc(consulta.getId())) {
                if (!guard.puedeLeerConsulta(receta.getConsulta())) {
                    continue;
                }
                for (Map<String, Object> recurso : fhirPrescripcion.receta(receta)) {
                    entries.add(entry(recurso));
                }
            }
            for (var orden : ordenRepository.findByConsulta_IdOrderByCreadaEnDesc(consulta.getId())) {
                if (!guard.puedeLeerConsulta(orden.getConsulta())) {
                    continue;
                }
                for (Map<String, Object> recurso : fhirPrescripcion.orden(orden)) {
                    entries.add(entry(recurso));
                }
            }
        }
        for (AdjuntoClinico adjunto : adjuntoRepository.findByPaciente_IdOrderByCreadoEnDesc(pacienteId)) {
            if (adjunto.getConsulta() != null && !guard.puedeLeerConsulta(adjunto.getConsulta())) {
                continue;
            }
            entries.add(entry(document(adjunto)));
        }
        entries.removeIf(item -> item.get("resource") == null);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "collection");
        bundle.put("entry", entries);
        guard.auditar(paciente, AccionAuditoriaHce.EXPORTAR_FHIR, "BUNDLE", String.valueOf(pacienteId), null,
                "Exportación FHIR de lectura");
        return bundle;
    }

    private Map<String, Object> patient(PerfilClinicoDTO perfil) {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("resourceType", "Patient");
        patient.put("id", "paciente-" + perfil.getPacienteId());
        List<Map<String, Object>> identifiers = new ArrayList<>();
        if (perfil.getDni() != null) {
            identifiers.add(identifier(DNI_SYSTEM, String.valueOf(perfil.getDni())));
        }
        if (perfil.getIdentificadorFederado() != null) {
            identifiers.add(identifier(FEDERADO_SYSTEM, perfil.getIdentificadorFederado()));
        }
        patient.put("identifier", identifiers);
        patient.put("name", List.of(Map.of("family", valor(perfil.getApellido()), "given", List.of(valor(perfil.getNombre())))));
        return patient;
    }

    private Map<String, Object> practitioner(ConsultaDTO consulta) {
        Map<String, Object> practitioner = new LinkedHashMap<>();
        practitioner.put("resourceType", "Practitioner");
        practitioner.put("id", "medico-" + consulta.getMedicoId());
        List<Map<String, Object>> identifiers = new ArrayList<>();
        if (consulta.getMatricula() != null) {
            identifiers.add(identifier(MATRICULA_SYSTEM, consulta.getMatricula()));
        }
        if (consulta.getRefepsId() != null) {
            identifiers.add(identifier(REFEPS_SYSTEM, consulta.getRefepsId()));
        }
        practitioner.put("identifier", identifiers);
        practitioner.put("name", List.of(Map.of("text", valor(consulta.getMedicoNombre()))));
        return practitioner;
    }

    private Map<String, Object> encounter(ConsultaDTO consulta) {
        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("resourceType", "Encounter");
        encounter.put("id", "consulta-" + consulta.getId());
        encounter.put("status", "CERRADA".equals(consulta.getEstado()) ? "finished" : "in-progress");
        encounter.put("class", Map.of("code", "AMB"));
        encounter.put("subject", ref("Patient", "paciente-" + consulta.getPacienteId()));
        encounter.put("participant", List.of(Map.of("individual", ref("Practitioner", "medico-" + consulta.getMedicoId()))));
        if (consulta.getMotivoConsulta() != null) {
            encounter.put("reasonCode", List.of(Map.of("text", consulta.getMotivoConsulta())));
        }
        return encounter;
    }

    private Map<String, Object> condition(ConsultaDTO consulta, DiagnosticoDTO diagnostico) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("resourceType", "Condition");
        condition.put("id", "condicion-" + diagnostico.getId());
        condition.put("clinicalStatus", Map.of("coding", List.of(Map.of("code", "active"))));
        condition.put("code", Map.of(
                "coding", List.of(Map.of(
                        "system", diagnostico.getSystemUri() == null ? CodigoCie10.SISTEMA_CIE10 : diagnostico.getSystemUri(),
                        "code", diagnostico.getCode(),
                        "display", diagnostico.getDisplay())),
                "text", diagnostico.getDisplay()));
        condition.put("subject", ref("Patient", "paciente-" + consulta.getPacienteId()));
        condition.put("encounter", ref("Encounter", "consulta-" + consulta.getId()));
        return condition;
    }

    private Map<String, Object> allergy(Integer pacienteId, AlergiaDTO alergia) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "AllergyIntolerance");
        resource.put("id", "alergia-" + alergia.getId());
        resource.put("clinicalStatus", Map.of("coding", List.of(Map.of("code", alergia.isActiva() ? "active" : "inactive"))));
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("text", alergia.getSustancia());
        if (alergia.getCode() != null) {
            code.put("coding", List.of(Map.of(
                    "system", alergia.getSystemUri() == null ? "urn:cheturno:alergia" : alergia.getSystemUri(),
                    "code", alergia.getCode(),
                    "display", alergia.getSustancia())));
        }
        resource.put("code", code);
        resource.put("patient", ref("Patient", "paciente-" + pacienteId));
        if (alergia.getCriticidad() != null) {
            resource.put("criticality", "ALTA".equals(alergia.getCriticidad()) ? "high" : "low");
        }
        return resource;
    }

    private Map<String, Object> medication(Integer pacienteId, MedicacionDTO medicacion) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "MedicationStatement");
        resource.put("id", "medicacion-" + medicacion.getId());
        resource.put("status", "active");
        Map<String, Object> concept = new LinkedHashMap<>();
        concept.put("text", medicacion.getFarmaco());
        if (medicacion.getCode() != null) {
            concept.put("coding", List.of(Map.of(
                    "system", medicacion.getSystemUri() == null ? "urn:cheturno:medicacion" : medicacion.getSystemUri(),
                    "code", medicacion.getCode(),
                    "display", medicacion.getFarmaco())));
        }
        resource.put("medicationCodeableConcept", concept);
        resource.put("subject", ref("Patient", "paciente-" + pacienteId));
        if (medicacion.getDosis() != null || medicacion.getFrecuencia() != null) {
            resource.put("dosage", List.of(Map.of("text",
                    (medicacion.getDosis() == null ? "" : medicacion.getDosis() + " ")
                            + (medicacion.getFrecuencia() == null ? "" : medicacion.getFrecuencia()))));
        }
        return resource;
    }

    private Map<String, Object> observation(ConsultaDTO consulta, String loinc, String texto, Object valor, String unidad) {
        if (valor == null) {
            return null;
        }
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("resourceType", "Observation");
        observation.put("id", "obs-" + consulta.getId() + "-" + loinc);
        observation.put("status", "final");
        observation.put("code", Map.of("coding", List.of(Map.of(
                "system", "http://loinc.org",
                "code", loinc,
                "display", texto)), "text", texto));
        observation.put("subject", ref("Patient", "paciente-" + consulta.getPacienteId()));
        observation.put("encounter", ref("Encounter", "consulta-" + consulta.getId()));
        observation.put("valueQuantity", Map.of("value", valor, "unit", unidad));
        return observation;
    }

    private Map<String, Object> document(AdjuntoClinico adjunto) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("resourceType", "DocumentReference");
        document.put("id", "adjunto-" + adjunto.getId());
        document.put("status", "current");
        document.put("description", adjunto.getNombreOriginal());
        document.put("subject", ref("Patient", "paciente-" + adjunto.getPaciente().getId()));
        document.put("content", List.of(Map.of("attachment", Map.of(
                "contentType", adjunto.getContentType(),
                "title", adjunto.getNombreOriginal(),
                "size", adjunto.getTamanioBytes()))));
        return document;
    }

    private Map<String, Object> entry(Map<String, Object> resource) {
        if (resource == null) {
            return Map.of();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("resource", resource);
        return entry;
    }

    private Map<String, Object> identifier(String system, String value) {
        Map<String, Object> identifier = new LinkedHashMap<>();
        identifier.put("system", system);
        identifier.put("value", value);
        return identifier;
    }

    private Map<String, Object> ref(String type, String id) {
        return Map.of("reference", type + "/" + id);
    }

    private String valor(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
