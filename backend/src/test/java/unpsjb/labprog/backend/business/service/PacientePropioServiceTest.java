package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.dto.PacienteDTO;
import unpsjb.labprog.backend.exception.DatosFichaIncompletosException;
import unpsjb.labprog.backend.exception.FichaAjenaException;
import unpsjb.labprog.backend.exception.PacienteSinFichaException;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

class PacientePropioServiceTest {

    private PacienteRepository repository;
    private MedicoRepository medicoRepository;
    private UserService userService;
    private AuditLogService auditLogService;
    private PacienteService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(PacienteRepository.class);
        medicoRepository = org.mockito.Mockito.mock(MedicoRepository.class);
        userService = org.mockito.Mockito.mock(UserService.class);
        auditLogService = org.mockito.Mockito.mock(AuditLogService.class);
        service = new PacienteService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "medicoRepository", medicoRepository);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        when(medicoRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findByDni(any())).thenReturn(Optional.empty());
        when(repository.findByTelefono(any())).thenReturn(Optional.empty());
        when(repository.findByUserEmail(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            Paciente guardado = inv.getArgument(0);
            if (guardado.getId() == null) {
                guardado.setId(77);
            }
            return guardado;
        });
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

    @Test
    void crearUsaLaCuentaYNoElEmailNiElIdDelCuerpo() {
        entrar(20L, Role.MEDICO, "ana.martinez@turnero.com");

        PacienteDTO pedido = new PacienteDTO();
        pedido.setId(999);
        pedido.setEmail("ana.martinez@turnero.com");
        pedido.setNombre("Intrusa");
        pedido.setApellido("Ajena");
        pedido.setDni(44444441L);
        pedido.setTelefono("+5492804444441");

        AltaFichaPropia alta = service.crearPropio(pedido);

        assertFalse(alta.vinculada());
        assertEquals(77, alta.ficha().getId());
        assertEquals("ana.martinez@turnero.com", alta.ficha().getEmail());
        assertEquals("Ana", alta.ficha().getNombre());
        assertEquals(30111222L, alta.ficha().getDni());
        assertEquals("2990000000", alta.ficha().getTelefono());
        ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
        verify(repository).save(captor.capture());
        assertEquals("ana.martinez@turnero.com", captor.getValue().getEmail());
        assertEquals(30111222L, captor.getValue().getDni());
        verify(repository, never()).findById(any());
    }

    @Test
    void noCreaLaFichaDeOtraPersona() {
        entrar(20L, Role.OPERADOR, "ana.martinez@turnero.com");
        PacienteDTO pedido = new PacienteDTO();
        pedido.setId(4);
        pedido.setEmail("paciente1.santamaria@turnero.com");
        pedido.setNombre("María");
        pedido.setApellido("González");
        pedido.setDni(44444441L);
        pedido.setTelefono("+5492804444441");

        assertThrows(FichaAjenaException.class, () -> service.crearPropio(pedido));
        verify(repository, never()).save(any());
        verify(repository, never()).findById(any());
    }

    @Test
    void siYaExisteConElMismoEmailLaVinculaSinDuplicar() {
        entrar(20L, Role.MEDICO, "ana.martinez@turnero.com");
        Paciente propia = paciente(2558, "Ana.Martinez@turnero.com");
        when(repository.findByUserEmail("ana.martinez@turnero.com")).thenReturn(Optional.of(propia));

        PacienteDTO pedido = new PacienteDTO();
        pedido.setEmail("ana.martinez@turnero.com");
        pedido.setDni(99999999L);
        pedido.setNombre("Otra");

        AltaFichaPropia alta = service.crearPropio(pedido);

        assertTrue(alta.vinculada());
        assertEquals(2558, alta.ficha().getId());
        assertEquals("Ana.Martinez@turnero.com", alta.ficha().getEmail());
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaUnDniQueYaEsDeOtraFicha() {
        entrar(20L, Role.ADMINISTRADOR, "sin.datos@turnero.com");
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.setNombre("Laura");
        user.setApellido("Sosa");
        user.setDni(null);
        user.setTelefono("2991112222");
        when(repository.findByDni(44444441L)).thenReturn(Optional.of(paciente(2, "paciente1.santamaria@turnero.com")));

        PacienteDTO pedido = new PacienteDTO();
        pedido.setDni(44444441L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.crearPropio(pedido));
        assertTrue(error.getMessage().contains("44444441"));
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaUnTelefonoQueYaEsDeOtraFicha() {
        entrar(20L, Role.OPERADOR, "ana.martinez@turnero.com");
        when(repository.findByTelefono("2990000000")).thenReturn(Optional.of(paciente(9, "otro@turnero.com")));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.crearPropio(new PacienteDTO()));
        assertTrue(error.getMessage().contains("2990000000"));
        verify(repository, never()).save(any());
    }

    @Test
    void completaLoQueFaltaConElFormularioYConLaFichaDelMedico() {
        entrar(20L, Role.MEDICO, "dr@turnero.com");
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.setEmail("dr@turnero.com");
        user.setNombre(null);
        user.setApellido(" ");
        user.setDni(null);
        user.setTelefono(null);

        Medico medico = new Medico();
        medico.setNombre("Juan");
        medico.setApellido("Pérez");
        medico.setDni(33333333L);
        medico.setEmail("dr@turnero.com");
        medico.setTelefono(null);
        when(medicoRepository.findByEmailIgnoreCase("dr@turnero.com")).thenReturn(Optional.of(medico));

        DatosFichaIncompletosException incompleta = assertThrows(
                DatosFichaIncompletosException.class, () -> service.crearPropio(new PacienteDTO()));
        assertEquals(java.util.List.of("telefono"), incompleta.getCampos());
        verify(repository, never()).save(any());

        PacienteDTO pedido = new PacienteDTO();
        pedido.setTelefono("2804000000");
        pedido.setEmail("intruso@turnero.com");

        assertThrows(FichaAjenaException.class, () -> service.crearPropio(pedido));

        pedido.setEmail(null);
        AltaFichaPropia alta = service.crearPropio(pedido);
        assertEquals("Juan", alta.ficha().getNombre());
        assertEquals("Pérez", alta.ficha().getApellido());
        assertEquals(33333333L, alta.ficha().getDni());
        assertEquals("dr@turnero.com", alta.ficha().getEmail());
        assertEquals("2804000000", alta.ficha().getTelefono());
    }

    @Test
    void sinSesionNoCreaNada() {
        assertThrows(IllegalStateException.class, () -> service.crearPropio(new PacienteDTO()));
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
