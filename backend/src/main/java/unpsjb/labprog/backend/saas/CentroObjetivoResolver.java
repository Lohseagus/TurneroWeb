package unpsjb.labprog.backend.saas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.OrdenEstudioRepository;
import unpsjb.labprog.backend.business.repository.RecetaRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;

/**
 * Centro al que apunta una escritura de alguien que no tiene centro propio
 * (el paciente, sobre todo). El personal usa el centro de su usuario.
 */
@Component
public class CentroObjetivoResolver {

    static final int TOPE_CUERPO = 1_048_576;

    private static final Pattern TURNO = Pattern.compile("^/api/turno/(\\d+)(?:/.*)?$");
    private static final Pattern LISTA = Pattern.compile("^/api/lista-espera/(\\d+)(?:/.*)?$");
    private static final Pattern CONSULTA = Pattern.compile("^/api/hce/consultas/(\\d+)(?:/.*)?$");
    private static final Pattern RECETA = Pattern.compile("^/api/recetas/(\\d+)(?:/.*)?$");
    private static final Pattern ORDEN = Pattern.compile("^/api/ordenes-estudio/(\\d+)(?:/.*)?$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private ConsultorioRepository consultorioRepository;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;

    @Autowired
    private ListaEsperaRepository listaEsperaRepository;

    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private RecetaRepository recetaRepository;

    @Autowired
    private OrdenEstudioRepository ordenEstudioRepository;

    public Resolucion resolver(HttpServletRequest request, String path) {
        String ruta = RutasEscrituraSaas.normalizar(path);
        Integer porRuta = centroPorRuta(ruta);
        if (porRuta != null) {
            return new Resolucion(porRuta, request);
        }
        if (!llevaCuerpo(request.getMethod()) || !esJson(request)) {
            return new Resolucion(null, request);
        }
        int largo = request.getContentLength();
        if (largo > TOPE_CUERPO) {
            return new Resolucion(null, request);
        }
        CuerpoPedidoCacheado cache;
        try {
            cache = new CuerpoPedidoCacheado(request);
        } catch (Exception e) {
            return new Resolucion(null, request);
        }
        if (cache.cuerpo().length > TOPE_CUERPO) {
            return new Resolucion(null, cache);
        }
        return new Resolucion(centroPorCuerpo(cache.cuerpo()), cache);
    }

    private Integer centroPorRuta(String ruta) {
        Integer turnoId = grupo(TURNO, ruta);
        if (turnoId != null) {
            return id(turnoRepository.findCentroIdByTurnoId(turnoId));
        }
        Long listaId = grupoLargo(LISTA, ruta);
        if (listaId != null) {
            return id(listaEsperaRepository.findCentroIdByListaId(listaId));
        }
        Long consultaId = grupoLargo(CONSULTA, ruta);
        if (consultaId != null) {
            return id(consultaRepository.findCentroIdByConsultaId(consultaId));
        }
        Long recetaId = grupoLargo(RECETA, ruta);
        if (recetaId != null) {
            return id(recetaRepository.findCentroIdByRecetaId(recetaId));
        }
        Long ordenId = grupoLargo(ORDEN, ruta);
        if (ordenId != null) {
            return id(ordenEstudioRepository.findCentroIdByOrdenId(ordenId));
        }
        return null;
    }

    private Integer centroPorCuerpo(byte[] cuerpo) {
        if (cuerpo == null || cuerpo.length == 0) {
            return null;
        }
        try {
            JsonNode nodo = objectMapper.readTree(cuerpo);
            if (nodo == null || !nodo.isObject()) {
                return null;
            }
            Integer explicito = primero(nodo, "centroAtencionId", "centroId");
            if (explicito != null) {
                return explicito;
            }
            Integer consultorioId = entero(nodo.get("consultorioId"));
            if (consultorioId != null) {
                Integer centro = id(consultorioRepository.findCentroIdByConsultorioId(consultorioId));
                if (centro != null) {
                    return centro;
                }
            }
            Integer staffId = entero(nodo.get("staffMedicoId"));
            if (staffId != null) {
                Integer centro = id(staffMedicoRepository.findCentroIdByStaffId(staffId));
                if (centro != null) {
                    return centro;
                }
            }
            Integer turnoId = entero(nodo.get("turnoId"));
            if (turnoId != null) {
                Integer centro = id(turnoRepository.findCentroIdByTurnoId(turnoId));
                if (centro != null) {
                    return centro;
                }
            }
            Long consultaId = largo(nodo.get("consultaId"));
            if (consultaId != null) {
                return id(consultaRepository.findCentroIdByConsultaId(consultaId));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer primero(JsonNode nodo, String... campos) {
        for (String campo : campos) {
            Integer valor = entero(nodo.get(campo));
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    private static Integer entero(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) {
            return null;
        }
        if (nodo.isNumber()) {
            return nodo.intValue();
        }
        if (nodo.isTextual()) {
            try {
                return Integer.valueOf(nodo.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Long largo(JsonNode nodo) {
        Integer corto = entero(nodo);
        return corto == null ? null : corto.longValue();
    }

    private static Integer grupo(Pattern pattern, String ruta) {
        Matcher matcher = pattern.matcher(ruta);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    private static Long grupoLargo(Pattern pattern, String ruta) {
        Matcher matcher = pattern.matcher(ruta);
        if (!matcher.matches()) {
            return null;
        }
        return Long.valueOf(matcher.group(1));
    }

    private static Integer id(java.util.Optional<Integer> valor) {
        return valor == null ? null : valor.orElse(null);
    }

    private static boolean llevaCuerpo(String metodo) {
        if (metodo == null) {
            return false;
        }
        String normalizado = metodo.toUpperCase(java.util.Locale.ROOT);
        return normalizado.equals("POST") || normalizado.equals("PUT") || normalizado.equals("PATCH");
    }

    private static boolean esJson(HttpServletRequest request) {
        String tipo = request.getContentType();
        return tipo != null && tipo.toLowerCase(java.util.Locale.ROOT).contains("json");
    }

    public record Resolucion(Integer centroId, HttpServletRequest request) {
    }
}
