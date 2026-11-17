package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.dto.PacienteDTO;
import unpsjb.labprog.backend.exception.PacienteSinFichaException;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class PacientePropioServiceTest {

    private PacienteRepository repository;
    private UserService userService;
    private AuditLogService auditLogService;
    private PacienteService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(PacienteRepository.class);
        userService = org.mockito.Mockito.mock(UserService.class);
        auditLogService = org.mockito.Mockito.mock(AuditLogService.class);
        service = new PacienteService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void laFichaSaleDelEmailDeLaCuentaYNoDeUnId() {
        entrar(2558L, Role.OPERADOR, "ana.martinez@turnero.com");
        when(repository.findByUserEmail("ana.martinez@turnero.com")).thenReturn(Optional.of(paciente(2558, "ana.martinez@turnero.com")));

        PacienteDTO dto = service.actual().orElseThrow();

        assertEquals(2558, dto.getId());
        assertEquals("ana.martinez@turnero.com", dto.getEmail());
        verify(repository, never()).findById(any());
    }

    @Test
    void otraCuentaNoRecibeEsaFicha() {
        entrar(99L, Role.PACIENTE, "otro@turnero.com");
        when(repository.findByUserEmail("otro@turnero.com")).thenReturn(Optional.empty());

        assertTrue(service.actual().isEmpty());
    }

    @Test
    void editarIgnoraUnIdAjeno() {
        entrar(20L, Role.OPERADOR, "ana.martinez@turnero.com");
        Paciente propio = paciente(2558, "ana.martinez@turnero.com");
        when(repository.findByUserEmail("ana.martinez@turnero.com")).thenReturn(Optional.of(propio));
        when(repository.findById(2558)).thenReturn(Optional.of(propio));
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.findByEmail(anyString())).thenReturn(Optional.of(cuenta()));

        PacienteDTO pedido = new PacienteDTO();
        pedido.setId(999);
        pedido.setNombre("Ana");
        pedido.setApellido("Martínez");
        pedido.setEmail("ana.martinez@turnero.com");
        pedido.setTelefono("2990000000");
        pedido.setDni(30111222L);

        PacienteDTO guardado = service.actualizarPropio(pedido);

        assertEquals(2558, guardado.getId());
        ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
        verify(repository).save(captor.capture());
        assertEquals(2558, captor.getValue().getId());
        verify(repository, never()).findById(999);
    }

    @Test
    void sinFichaNoSePuedeEditar() {
        entrar(20L, Role.OPERADOR, "sin.ficha@turnero.com");
        when(repository.findByUserEmail("sin.ficha@turnero.com")).thenReturn(Optional.empty());

        assertThrows(PacienteSinFichaException.class, () -> service.actualizarPropio(new PacienteDTO()));
        verify(repository, never()).save(any());
    }

    private void entrar(long userId, Role role, String email) {
        User user = cuenta();
        user.setId(userId);
        user.setRole(role);
        user.setEmail(email);
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User cuenta() {
        User user = new User();
        user.setId(20L);
        user.setRole(Role.OPERADOR);
        user.setEmail("ana.martinez@turnero.com");
        user.setNombre("Ana");
        user.setApellido("Martínez");
        user.setDni(30111222L);
        user.setTelefono("2990000000");
        user.setHashedPassword("hash");
        return user;
    }

    private Paciente paciente(int id, String email) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        paciente.setNombre("Ana");
        paciente.setApellido("Martínez");
        paciente.setEmail(email);
        paciente.setDni(30111222L);
        paciente.setTelefono("2990000000");
        return paciente;
    }
}
