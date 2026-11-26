package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.repository.AuditLogRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.model.AuditLog;
import unpsjb.labprog.backend.model.AuditSource;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

@ExtendWith(MockitoExtension.class)
class AuditLogConsultaTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private ConsultorioRepository consultorioRepository;
    @Mock private StaffMedicoRepository staffMedicoRepository;
    @Mock private MedicoRepository medicoRepository;
    @Mock private DisponibilidadMedicoRepository disponibilidadRepository;
    @Mock private EsquemaTurnoRepository esquemaTurnoRepository;
    @Mock private PacienteRepository pacienteRepository;

    private AuditLogService service;

    @BeforeEach
    void armar() {
        CentroAccessGuard guard = new CentroAccessGuard(medicoRepository, staffMedicoRepository, turnoRepository,
                consultorioRepository, disponibilidadRepository, esquemaTurnoRepository,
                new PacienteAccessGuard(pacienteRepository));
        service = new AuditLogService();
        ReflectionTestUtils.setField(service, "auditLogRepository", auditLogRepository);
        ReflectionTestUtils.setField(service, "centroAccess", guard);
        ReflectionTestUtils.setField(service, "turnoRepository", turnoRepository);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filtraPorRolActorEntidadAccionOrigenYFechasYTraeLoMasNuevoPrimero() {
        entrar(Role.ADMINISTRADOR, 1);
        when(auditLogRepository.buscar(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.findByFilters(" TURNO ", " ana ", "CANCEL", "operador", "sistema",
                null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                0, 20, "performedAt", "DESC");

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).buscar(
                eq("TURNO"), eq("ana"), eq("CANCEL"), eq("operador"), eq("SISTEMA"),
                eq(LocalDateTime.of(2026, 3, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 3, 31, 23, 59, 59, 999999999)),
                eq(1), pagina.capture());
        Sort.Order orden = pagina.getValue().getSort().getOrderFor("performedAt");
        assertEquals(Sort.Direction.DESC, orden.getDirection());
    }

    @Test
    void unAdminDeOtroCentroRecibe403YNoVeEsasFilas() {
        entrar(Role.ADMINISTRADOR, 1);
        AuditLog propio = fila(1);
        AuditLog ajeno = fila(2);
        when(auditLogRepository.findByActionOrderByPerformedAtDesc("CANCEL"))
                .thenReturn(List.of(propio, ajeno));

        assertThrows(AccessDeniedException.class, () -> service.findByFilters(
                null, null, null, null, null, 2, null, null, 0, 20, "performedAt", "DESC"));
        verify(auditLogRepository, never()).buscar(any(), any(), any(), any(), any(), any(), any(), any(), any());

        List<AuditLog> visibles = service.getLogsByAction("CANCEL");
        assertEquals(1, visibles.size());
        assertEquals(1, visibles.get(0).getCentroAtencionId());
    }

    @Test
    void elSuperadminNoFiltraPorCentro() {
        entrar(Role.SUPERADMIN, null);
        Object[] deOtroCentro = new Object[] {8, null, "TURNO", 4L, "CANCEL",
                LocalDateTime.of(2026, 3, 2, 10, 0), "ana@turnero.com", null, null, null,
                AuditSource.USUARIO, "OPERADOR", 2};
        java.util.List<Object[]> filas = new java.util.ArrayList<>();
        filas.add(deOtroCentro);
        when(auditLogRepository.buscar(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(filas));

        var pagina = service.findByFilters(null, null, null, null, null, null,
                null, null, 0, 20, "performedAt", "DESC");

        assertEquals(1, pagina.getTotalElements());
        assertEquals(2, pagina.getContent().get(0).getCentroAtencionId());
        verify(auditLogRepository).buscar(isNull(), isNull(), isNull(), isNull(), isNull(),
                any(), any(), isNull(), any());
    }

    private static AuditLog fila(int centro) {
        AuditLog log = new AuditLog();
        log.setId(centro);
        log.setEntityType("TURNO");
        log.setAction("CANCEL");
        log.setPerformedAt(LocalDateTime.of(2026, 3, centro, 10, 0));
        log.setPerformedBy("ana@turnero.com");
        log.setCentroAtencionId(centro);
        return log;
    }

    private static void entrar(Role rol, Integer centroId) {
        User user = new User();
        user.setId(1L);
        user.setEmail(rol == Role.SUPERADMIN ? "super@turnero.com" : "admin@turnero.com");
        user.setRole(rol);
        user.setHashedPassword("hash");
        if (centroId != null) {
            CentroAtencion centro = new CentroAtencion();
            centro.setId(centroId);
            user.setCentroAtencion(centro);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
