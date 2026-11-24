package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.EspecialidadRepository;
import unpsjb.labprog.backend.business.repository.ListaEsperaRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.ListaEsperaDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.ListaEspera;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.Turno;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.SuscripcionAccessService;

class ListaEsperaColaTest {

    private final ListaEsperaRepository repository = mock(ListaEsperaRepository.class);
    private final PacienteRepository pacienteRepository = mock(PacienteRepository.class);
    private final EspecialidadRepository especialidadRepository = mock(EspecialidadRepository.class);
    private final CentroAtencionRepository centroAtencionRepository = mock(CentroAtencionRepository.class);
    private final MedicoRepository medicoRepository = mock(MedicoRepository.class);
    private final StaffMedicoRepository staffMedicoRepository = mock(StaffMedicoRepository.class);
    private final TurnoRepository turnoRepository = mock(TurnoRepository.class);
    private final CentroAccessGuard centroAccess = mock(CentroAccessGuard.class);
    private final SuscripcionAccessService suscripcion = mock(SuscripcionAccessService.class);
    private final AuditLogService auditoria = mock(AuditLogService.class);
    private final TurnoSlotGuard slotGuard = mock(TurnoSlotGuard.class);
    private final ListaEsperaService service = new ListaEsperaService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "pacienteRepository", pacienteRepository);
        ReflectionTestUtils.setField(service, "especialidadRepository", especialidadRepository);
        ReflectionTestUtils.setField(service, "centroAtencionRepository", centroAtencionRepository);
        ReflectionTestUtils.setField(service, "medicoRepository", medicoRepository);
        ReflectionTestUtils.setField(service, "staffMedicoRepository", staffMedicoRepository);
        ReflectionTestUtils.setField(service, "turnoRepository", turnoRepository);
        ReflectionTestUtils.setField(service, "centroAccess", centroAccess);
        ReflectionTestUtils.setField(service, "suscripcionAccessService", suscripcion);
        ReflectionTestUtils.setField(service, "auditLogService", auditoria);
        ReflectionTestUtils.setField(service, "slotGuard", slotGuard);
        when(centroAccess.filtrar(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            ListaEspera guardada = invocation.getArgument(0);
            if (guardada.getId() == null) {
                guardada.setId(40L);
            }
            return guardada;
        });
        when(pacienteRepository.findById(7)).thenReturn(Optional.of(paciente(7)));
        when(especialidadRepository.findById(1)).thenReturn(Optional.of(especialidad(1)));
        when(centroAtencionRepository.findById(1)).thenReturn(Optional.of(centro(1)));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elPacienteNoRetrocedeLaFechaNiSubeLaUrgencia() {
        autenticar(Role.PACIENTE, "ana@turnero.com");
        ListaEsperaDTO pedido = pedido();
        pedido.setUrgenciaMedica("URGENTE");
        pedido.setFechaSolicitud(LocalDateTime.of(2000, 1, 1, 0, 0));

        LocalDateTime antes = LocalDateTime.now(ReglasReservaTurno.ZONA).minusSeconds(2);
        ListaEsperaDTO creada = service.save(pedido);

        assertEquals("BAJA", creada.getUrgenciaMedica());
        assertTrue(creada.getFechaSolicitud().isAfter(antes));
        assertFalse(creada.getFechaSolicitud().getYear() == 2000);
        verify(auditoria, never()).logGenericAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void elOperadorFijaLaUrgenciaYQuedaAuditado() {
        autenticar(Role.OPERADOR, "operador@turnero.com");
        ListaEsperaDTO pedido = pedido();
        pedido.setUrgenciaMedica("ALTA");
        pedido.setFechaSolicitud(LocalDateTime.of(1999, 5, 5, 8, 0));

        ListaEsperaDTO creada = service.save(pedido);

        assertEquals("ALTA", creada.getUrgenciaMedica());
        assertTrue(creada.getFechaSolicitud().isAfter(LocalDateTime.of(2026, 1, 1, 0, 0)));
        verify(auditoria).logGenericAction(any(), any(), org.mockito.ArgumentMatchers.eq("URGENCIA_ASIGNADA"),
                org.mockito.ArgumentMatchers.eq("operador@turnero.com"), any(), org.mockito.ArgumentMatchers.eq("ALTA"),
                any(), any(), any());
    }

    @Test
    void elMedicoCambiaLaUrgenciaSoloDeSuPaciente() {
        autenticar(Role.MEDICO, "medico@turnero.com");
        Medico propio = new Medico();
        propio.setId(9);
        when(medicoRepository.findByEmailIgnoreCase("medico@turnero.com")).thenReturn(Optional.of(propio));
        when(medicoRepository.findById(9)).thenReturn(Optional.of(propio));
        StaffMedico staff = new StaffMedico();
        staff.setCentroAtencion(centro(1));
        when(staffMedicoRepository.findByMedicoId(9)).thenReturn(List.of(staff));

        ListaEsperaDTO suyo = pedido();
        suyo.setMedicoPreferidoId(9);
        suyo.setUrgenciaMedica("URGENTE");
        assertEquals("URGENTE", service.save(suyo).getUrgenciaMedica());

        ListaEsperaDTO ajeno = pedido();
        ajeno.setMedicoPreferidoId(3);
        ajeno.setUrgenciaMedica("URGENTE");
        Medico otro = new Medico();
        otro.setId(3);
        when(medicoRepository.findById(3)).thenReturn(Optional.of(otro));
        assertEquals("BAJA", service.save(ajeno).getUrgenciaMedica());
    }

    @Test
    void laReasignacionSalteaAlIncompatibleYTomaAlSiguiente() {
        ListaEspera otraEspecialidad = pendiente(1L, 2, 1, null, 0);
        ListaEspera conSolape = pendiente(2L, 1, 1, null, 0);
        ListaEspera compatible = pendiente(3L, 1, 1, null, 4);
        when(repository.findAll()).thenReturn(List.of(otraEspecialidad, conSolape, compatible));
        Turno ocupado = new Turno();
        ocupado.setId(90);
        ocupado.setFecha(LocalDate.now(ReglasReservaTurno.ZONA).plusDays(5));
        ocupado.setHoraInicio(LocalTime.of(10, 0));
        ocupado.setHoraFin(LocalTime.of(10, 30));
        ocupado.setEstado(EstadoTurno.CONFIRMADO);
        when(turnoRepository.findByPaciente_Id(2)).thenReturn(List.of(ocupado));
        when(turnoRepository.findByPaciente_Id(3)).thenReturn(List.of());
        when(slotGuard.guardar(any())).thenAnswer(invocation -> {
            Turno turno = invocation.getArgument(0);
            turno.setId(80);
            return turno;
        });

        assertTrue(service.ofrecerHuecoLiberado(hueco()));

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(slotGuard).guardar(captor.capture());
        assertEquals(3, captor.getValue().getPaciente().getId());
        assertEquals("CUBIERTA", compatible.getEstado());
        assertEquals("PENDIENTE", conSolape.getEstado());
    }

    @Test
    void unHuecoDentroDeDosHorasNoSeOfrece() {
        Turno proximo = hueco();
        proximo.setFecha(LocalDate.now(ReglasReservaTurno.ZONA));
        proximo.setHoraInicio(LocalTime.now(ReglasReservaTurno.ZONA).plusMinutes(30));
        proximo.setHoraFin(proximo.getHoraInicio().plusMinutes(30));

        assertFalse(service.ofrecerHuecoLiberado(proximo));
        verify(repository, never()).findAll();
    }

    @Test
    void aIgualUrgenciaYFechaGanaLaMejorReputacion() {
        LocalDateTime ingreso = LocalDateTime.of(2026, 10, 1, 9, 0);
        ListaEspera baja = pendiente(1L, 1, 1, ingreso, 1);
        ListaEspera alta = pendiente(2L, 1, 1, ingreso, 8);
        when(repository.findAll()).thenReturn(List.of(baja, alta));
        when(turnoRepository.findByPaciente_Id(anyInt())).thenReturn(List.of());
        when(slotGuard.guardar(any())).thenAnswer(invocation -> {
            Turno turno = invocation.getArgument(0);
            turno.setId(81);
            return turno;
        });

        assertTrue(service.buscarPacienteParaReasignar(hueco()));

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(slotGuard).guardar(captor.capture());
        assertEquals(2, captor.getValue().getPaciente().getId());
    }

    private static ListaEsperaDTO pedido() {
        ListaEsperaDTO dto = new ListaEsperaDTO();
        dto.setPacienteId(7);
        dto.setEspecialidadId(1);
        dto.setCentroAtencionId(1);
        dto.setEstado("PENDIENTE");
        return dto;
    }

    private static ListaEspera pendiente(long id, int especialidadId, Integer centroId, LocalDateTime ingreso,
            int reputacion) {
        ListaEspera solicitud = new ListaEspera();
        solicitud.setId(id);
        solicitud.setEstado("PENDIENTE");
        solicitud.setUrgenciaMedica(ListaEspera.UrgenciaMedica.BAJA);
        solicitud.setFechaSolicitud(ingreso == null ? LocalDateTime.of(2026, 10, 2, 9, 0) : ingreso);
        solicitud.setEspecialidad(especialidad(especialidadId));
        solicitud.setCentroAtencion(centro(centroId));
        Paciente paciente = paciente((int) id);
        paciente.setReputacion(reputacion);
        solicitud.setPaciente(paciente);
        return solicitud;
    }

    private static Turno hueco() {
        Turno turno = new Turno();
        turno.setId(15);
        turno.setFecha(LocalDate.now(ReglasReservaTurno.ZONA).plusDays(5));
        turno.setHoraInicio(LocalTime.of(10, 0));
        turno.setHoraFin(LocalTime.of(10, 30));
        turno.setEstado(EstadoTurno.CANCELADO);
        Especialidad especialidad = especialidad(1);
        CentroAtencion centro = centro(1);
        Medico medico = new Medico();
        medico.setId(4);
        medico.setNombre("Juan");
        medico.setApellido("Paz");
        StaffMedico staff = new StaffMedico();
        staff.setId(8);
        staff.setEspecialidad(especialidad);
        staff.setCentroAtencion(centro);
        staff.setMedico(medico);
        turno.setStaffMedico(staff);
        turno.setMedico(medico);
        turno.setEspecialidad(especialidad);
        turno.setCentroAtencion(centro);
        return turno;
    }

    private static Paciente paciente(int id) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        paciente.setNombre("Ana");
        paciente.setApellido("Test");
        paciente.setDni(30000000L + id);
        paciente.setEmail("ana@turnero.com");
        paciente.setTelefono("110000");
        return paciente;
    }

    private static Especialidad especialidad(int id) {
        Especialidad especialidad = new Especialidad();
        especialidad.setId(id);
        especialidad.setNombre("Clinica");
        return especialidad;
    }

    private static CentroAtencion centro(int id) {
        CentroAtencion centro = new CentroAtencion();
        centro.setId(id);
        centro.setNombre("Centro " + id);
        return centro;
    }

    private static void autenticar(Role rol, String email) {
        User user = new User();
        user.setEmail(email);
        user.setRole(rol);
        user.setHashedPassword("x");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
