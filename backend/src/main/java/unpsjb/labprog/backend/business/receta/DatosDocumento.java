package unpsjb.labprog.backend.business.receta;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import unpsjb.labprog.backend.business.hce.HceAccessDeniedException;
import unpsjb.labprog.backend.business.hce.HceAccessGuard;
import unpsjb.labprog.backend.business.hce.HceImmutableException;
import unpsjb.labprog.backend.business.repository.DiagnosticoConsultaRepository;
import unpsjb.labprog.backend.business.repository.PacienteRepository;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.model.hce.DiagnosticoConsulta;
import unpsjb.labprog.backend.model.hce.DocumentoAsistencial;
import unpsjb.labprog.backend.model.hce.EstadoPrescripcion;

@Component
public class DatosDocumento {

    private final HceAccessGuard guard;
    private final PacienteRepository pacienteRepository;
    private final DiagnosticoConsultaRepository diagnosticoRepository;
    private final RecetaProperties properties;

    public DatosDocumento(HceAccessGuard guard, PacienteRepository pacienteRepository,
            DiagnosticoConsultaRepository diagnosticoRepository, RecetaProperties properties) {
        this.guard = guard;
        this.pacienteRepository = pacienteRepository;
        this.diagnosticoRepository = diagnosticoRepository;
        this.properties = properties;
    }

    public void exigirMedico(String mensaje) {
        User user = guard.usuarioActual();
        if (user.getRole() != Role.MEDICO) {
            throw new HceAccessDeniedException(mensaje);
        }
    }

    public Medico medicoActual() {
        exigirMedico("Solo un médico puede hacer esta operación");
        return guard.exigirMedico(guard.usuarioActual());
    }

    public void exigirAutor(DocumentoAsistencial documento) {
        Medico medico = medicoActual();
        if (documento.getMedico() == null || !medico.getId().equals(documento.getMedico().getId())) {
            throw new HceAccessDeniedException("Solo el profesional que la creó puede emitirla o anularla");
        }
    }

    public void assertBorrador(EstadoPrescripcion estado) {
        if (estado != EstadoPrescripcion.BORRADOR) {
            throw new HceImmutableException("El documento emitido no se edita. Si hace falta, se anula con motivo.");
        }
    }

