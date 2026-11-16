package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.SuscripcionEventoRepository;
import unpsjb.labprog.backend.business.repository.SuscripcionRepository;
import unpsjb.labprog.backend.business.repository.UserRepository;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Organizacion;
import unpsjb.labprog.backend.model.PlanSuscripcion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Suscripcion;

@ExtendWith(MockitoExtension.class)
class SuscripcionAccessServiceTest {

    @Mock
    private CentroAtencionRepository centroAtencionRepository;
    @Mock
    private SuscripcionRepository suscripcionRepository;
    @Mock
    private StaffMedicoRepository staffMedicoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConsultorioRepository consultorioRepository;
    @Mock
    private SuscripcionEventoRepository eventoRepository;

    @InjectMocks
    private SuscripcionAccessService service;

    @Test
    void sinSuscripcionNoBloquea() {
        when(centroAtencionRepository.findById(8)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.assertOperacion(8, RecursoSaas.TURNO));
    }

    @Test
    void trialVencidoRechazaUnTurnoNuevo() {
        suscripcion(EstadoSuscripcion.VENCIDA, null);
        ReglaSuscripcionException error = assertThrows(ReglaSuscripcionException.class,
                () -> service.assertOperacion(1, RecursoSaas.TURNO));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("solo lectura"));
    }

    @Test
    void trialVigentePermiteElTurno() {
        Suscripcion suscripcion = suscripcion(EstadoSuscripcion.TRIAL, LocalDateTime.now().plusDays(30));
        suscripcion.setFechaFinTrial(LocalDateTime.now().plusDays(30));
        assertDoesNotThrow(() -> service.assertOperacion(1, RecursoSaas.TURNO));
    }

    @Test
    void profesionalNoPuedeAbrirLaSegundaSede() {
        Suscripcion suscripcion = suscripcion(EstadoSuscripcion.ACTIVA, LocalDateTime.now().plusDays(20));
        suscripcion.getPlan().setMaxCentros(1);
        when(staffMedicoRepository.countDistinctMedicosByOrganizacion(5)).thenReturn(1L);
        when(userRepository.countByRoleAndCentroAtencion_Organizacion_Id(Role.OPERADOR, 5)).thenReturn(0L);
        when(consultorioRepository.countByCentroAtencion_Organizacion_Id(5)).thenReturn(1L);
        when(centroAtencionRepository.countByOrganizacion_Id(5)).thenReturn(1L);
        when(centroAtencionRepository.countEspecialidadesByOrganizacion(5)).thenReturn(1L);

        assertThrows(ReglaSuscripcionException.class, () -> service.assertOperacion(1, RecursoSaas.CENTRO));
    }

    private Suscripcion suscripcion(EstadoSuscripcion estado, LocalDateTime finPeriodo) {
        Organizacion organizacion = new Organizacion();
        organizacion.setId(5);
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setOrganizacion(organizacion);
        PlanSuscripcion plan = new PlanSuscripcion();
        plan.setNombre("Profesional");
        plan.setPrecioPorProfesional(java.math.BigDecimal.ZERO);
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setEstado(estado);
        suscripcion.setOrganizacion(organizacion);
        suscripcion.setPlan(plan);
        suscripcion.setFechaFinPeriodo(finPeriodo);
        suscripcion.setFechaFinTrial(estado == EstadoSuscripcion.TRIAL ? finPeriodo : LocalDateTime.now().minusDays(1));
        when(centroAtencionRepository.findById(1)).thenReturn(Optional.of(centro));
        when(suscripcionRepository.findByOrganizacion_Id(5)).thenReturn(Optional.of(suscripcion));
        return suscripcion;
    }
}
