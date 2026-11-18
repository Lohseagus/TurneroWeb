package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.OrganizacionRepository;
import unpsjb.labprog.backend.business.repository.PlanSuscripcionRepository;
import unpsjb.labprog.backend.business.repository.SuscripcionRepository;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Organizacion;
import unpsjb.labprog.backend.model.PlanSuscripcion;
import unpsjb.labprog.backend.model.Suscripcion;

@ExtendWith(MockitoExtension.class)
class SuscripcionHerenciaTest {

    @Mock
    private PlanSuscripcionRepository planRepository;
    @Mock
    private CentroAtencionRepository centroAtencionRepository;
    @Mock
    private OrganizacionRepository organizacionRepository;
    @Mock
    private SuscripcionRepository suscripcionRepository;
    @Mock
    private SuscripcionAccessService access;

    @InjectMocks
    private SaasService service;

    @BeforeEach
    void elSaveDevuelveLaSuscripcion() {
        lenient().when(suscripcionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void unCentroSinOrganizacionRecibeUnaActivaHeredada() {
        PlanSuscripcion plan = planCentro();
        CentroAtencion centro = new CentroAtencion();
        centro.setId(4);
        centro.setNombre("Consultorio viejo");
        centro.setTelefono("2804000000");
        when(planRepository.findByCodigo(CodigosPlan.CENTRO)).thenReturn(Optional.of(plan));
        when(centroAtencionRepository.findByOrganizacionIsNull()).thenReturn(List.of(centro));
        when(centroAtencionRepository.findConOrganizacionSinSuscripcion()).thenReturn(List.of());
        when(organizacionRepository.save(any())).thenAnswer(invocation -> {
            Organizacion organizacion = invocation.getArgument(0);
            organizacion.setId(9);
            return organizacion;
        });

        service.heredarCentrosExistentes();

        assertEquals(9, centro.getOrganizacion().getId());
        Suscripcion guardada = suscripcionGuardada();
        assertEquals(EstadoSuscripcion.ACTIVA, guardada.getEstado());
        assertTrue(guardada.isHeredada());
        assertEquals(plan, guardada.getPlan());
        verify(access).registrar(any(), org.mockito.ArgumentMatchers.eq(TipoEventoSuscripcion.HEREDADA), any(), org.mockito.ArgumentMatchers.eq("sistema"));
    }

    @Test
    void unaOrganizacionSinSuscripcionTambienSeHereda() {
        PlanSuscripcion plan = planCentro();
        Organizacion organizacion = new Organizacion();
        organizacion.setId(6);
        CentroAtencion centro = new CentroAtencion();
        centro.setId(2);
        centro.setOrganizacion(organizacion);
        when(planRepository.findByCodigo(CodigosPlan.CENTRO)).thenReturn(Optional.of(plan));
        when(centroAtencionRepository.findByOrganizacionIsNull()).thenReturn(List.of());
        when(centroAtencionRepository.findConOrganizacionSinSuscripcion()).thenReturn(List.of(centro));
        when(suscripcionRepository.findByOrganizacion_Id(6)).thenReturn(Optional.empty());

        service.heredarCentrosExistentes();

        Suscripcion guardada = suscripcionGuardada();
        assertEquals(organizacion, guardada.getOrganizacion());
        assertEquals(EstadoSuscripcion.ACTIVA, guardada.getEstado());
        assertTrue(guardada.isHeredada());
    }

    @Test
    void sinPlanCentroNoInventaSuscripciones() {
        when(planRepository.findByCodigo(CodigosPlan.CENTRO)).thenReturn(Optional.empty());

        service.heredarCentrosExistentes();

        verify(suscripcionRepository, never()).save(any());
    }

    private Suscripcion suscripcionGuardada() {
        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static PlanSuscripcion planCentro() {
        PlanSuscripcion plan = new PlanSuscripcion();
        plan.setCodigo(CodigosPlan.CENTRO);
        plan.setNombre("Centro");
        return plan;
    }
}
