package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.OrdenEstudioRepository;
import unpsjb.labprog.backend.business.repository.RecetaRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;

@ExtendWith(MockitoExtension.class)
class CentroObjetivoResolverTest {

    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private ConsultorioRepository consultorioRepository;
    @Mock
    private StaffMedicoRepository staffMedicoRepository;
    @Mock
    private ListaEsperaRepository listaEsperaRepository;
    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private RecetaRepository recetaRepository;
    @Mock
    private OrdenEstudioRepository ordenEstudioRepository;

    @InjectMocks
    private CentroObjetivoResolver resolver;

    @Test
    void elTurnoDeLaRutaDefineElCentro() {
        when(turnoRepository.findCentroIdByTurnoId(15)).thenReturn(Optional.of(4));
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/turno/15/cancelar");

        CentroObjetivoResolver.Resolucion resolucion = resolver.resolver(request, "/api/turno/15/cancelar");

        assertEquals(4, resolucion.centroId());
        assertEquals(request, resolucion.request());
    }

    @Test
    void laReservaUsaElCentroDelCuerpo() {
        MockHttpServletRequest request = pedido("{\"centroId\":7,\"staffMedicoId\":8}");

        CentroObjetivoResolver.Resolucion resolucion = resolver.resolver(request, "/api/turno/asignar");

        assertEquals(7, resolucion.centroId());
    }

    @Test
    void sinCentroExplicitoUsaElStaffMedico() {
        when(staffMedicoRepository.findCentroIdByStaffId(8)).thenReturn(Optional.of(2));
        MockHttpServletRequest request = pedido("{\"staffMedicoId\":8}");

        CentroObjetivoResolver.Resolucion resolucion = resolver.resolver(request, "/api/turno/asignar");

        assertEquals(2, resolucion.centroId());
    }

    @Test
    void laListaDeEsperaExistenteSaleDeLaRuta() {
        when(listaEsperaRepository.findCentroIdByListaId(3L)).thenReturn(Optional.of(1));
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/lista-espera/3");

        assertEquals(1, resolver.resolver(request, "/api/lista-espera/3").centroId());
    }

    @Test
    void unCuerpoSinCentroNoInventaUno() {
        MockHttpServletRequest request = pedido("{\"nombre\":\"Ada\"}");

        assertNull(resolver.resolver(request, "/api/pacientes").centroId());
    }

    private static MockHttpServletRequest pedido(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/turno/asignar");
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
