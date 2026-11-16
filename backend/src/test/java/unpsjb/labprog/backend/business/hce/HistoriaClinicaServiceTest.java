package unpsjb.labprog.backend.business.hce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import unpsjb.labprog.backend.business.repository.AccesoHistoriaClinicaRepository;
import unpsjb.labprog.backend.business.repository.AdjuntoClinicoRepository;
import unpsjb.labprog.backend.business.repository.AlergiaRepository;
import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.CodigoCie10Repository;
import unpsjb.labprog.backend.business.repository.ConsultaRepository;
import unpsjb.labprog.backend.business.repository.DiagnosticoConsultaRepository;
import unpsjb.labprog.backend.business.repository.EnmiendaRepository;
import unpsjb.labprog.backend.business.repository.EspecialidadRepository;
import unpsjb.labprog.backend.business.repository.MedicacionHabitualRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.business.repository.PerfilClinicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.business.repository.VacunaRepository;
import unpsjb.labprog.backend.dto.hce.ConsultaWriteDTO;
import unpsjb.labprog.backend.dto.hce.EnmiendaDTO;
import unpsjb.labprog.backend.dto.hce.EnmiendaWriteDTO;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.Consulta;
import unpsjb.labprog.backend.model.hce.Enmienda;
import unpsjb.labprog.backend.model.hce.EstadoConsulta;

@ExtendWith(MockitoExtension.class)
class HistoriaClinicaServiceTest {

    @Mock private HceAccessGuard guard;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private ConsultaRepository consultaRepository;
    @Mock private DiagnosticoConsultaRepository diagnosticoRepository;
    @Mock private EnmiendaRepository enmiendaRepository;
    @Mock private PerfilClinicoRepository perfilRepository;
    @Mock private AlergiaRepository alergiaRepository;
    @Mock private MedicacionHabitualRepository medicacionRepository;
    @Mock private VacunaRepository vacunaRepository;
    @Mock private CodigoCie10Repository cie10Repository;
    @Mock private CentroAtencionRepository centroRepository;
    @Mock private EspecialidadRepository especialidadRepository;
    @Mock private AccesoHistoriaClinicaRepository accesoRepository;
    @Mock private AdjuntoClinicoRepository adjuntoRepository;

    @InjectMocks
    private HistoriaClinicaService service;

    @Test
    void consultaCerradaNoSeReescribe() {
        Consulta consulta = consulta(1L, EstadoConsulta.CERRADA);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        User user = usuarioMedico();
        when(guard.usuarioActual()).thenReturn(user);
        when(guard.exigirMedico(user)).thenReturn(consulta.getMedico());

        ConsultaWriteDTO dto = new ConsultaWriteDTO();
        dto.setMotivoConsulta("texto alterado");

        assertThrows(HceImmutableException.class, () -> service.actualizar(1L, dto));
        verify(consultaRepository, never()).save(any());
    }

    @Test
    void borradorNoAdmiteEnmienda() {
        Consulta consulta = consulta(2L, EstadoConsulta.BORRADOR);
        when(consultaRepository.findById(2L)).thenReturn(Optional.of(consulta));

        EnmiendaWriteDTO dto = new EnmiendaWriteDTO();
        dto.setTexto("aclaración");
        dto.setMotivo("dato incompleto");

        assertThrows(IllegalStateException.class, () -> service.enmendar(2L, dto));
        verify(enmiendaRepository, never()).save(any());
    }

    @Test
    void enmiendaDeConsultaCerradaQuedaAppendOnly() {
        Consulta consulta = consulta(3L, EstadoConsulta.CERRADA);
        when(consultaRepository.findById(3L)).thenReturn(Optional.of(consulta));
        when(guard.usuarioActual()).thenReturn(usuarioMedico());
        when(guard.exigirMedico(any())).thenReturn(consulta.getMedico());
        when(enmiendaRepository.save(any())).thenAnswer(invocation -> {
            Enmienda enmienda = invocation.getArgument(0);
            enmienda.setId(9L);
            return enmienda;
        });

        EnmiendaWriteDTO dto = new EnmiendaWriteDTO();
        dto.setTexto("La dosis correcta es 50 mg");
        dto.setMotivo("Error de transcripción");

        EnmiendaDTO resultado = service.enmendar(3L, dto);

        assertEquals("Error de transcripción", resultado.getMotivo());
        assertEquals("La dosis correcta es 50 mg", resultado.getTexto());
        assertEquals(consulta.getMedico().getId(), resultado.getMedicoId());
        verify(consultaRepository, never()).save(any());
    }

    @Test
    void elBorradorCalculaElImc() {
        Consulta consulta = consulta(4L, EstadoConsulta.BORRADOR);
        when(consultaRepository.findById(4L)).thenReturn(Optional.of(consulta));
        User user = usuarioMedico();
        when(guard.usuarioActual()).thenReturn(user);
        when(guard.exigirMedico(user)).thenReturn(consulta.getMedico());
        when(consultaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConsultaWriteDTO dto = new ConsultaWriteDTO();
        dto.setMotivoConsulta("Control");
        dto.setPesoKg(70.0);
        dto.setTallaCm(170.0);

        assertEquals(24.22, service.actualizar(4L, dto).getImc(), 0.001);
    }

    private Consulta consulta(Long id, EstadoConsulta estado) {
        Paciente paciente = new Paciente();
        paciente.setId(10);
        paciente.setNombre("María");
        paciente.setApellido("González");
        paciente.setDni(44444441L);
        paciente.setEmail("paciente1.santamaria@turnero.com");

        Medico medico = new Medico();
        medico.setId(1);
        medico.setNombre("Dr. Juan");
        medico.setApellido("Pérez");
        medico.setMatricula("MP-12345");
        medico.setEmail("medico@turnero.com");

        CentroAtencion centro = new CentroAtencion();
        centro.setId(1);
        centro.setNombre("Clínica Santa María");

        Consulta consulta = new Consulta();
        consulta.setId(id);
        consulta.setPaciente(paciente);
        consulta.setMedico(medico);
        consulta.setCentroAtencion(centro);
        consulta.setEstado(estado);
        consulta.setCreadaEn(LocalDateTime.now());
        consulta.setMotivoConsulta("Cefalea");
        return consulta;
    }

    private User usuarioMedico() {
        return new User("Dr. Juan", "Pérez", 33333333L, "medico@turnero.com", "x", "+5492804333333", Role.MEDICO);
    }
}
