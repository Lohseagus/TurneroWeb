package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.DisponibilidadMedicoRepository;
import unpsjb.labprog.backend.business.repository.EsquemaTurnoRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.dto.CalendarioMesDTO;
import unpsjb.labprog.backend.dto.TurnoCalendarioDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.Consultorio;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.User;

class CalendarioServiceTest {

    private final TurnoRepository turnos = org.mockito.Mockito.mock(TurnoRepository.class);
    private final ConfiguracionExcepcionalRepository feriados = org.mockito.Mockito.mock(ConfiguracionExcepcionalRepository.class);
    private final MedicoRepository medicos = org.mockito.Mockito.mock(MedicoRepository.class);
    private final StaffMedicoRepository staffRepo = org.mockito.Mockito.mock(StaffMedicoRepository.class);
    private final ConsultorioRepository consultorios = org.mockito.Mockito.mock(ConsultorioRepository.class);
    private final PacienteRepository pacientes = org.mockito.Mockito.mock(PacienteRepository.class);

    private final CalendarioService service = new CalendarioService(
            turnos,
            feriados,
            medicos,
            new CentroAccessGuard(
                    medicos,
                    staffRepo,
                    turnos,
                    consultorios,
                    org.mockito.Mockito.mock(DisponibilidadMedicoRepository.class),
                    org.mockito.Mockito.mock(EsquemaTurnoRepository.class),
                    new PacienteAccessGuard(pacientes)),
            new PacienteAccessGuard(pacientes));

    private final LocalDate desde = LocalDate.of(2026, 10, 26);
    private final LocalDate hasta = LocalDate.of(2026, 12, 6);

    @BeforeEach
    void setUp() {
        when(turnos.buscarParaCalendario(
                any(), any(), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class), nullable(Integer.class)))
                .thenReturn(List.of(turno(10, 2, 7)));
        when(feriados.feriadosEnRango(any(), any(), any())).thenReturn(List.of(
                feriado(1, null, "Global"),
                feriado(2, 2, "Del centro")));
        when(feriados.feriadosGlobalesEnRango(any(), any())).thenReturn(List.of(feriado(1, null, "Global")));
        when(feriados.feriadosTodosEnRango(any(), any())).thenReturn(List.of());
        when(turnos.findCentroIdsByPacienteId(any())).thenReturn(List.of(2));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elOperadorVeSuCentroEnElRangoYLosFeriadosQueLeAplican() {
        autenticar(Role.OPERADOR, 2);

        CalendarioMesDTO mes = service.consultar(desde, hasta, null, 9, 3, 4);

        assertEquals(desde, mes.getDesde());
        assertEquals(hasta, mes.getHasta());
        assertEquals(1, mes.getTurnos().size());
        assertEquals(2, mes.getFeriados().size());
        assertNull(mes.getFeriados().get(0).getCentroAtencionId());
        assertEquals(2, mes.getFeriados().get(1).getCentroAtencionId());
        assertEquals("Del centro", mes.getFeriados().get(1).getDescripcion());
        assertEquals("Centro 2", mes.getFeriados().get(1).getCentroAtencionNombre());
        verify(turnos).buscarParaCalendario(desde, hasta, 2, 9, 3, 4, null);
        verify(feriados).feriadosEnRango(eq(desde), eq(hasta), argThat(ids -> ids.size() == 1 && ids.contains(2)));
    }

