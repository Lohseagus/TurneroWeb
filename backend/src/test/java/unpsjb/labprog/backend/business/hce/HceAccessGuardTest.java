package unpsjb.labprog.backend.business.hce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.PerfilClinicoRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.exception.MedicoSinFichaException;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.PerfilClinico;

@ExtendWith(MockitoExtension.class)
class HceAccessGuardTest {

    @Mock private MedicoRepository medicoRepository;
    @Mock private StaffMedicoRepository staffMedicoRepository;
    @Mock private PerfilClinicoRepository perfilRepository;
    @Mock private ConsultaRepository consultaRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private HceAuditoriaService auditoria;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operadorAdminYSuperadminNoLeenLaConsulta() {
        for (Role rol : List.of(Role.OPERADOR, Role.ADMINISTRADOR, Role.SUPERADMIN)) {
            autenticar(rol, "staff@turnero.com");
            HceAccessDeniedException error = assertThrows(HceAccessDeniedException.class,
                    () -> guard().assertLeeConsulta(consultaCentro(1)));
            assertEquals(403, error.getStatus());
        }
    }

    @Test
    void pacienteLeeLaPropiaYNoLaAjena() {
        autenticar(Role.PACIENTE, "paciente1.santamaria@turnero.com");
        guard().assertLeeConsulta(consultaCentro(1));

        HceAccessDeniedException error = assertThrows(HceAccessDeniedException.class,
                () -> guard().assertLeeConsulta(consultaDe("otra@turnero.com", 1)));
        assertEquals(403, error.getStatus());
    }

    @Test
    void medicoDelMismoCentroLeeYElDeOtroCentroNo() {
        autenticar(Role.MEDICO, "medico@turnero.com");
        when(medicoRepository.findByEmailIgnoreCase("medico@turnero.com")).thenReturn(Optional.of(medico()));
        when(staffMedicoRepository.existsByMedicoIdAndCentroId(7, 1)).thenReturn(true);
        guard().assertLeeConsulta(consultaCentro(1));

        when(staffMedicoRepository.existsByMedicoIdAndCentroId(7, 2)).thenReturn(false);
        when(perfilRepository.findByPaciente_Id(10)).thenReturn(Optional.empty());
        assertThrows(HceAccessDeniedException.class, () -> guard().assertLeeConsulta(consultaCentro(2)));
    }

    @Test
    void consentimientoHabilitaLaLecturaCruzada() {
        autenticar(Role.MEDICO, "medico@turnero.com");
        when(medicoRepository.findByEmailIgnoreCase("medico@turnero.com")).thenReturn(Optional.of(medico()));
        when(staffMedicoRepository.existsByMedicoIdAndCentroId(7, 2)).thenReturn(false);
        PerfilClinico perfil = new PerfilClinico();
        perfil.setComparteEntreCentros(true);
        when(perfilRepository.findByPaciente_Id(10)).thenReturn(Optional.of(perfil));

        guard().assertLeeConsulta(consultaCentro(2));
    }

    @Test
    void medicoSinFichaNoEntraALaHistoria() {
        autenticar(Role.MEDICO, "sin.ficha@turnero.com");
        when(medicoRepository.findByEmailIgnoreCase("sin.ficha@turnero.com")).thenReturn(Optional.empty());
        HceNotFoundException error = assertThrows(HceNotFoundException.class,
                () -> guard().exigirMedico(guard().usuarioActual()));
        assertEquals(MedicoSinFichaException.MENSAJE, error.getMessage());
    }

    @Test
    void anonimoRecibe401() {
        SecurityContextHolder.clearContext();
        HceAccessDeniedException error = assertThrows(HceAccessDeniedException.class,
                () -> guard().assertLeeConsulta(consultaCentro(1)));
        assertEquals(401, error.getStatus());
    }

    private HceAccessGuard guard() {
        return new HceAccessGuard(medicoRepository, staffMedicoRepository, perfilRepository, consultaRepository,
                turnoRepository, auditoria);
    }

    private void autenticar(Role rol, String email) {
        User user = new User("Nombre", "Apellido", 1L, email, "x", "1234567", rol);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Consulta consultaCentro(int centroId) {
        return consultaDe("paciente1.santamaria@turnero.com", centroId);
    }

    private Consulta consultaDe(String emailPaciente, int centroId) {
        Paciente paciente = new Paciente();
        paciente.setId(10);
        paciente.setEmail(emailPaciente);
        Medico autor = new Medico();
        autor.setId(99);
        autor.setNombre("Otra");
        autor.setApellido("Autora");
        CentroAtencion centro = new CentroAtencion();
        centro.setId(centroId);
        centro.setNombre("Centro " + centroId);
        Consulta consulta = new Consulta();
        consulta.setId(1L);
        consulta.setPaciente(paciente);
        consulta.setMedico(autor);
        consulta.setCentroAtencion(centro);
        return consulta;
    }

    private Medico medico() {
        Medico medico = new Medico();
        medico.setId(7);
        medico.setEmail("medico@turnero.com");
        medico.setNombre("Juan");
        medico.setApellido("Pérez");
        return medico;
    }
}
