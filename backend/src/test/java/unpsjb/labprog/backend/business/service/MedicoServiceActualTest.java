package unpsjb.labprog.backend.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.dto.MedicoActualDTO;
import unpsjb.labprog.backend.exception.MedicoSinFichaException;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.StaffMedico;
import unpsjb.labprog.backend.model.User;

@ExtendWith(MockitoExtension.class)
class MedicoServiceActualTest {

    @Mock
    private MedicoRepository repository;

    @Mock
    private StaffMedicoRepository staffMedicoRepository;

    @InjectMocks
    private MedicoService service;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resuelveLaFichaPorElEmailDelPrincipalAunqueElClienteNoTengaId() {
        autenticar(Role.MEDICO, "  Medico@Turnero.com  ");
        Medico medico = new Medico();
        medico.setId(7);
        medico.setNombre("Juan");
        medico.setApellido("Pérez");
        medico.setEmail("medico@turnero.com");
        medico.setMatricula("MP-1");
        when(repository.findByEmailIgnoreCase("Medico@Turnero.com")).thenReturn(Optional.of(medico));

        StaffMedico staff = new StaffMedico();
        staff.setId(15);
        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setNombre("Clínica Santa María");
        staff.setCentroAtencion(centro);
        Especialidad especialidad = new Especialidad();
        especialidad.setId(3);
        especialidad.setNombre("Clínica");
        staff.setEspecialidad(especialidad);
        when(staffMedicoRepository.findByMedicoId(7)).thenReturn(List.of(staff));

        MedicoActualDTO dto = service.actual().orElseThrow();

        assertEquals(7, dto.getId());
        assertEquals(15, dto.getStaffMedicoId());
        assertEquals("Clínica Santa María", dto.getStaff().get(0).getCentroNombre());
        verify(repository).findByEmailIgnoreCase("Medico@Turnero.com");
    }

    @Test
    void medicoSinFichaSeRegistraYNoSeConfundeConOtroRol() {
        Logger logger = (Logger) LoggerFactory.getLogger(MedicoService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            autenticar(Role.MEDICO, "sin.ficha@turnero.com");
            when(repository.findByEmailIgnoreCase("sin.ficha@turnero.com")).thenReturn(Optional.empty());

            MedicoSinFichaException error = assertThrows(MedicoSinFichaException.class, () -> service.actual());
            assertEquals(MedicoSinFichaException.MENSAJE, error.getMessage());
            assertTrue(appender.list.stream().anyMatch(evento ->
                    evento.getLevel() == Level.WARN
                            && evento.getFormattedMessage().contains("sin.ficha@turnero.com")
                            && evento.getFormattedMessage().contains("no tiene una ficha")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void otroRolSinFichaNoDisparaLaAlertaDeMedico() {
        autenticar(Role.PACIENTE, "paciente@turnero.com");
        when(repository.findByEmailIgnoreCase("paciente@turnero.com")).thenReturn(Optional.empty());
        assertTrue(service.actual().isEmpty());
    }

    private void autenticar(Role rol, String email) {
        User user = new User("Nombre", "Apellido", 1L, email, "x", "1234567", rol);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
