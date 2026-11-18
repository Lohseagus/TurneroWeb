package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.ListaEsperaDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.ListaEspera;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.User;

/**
 * Lote 4: la lista de espera y los filtros de centro con cuentas de tres centros.
 * Las solicitudes 1, 2 y 3 son de los centros 1, 2 y 3.
 */
class ListaEsperaCentroTest {

    private final ListaEsperaRepository repository = mock(ListaEsperaRepository.class);
    private final MedicoRepository medicoRepository = mock(MedicoRepository.class);
    private final StaffMedicoRepository staffMedicoRepository = mock(StaffMedicoRepository.class);
    private final PacienteRepository pacienteRepository = mock(PacienteRepository.class);

    private final CentroAccessGuard guard = new CentroAccessGuard(medicoRepository, staffMedicoRepository,
            mock(TurnoRepository.class), mock(ConsultorioRepository.class),
            mock(DisponibilidadMedicoRepository.class), mock(EsquemaTurnoRepository.class),
            new PacienteAccessGuard(pacienteRepository));
    private final ListaEsperaService service = new ListaEsperaService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "centroAccess", guard);
        when(repository.findAll()).thenReturn(List.of(solicitud(1), solicitud(2), solicitud(3)));
        for (long id = 1; id <= 3; id++) {
            when(repository.findById(id)).thenReturn(Optional.of(solicitud((int) id)));
            when(repository.existsById(id)).thenReturn(true);
        }
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elOperadorListaSoloLasSolicitudesDeSuCentro() {
        autenticar(Role.OPERADOR, "operador@turnero.com", 2);

        assertEquals(List.of(2), centros(service.findAll()));
        assertEquals(List.of(2), centros(service.findPendientes()));
        assertEquals(1, service.obtenerEstadisticasGenerales().get("totalSolicitudes"));
    }

    @Test
    void elOperadorNoLeeNiModificaUnaSolicitudDeOtroCentro() {
        autenticar(Role.OPERADOR, "operador@turnero.com", 2);

        assertTrue(service.findById(2L).isPresent());
        assertThrows(AccessDeniedException.class, () -> service.findById(1L));
        assertThrows(AccessDeniedException.class, () -> service.marcarComoResuelta(1L));
        assertThrows(AccessDeniedException.class, () -> service.marcarComoCubierta(3L));
        assertThrows(AccessDeniedException.class, () -> service.marcarComoResueltaPorOtroMedio(3L));
        assertThrows(AccessDeniedException.class, () -> service.delete(1L));

        ListaEsperaDTO mudanza = new ListaEsperaDTO();
        mudanza.setId(2L);
        mudanza.setCentroAtencionId(1);
        assertThrows(AccessDeniedException.class, () -> service.update(mudanza));
        assertThrows(AccessDeniedException.class, () -> service.save(mudanza));

        verify(repository, never()).deleteById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void elSuperadminYLasTareasSinUsuarioVenTodo() {
        assertEquals(List.of(1, 2, 3), centros(service.findAll()));

        autenticar(Role.SUPERADMIN, "root@turnero.com", null);
        assertEquals(List.of(1, 2, 3), centros(service.findAll()));
        assertTrue(service.findById(1L).isPresent());
    }

    @Test
    void elMedicoSumaLosCentrosDondeTieneStaff() {
        Medico medico = new Medico();
        medico.setId(50);
        when(medicoRepository.findByEmailIgnoreCase("medico@turnero.com")).thenReturn(Optional.of(medico));
        StaffMedico staff = new StaffMedico();
        staff.setCentroAtencion(centro(3));
        when(staffMedicoRepository.findByMedicoId(50)).thenReturn(List.of(staff));
        autenticar(Role.MEDICO, "medico@turnero.com", 1);

        assertEquals(Set.of(1, 3), guard.centrosPermitidos());
        assertTrue(guard.puedeOperar(3));
        assertThrows(AccessDeniedException.class, () -> guard.exigirCentro(2));
    }

    @Test
    void unCentroPedidoQueNoEsDeLaCuentaSeRechazaEnVezDeIgnorarse() {
        autenticar(Role.ADMINISTRADOR, "admin@turnero.com", 2);

        assertEquals(2, guard.centroParaFiltro(null));
        assertEquals(2, guard.centroParaFiltro(2));
        assertThrows(AccessDeniedException.class, () -> guard.centroParaFiltro(1));

        autenticar(Role.SUPERADMIN, "root@turnero.com", null);
        assertEquals(null, guard.centroParaFiltro(null));
        assertEquals(1, guard.centroParaFiltro(1));
    }

    @Test
    void unaCuentaDePersonalSinCentroNoVeNada() {
        autenticar(Role.OPERADOR, "sin.centro@turnero.com", null);

        assertEquals(List.of(), service.findAll());
        assertThrows(AccessDeniedException.class, () -> service.findById(1L));
        assertThrows(AccessDeniedException.class, () -> guard.centroParaFiltro(null));
    }

    private static List<Integer> centros(List<ListaEsperaDTO> solicitudes) {
        return solicitudes.stream().map(ListaEsperaDTO::getCentroAtencionId).toList();
    }

    private static CentroAtencion centro(int id) {
        CentroAtencion centro = new CentroAtencion();
        centro.setId(id);
        return centro;
    }

    private static ListaEspera solicitud(int centroId) {
        Paciente paciente = new Paciente();
        paciente.setId(100 + centroId);
        Especialidad especialidad = new Especialidad();
        especialidad.setId(1);
        ListaEspera solicitud = new ListaEspera();
        solicitud.setId((long) centroId);
        solicitud.setPaciente(paciente);
        solicitud.setEspecialidad(especialidad);
        solicitud.setCentroAtencion(centro(centroId));
        solicitud.setEstado("PENDIENTE");
        solicitud.setUrgenciaMedica(ListaEspera.UrgenciaMedica.BAJA);
        solicitud.setFechaSolicitud(LocalDateTime.now().minusDays(2));
        return solicitud;
    }

    private static void autenticar(Role role, String email, Integer centroId) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        if (centroId != null) {
            user.setCentroAtencion(centro(centroId));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
