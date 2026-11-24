package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;

class FeriadoServiceTest {

    private final ConfiguracionExcepcionalRepository repository = org.mockito.Mockito.mock(ConfiguracionExcepcionalRepository.class);
    private final CentroAtencionRepository centros = org.mockito.Mockito.mock(CentroAtencionRepository.class);
    private final TurnoRepository turnos = org.mockito.Mockito.mock(TurnoRepository.class);
    private final TurnoService turnoService = org.mockito.Mockito.mock(TurnoService.class);
    private final CentroAccessGuard centroAccess = org.mockito.Mockito.mock(CentroAccessGuard.class);
    private final AuditLogService auditoria = org.mockito.Mockito.mock(AuditLogService.class);
    private final FeriadoService service = new FeriadoService();

    private final LocalDate fecha = LocalDate.now(AusenciaMedicoService.ZONA).plusDays(10);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "centroAtencionRepository", centros);
        ReflectionTestUtils.setField(service, "turnoRepository", turnos);
        ReflectionTestUtils.setField(service, "turnoService", turnoService);
        ReflectionTestUtils.setField(service, "centroAccess", centroAccess);
        ReflectionTestUtils.setField(service, "auditLogService", auditoria);
        when(repository.save(any())).thenAnswer(invocation -> {
            ConfiguracionExcepcional guardada = invocation.getArgument(0);
            if (guardada.getId() == null) {
                guardada.setId(15);
            }
            return guardada;
        });
        when(repository.findByTipoAndFechaAndActivoTrue(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elOperadorCargaElFeriadoDeSuCentroYNoUnoGlobal() {
        autenticar(Role.OPERADOR, 2);
        when(centroAccess.centroParaFiltro(null)).thenReturn(2);
        when(centros.findById(2)).thenReturn(Optional.of(centro(2)));
        when(turnos.findByFecha(fecha)).thenReturn(List.of());

        var resultado = service.crear(fecha, "Día del centro", null);

        assertEquals(2, resultado.getFeriado().getCentroAtencionId());
        verify(auditoria).logGenericAction(eq("FERIADO"), eq(15L), eq("CREATE"), eq("operador@turnero.com"),
                any(), eq("ACTIVO"), any(), any(), any());

        doThrow(new AccessDeniedException("otro centro")).when(centroAccess).exigirCentro(1);
        assertThrows(AccessDeniedException.class, () -> service.crear(fecha, "Ajeno", 1));
    }

    @Test
    void elMedicoNoCargaFeriadosYElOperadorNoTocaUnoGlobal() {
        autenticar(Role.MEDICO, 2);
        assertThrows(AccessDeniedException.class, () -> service.crear(fecha, "Nada", 2));

        autenticar(Role.OPERADOR, 2);
        ConfiguracionExcepcional global = feriado(9, null);
        when(repository.findById(9)).thenReturn(Optional.of(global));
        assertThrows(AccessDeniedException.class, () -> service.eliminar(9));
        verify(repository, never()).save(global);
    }

    @Test
    void elSuperadminCargaUnFeriadoGlobal() {
        autenticar(Role.SUPERADMIN, null);
        when(turnos.findByFecha(fecha)).thenReturn(List.of());

        var resultado = service.crear(fecha, "Día de la independencia", null);

        assertNull(resultado.getFeriado().getCentroAtencionId());
    }

    @Test
    void cancelaLosTurnosDelAlcanceYNoOfreceLaListaDeEspera() {
        autenticar(Role.OPERADOR, 2);
        when(centros.findById(2)).thenReturn(Optional.of(centro(2)));
        Turno propio = turno(20, 2, EstadoTurno.CONFIRMADO);
        Turno reagendado = turno(21, 2, EstadoTurno.REAGENDADO);
        Turno programado = turno(22, 2, EstadoTurno.PROGRAMADO);
        Turno ajeno = turno(23, 1, EstadoTurno.CONFIRMADO);
        Turno cancelado = turno(24, 2, EstadoTurno.CANCELADO);
        when(turnos.findByFecha(fecha)).thenReturn(List.of(propio, reagendado, programado, ajeno, cancelado));

        var resultado = service.crear(fecha, "Feriado local", 2);

        assertEquals(3, resultado.getTurnosCancelados());
        verify(turnoService).cancelarTurno(eq(20), eq("Feriado: Feriado local"), eq("operador@turnero.com"),
                eq(false), eq("AUSENCIA"), eq(true));
        verify(turnoService).cancelarTurno(eq(21), eq("Feriado: Feriado local"), eq("operador@turnero.com"),
                eq(false), eq("AUSENCIA"), eq(true));
        verify(turnoService).cancelarTurno(eq(22), eq("Feriado: Feriado local"), eq("operador@turnero.com"),
                eq(false), eq("AUSENCIA"), eq(true));
        verify(turnoService, never()).cancelarTurno(eq(23), any(), any(), anyBoolean(), any(), anyBoolean());
        verify(turnoService, never()).cancelarTurno(eq(24), any(), any(), anyBoolean(), any(), anyBoolean());
    }

    @Test
    void laVistaPreviaCuentaSinCancelar() {
        autenticar(Role.ADMINISTRADOR, 2);
        when(turnos.findByFecha(fecha)).thenReturn(List.of(
                turno(1, 2, EstadoTurno.PROGRAMADO),
                turno(2, 1, EstadoTurno.PROGRAMADO)));

        assertEquals(1, service.contarAfectados(fecha, 2));
        verify(turnoService, never()).cancelarTurno(any(), any(), any(), anyBoolean(), any(), anyBoolean());
    }

    private static ConfiguracionExcepcional feriado(int id, Integer centroId) {
        ConfiguracionExcepcional feriado = new ConfiguracionExcepcional();
        feriado.setId(id);
        feriado.setTipo(ConfiguracionExcepcional.TipoExcepcion.FERIADO);
        feriado.setActivo(true);
        feriado.setFecha(LocalDate.now().plusDays(3));
        feriado.setDescripcion("Global");
        if (centroId != null) {
            feriado.setCentroAtencion(centro(centroId));
        }
        return feriado;
    }

    private static Turno turno(int id, int centroId, EstadoTurno estado) {
        Turno turno = new Turno();
        turno.setId(id);
        turno.setEstado(estado);
        turno.setCentroAtencion(centro(centroId));
        turno.setFecha(LocalDate.now().plusDays(10));
        turno.setHoraInicio(LocalTime.of(10, 0));
        return turno;
    }

    private static CentroAtencion centro(int id) {
        CentroAtencion centro = new CentroAtencion();
        centro.setId(id);
        centro.setNombre("Centro " + id);
        return centro;
    }

    private static void autenticar(Role rol, Integer centroId) {
        User user = new User();
        user.setEmail(rol == Role.SUPERADMIN ? "root@turnero.com" : rol.name().toLowerCase() + "@turnero.com");
        user.setRole(rol);
        user.setHashedPassword("x");
        if (centroId != null) {
            user.setCentroAtencion(centro(centroId));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