    public void diagnostico(DocumentoAsistencial documento, String code, boolean obligatorio) {
        if (code == null || code.isBlank()) {
            if (obligatorio) {
                throw new IllegalArgumentException("Hace falta un diagnóstico CIE-10 de la consulta");
            }
            documento.setDiagnosticoCode(null);
            documento.setDiagnosticoDisplay(null);
            documento.setDiagnosticoSystem(null);
            return;
        }
        String buscado = code.trim();
        DiagnosticoConsulta diagnostico = diagnosticoRepository
                .findByConsulta_IdOrderByIdAsc(documento.getConsulta().getId()).stream()
                .filter(item -> buscado.equalsIgnoreCase(item.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El diagnóstico tiene que ser uno de la consulta"));
        documento.setDiagnosticoCode(diagnostico.getCode());
        documento.setDiagnosticoDisplay(diagnostico.getDisplay());
        documento.setDiagnosticoSystem(diagnostico.getSystemUri());
    }

    public void congelar(DocumentoAsistencial documento, String sexo, LocalDate nacimiento, String afiliado,
            String obraSocial) {
        Paciente paciente = documento.getPaciente();
        if (sexo != null && !sexo.isBlank()) {
            String normal = sexo.trim().toUpperCase();
            if (!normal.equals("F") && !normal.equals("M") && !normal.equals("X")) {
                throw new IllegalArgumentException("El sexo tiene que ser F, M o X");
            }
            paciente.setSexo(normal);
        }
        if (nacimiento != null) {
            paciente.setFechaNacimiento(FechasPersona.aDate(nacimiento));
        }
        if (afiliado != null && !afiliado.isBlank()) {
            paciente.setNumeroAfiliado(afiliado.trim());
        }
        pacienteRepository.save(paciente);
        documento.setPacienteNombre(paciente.getNombre());
        documento.setPacienteApellido(paciente.getApellido());
        documento.setPacienteDni(paciente.getDni());
        documento.setPacienteSexo(paciente.getSexo());
        documento.setPacienteNacimiento(FechasPersona.aLocal(paciente.getFechaNacimiento()));
        if (paciente.getObraSocial() != null && paciente.getObraSocial().getNombre() != null) {
            documento.setObraSocialNombre(paciente.getObraSocial().getNombre());
        } else if (obraSocial != null && !obraSocial.isBlank()) {
            documento.setObraSocialNombre(obraSocial.trim());
        }
        documento.setNumeroAfiliado(paciente.getNumeroAfiliado());
        Medico medico = documento.getMedico();
        documento.setPrescriptorNombre(medico.getNombre());
        documento.setPrescriptorApellido(medico.getApellido());
        documento.setMatricula(medico.getMatricula());
        documento.setMatriculaNacional(medico.getMatriculaNacional());
        documento.setMatriculaProvincial(medico.getMatriculaProvincial());
        documento.setRefepsId(medico.getRefepsId());
        if (documento.getConsulta().getEspecialidad() != null) {
            documento.setEspecialidadNombre(documento.getConsulta().getEspecialidad().getNombre());
        }
        if (documento.getCentroAtencion() != null) {
            documento.setCentroNombre(documento.getCentroAtencion().getNombre());
        }
    }

    public void exigirDatosPaciente(DocumentoAsistencial documento) {
        if (documento.getPacienteDni() == null) {
            throw new IllegalArgumentException("La receta necesita el DNI del paciente");
        }
        if (documento.getPacienteSexo() == null || documento.getPacienteSexo().isBlank()) {
            throw new IllegalArgumentException("La receta necesita el sexo del paciente (F, M o X)");
        }
        if (documento.getPacienteNacimiento() == null) {
            throw new IllegalArgumentException("La receta necesita la fecha de nacimiento del paciente");
        }
    }

    public LocalDate vencimiento(LocalDate pedido, LocalDate emision) {
        int dias = properties.getVigenciaDias() > 0 ? properties.getVigenciaDias() : 30;
        LocalDate vto = pedido != null ? pedido : emision.plusDays(dias);
        if (!vto.isAfter(emision)) {
            throw new IllegalArgumentException("El vencimiento tiene que ser posterior a la emisión");
        }
        return vto;
    }

    public boolean vencerSiCorresponde(DocumentoAsistencial documento) {
        if (documento.getFechaVencimiento() == null) {
            return false;
        }
        EstadoPrescripcion estado = documento.getEstado();
        if (estado != EstadoPrescripcion.EMITIDA && estado != EstadoPrescripcion.DISPENSADA_PARCIAL) {
            return false;
        }
        if (!documento.getFechaVencimiento().isBefore(LocalDate.now())) {
            return false;
        }
        documento.setEstado(EstadoPrescripcion.VENCIDA);
        documento.setActualizadaEn(LocalDateTime.now());
        return true;
    }

    public String url(String camino, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String base = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().replaceAll("/+$", "");
        return base + camino + codigo;
    }

    public boolean integridad(String hash, String canonico) {
        return hash != null && canonico != null && hash.equalsIgnoreCase(CodigosDocumento.sha256(canonico));
    }

    public String iniciales(String nombre, String apellido) {
        String n = inicial(nombre);
        String a = inicial(apellido);
        if (n.isEmpty() && a.isEmpty()) {
            return "—";
        }
        if (n.isEmpty()) {
            return a + ".";
        }
        if (a.isEmpty()) {
            return n + ".";
        }
        return n + ". " + a + ".";
    }

    public String dniParcial(Long dni) {
        if (dni == null) {
            return "—";
        }
        String texto = String.valueOf(dni);
        if (texto.length() <= 3) {
            return texto;
        }
        return "*".repeat(texto.length() - 3) + texto.substring(texto.length() - 3);
    }

    public String matriculaPrincipal(DocumentoAsistencial documento) {
        if (documento.getMatriculaNacional() != null && !documento.getMatriculaNacional().isBlank()) {
            return documento.getMatriculaNacional();
        }
        return documento.getMatricula() == null ? "" : documento.getMatricula();
    }

    public int tope(int configurado) {
        return configurado > 0 ? configurado : 1;
    }

    public String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private String inicial(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return valor.trim().substring(0, 1).toUpperCase();
    }
}