    @Test
    void elOperadorNoPideElCalendarioDeOtroCentro() {
        autenticar(Role.OPERADOR, 2);

        assertThrows(AccessDeniedException.class, () -> service.consultar(desde, hasta, 1, null, null, null));
        verify(turnos, never()).buscarParaCalendario(
                any(), any(), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class), nullable(Integer.class));
    }

    @Test
    void unConsultorioDeOtroCentroTampocoPasa() {
        autenticar(Role.ADMINISTRADOR, 2);
        Consultorio ajeno = new Consultorio();
        ajeno.setId(8);
        ajeno.setCentroAtencion(centro(1));
        when(consultorios.findById(8)).thenReturn(Optional.of(ajeno));

        assertThrows(AccessDeniedException.class, () -> service.consultar(desde, hasta, 2, null, null, 8));
        verify(turnos, never()).buscarParaCalendario(
                any(), any(), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class), nullable(Integer.class));
    }

    @Test
    void elMedicoSoloVeSusTurnosYNoLosDeOtro() {
        autenticar(Role.MEDICO, 2);
        fichaMedico(7, 2);

        service.consultar(desde, hasta, null, null, 5, null);

        verify(turnos).buscarParaCalendario(eq(desde), eq(hasta), isNull(), eq(7), eq(5), isNull(), isNull());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Integer>> centros = ArgumentCaptor.forClass(Collection.class);
        verify(feriados).feriadosEnRango(eq(desde), eq(hasta), centros.capture());
        assertTrue(centros.getValue().contains(2));

        assertThrows(AccessDeniedException.class,
                () -> service.consultar(desde, hasta, null, 99, null, null));
    }

    @Test
    void elMedicoNoEntraAUnCentroAjeno() {
        autenticar(Role.MEDICO, 2);
        fichaMedico(7, 2);

        assertThrows(AccessDeniedException.class, () -> service.consultar(desde, hasta, 1, 7, null, null));
        verify(turnos, never()).buscarParaCalendario(
                any(), any(), eq(1), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class));
    }

    @Test
    void elPacienteVeSoloSusTurnos() {
        autenticar(Role.PACIENTE, null);
        Paciente ficha = new Paciente();
        ficha.setId(4);
        when(pacientes.findByUserEmail("paciente@turnero.com")).thenReturn(Optional.of(ficha));

        service.consultar(desde, hasta, 9, 3, 1, 2);

        verify(turnos).buscarParaCalendario(desde, hasta, 9, 3, 1, 2, 4);
        verify(feriados).feriadosGlobalesEnRango(desde, hasta);
        verify(turnos, never()).buscarParaCalendario(
                any(), any(), any(), any(), any(), any(), eq(99));
    }

    @Test
    void elPacienteSinFichaNoEntra() {
        autenticar(Role.PACIENTE, null);
        when(pacientes.findByUserEmail("paciente@turnero.com")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.consultar(desde, hasta, null, null, null, null));
        verify(turnos, never()).buscarParaCalendario(
                any(), any(), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class), nullable(Integer.class));
    }

    @Test
    void elSuperadminEligeCentroYSinCentroNoConsulta() {
        autenticar(Role.SUPERADMIN, null);

        service.consultar(desde, hasta, 3, null, null, null);
        verify(turnos).buscarParaCalendario(eq(desde), eq(hasta), eq(3), isNull(), isNull(), isNull(), isNull());
        verify(feriados).feriadosEnRango(eq(desde), eq(hasta), argThat(ids -> ids.size() == 1 && ids.contains(3)));

        assertThrows(IllegalArgumentException.class, () -> service.consultar(desde, hasta, null, null, null, null));
    }

    @Test
    void rechazaUnRangoMasLargoQueLaGrillaOInvertido() {
        autenticar(Role.OPERADOR, 2);

        assertThrows(IllegalArgumentException.class,
                () -> service.consultar(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 13), null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.consultar(hasta, desde, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.consultar(null, hasta, null, null, null, null));
        verify(turnos, never()).buscarParaCalendario(
                any(), any(), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class), nullable(Integer.class));
    }

    private void fichaMedico(int medicoId, int centroId) {
        Medico medico = new Medico();
        medico.setId(medicoId);
        medico.setEmail("medico@turnero.com");
        when(medicos.findByEmailIgnoreCase("medico@turnero.com")).thenReturn(Optional.of(medico));
        StaffMedico staff = new StaffMedico();
        staff.setId(70);
        staff.setMedico(medico);
        staff.setCentroAtencion(centro(centroId));
        when(staffRepo.findByMedicoId(medicoId)).thenReturn(List.of(staff));
    }

    private static TurnoCalendarioDTO turno(int id, int centroId, int medicoId) {
        return new TurnoCalendarioDTO(
                id, LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), LocalTime.of(9, 20),
                EstadoTurno.PROGRAMADO,
                1, "Ana", "Paz",
                medicoId, "Luis", "Gómez",
                70, 3, "Clínica",
                4, "1",
                centroId, "Centro " + centroId,
                null);
    }

    private static ConfiguracionExcepcional feriado(int id, Integer centroId, String descripcion) {
        ConfiguracionExcepcional feriado = new ConfiguracionExcepcional();
        feriado.setId(id);
        feriado.setActivo(true);
        feriado.setTipo(ConfiguracionExcepcional.TipoExcepcion.FERIADO);
        feriado.setFecha(LocalDate.of(2026, 11, 20));
        feriado.setDescripcion(descripcion);
        if (centroId != null) {
            feriado.setCentroAtencion(centro(centroId));
        }
        return feriado;
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
