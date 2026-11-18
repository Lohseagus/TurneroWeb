package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.exception.PacienteSinFichaException;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.ListaEspera;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class ListaEsperaPropiaTest {

    private final ListaEsperaRepository repository = mock(ListaEsperaRepository.class);
    private final PacienteRepository pacienteRepository = mock(PacienteRepository.class);
    private final PacienteAccessGuard pacienteAccess = new PacienteAccessGuard(pacienteRepository);
    private final ListaEsperaService service = new ListaEsperaService();

    private final Paciente titular = paciente(7, "ana@turnero.com");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "pacienteRepository", pacienteRepository);
        ReflectionTestUtils.setField(service, "pacienteAccess", pacienteAccess);
        when(pacienteRepository.findByUserEmail("ana@turnero.com")).thenReturn(Optional.of(titular));
        when(pacienteRepository.findById(7)).thenReturn(Optional.of(titular));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void laCuentaVeSoloSusSolicitudes() {
        autenticar("ana@turnero.com");
        ListaEspera propia = solicitud(4L, titular);
        when(repository.findByPaciente(titular)).thenReturn(List.of(propia));

        assertEquals(List.of(4L), service.deLaCuenta().stream().map(dto -> dto.getId()).toList());
    }

    @Test
    void sinFichaNoListaNada() {
        autenticar("sin.ficha@turnero.com");
        when(pacienteRepository.findByUserEmail("sin.ficha@turnero.com")).thenReturn(Optional.empty());

        assertThrows(PacienteSinFichaException.class, service::deLaCuenta);
    }

    @Test
    void elTitularCancelaUnaPendiente() {
        autenticar("ana@turnero.com");
        ListaEspera propia = solicitud(4L, titular);
        when(repository.findById(4L)).thenReturn(Optional.of(propia));
        when(repository.save(propia)).thenReturn(propia);

        assertEquals("CANCELADA", service.cancelarPropia(4L).getEstado());
        verify(repository).save(propia);
    }

    @Test
    void noCancelaLaSolicitudDeOtroPacienteNiUnaQueNoEstaPendiente() {
        autenticar("ana@turnero.com");
        ListaEspera ajena = solicitud(9L, paciente(8, "otro@turnero.com"));
        when(repository.findById(9L)).thenReturn(Optional.of(ajena));
        assertThrows(AccessDeniedException.class, () -> service.cancelarPropia(9L));

        ListaEspera cubierta = solicitud(4L, titular);
        cubierta.setEstado("CUBIERTA");
        when(repository.findById(4L)).thenReturn(Optional.of(cubierta));
        assertThrows(IllegalStateException.class, () -> service.cancelarPropia(4L));
    }

    private static Paciente paciente(int id, String email) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        paciente.setNombre("Ana");
        paciente.setApellido("Test");
        paciente.setEmail(email);
        paciente.setDni(20000000L + id);
        return paciente;
    }

    private static ListaEspera solicitud(long id, Paciente paciente) {
        Especialidad especialidad = new Especialidad();
        especialidad.setId(1);
        especialidad.setNombre("Cardiología");
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setNombre("Centro");
        ListaEspera solicitud = new ListaEspera();
        solicitud.setId(id);
        solicitud.setPaciente(paciente);
        solicitud.setEspecialidad(especialidad);
        solicitud.setCentroAtencion(centro);
        solicitud.setEstado("PENDIENTE");
        solicitud.setUrgenciaMedica(ListaEspera.UrgenciaMedica.BAJA);
        solicitud.setFechaSolicitud(LocalDateTime.now().minusDays(1));
        return solicitud;
    }

    private static void autenticar(String email) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(Role.PACIENTE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
