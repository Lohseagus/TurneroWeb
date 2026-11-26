package unpsjb.labprog.backend.business.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.ConfiguracionExcepcionalRepository;
import unpsjb.labprog.backend.business.repository.MedicoRepository;
import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.dto.CalendarioMesDTO;
import unpsjb.labprog.backend.dto.FeriadoCalendarioDTO;
import unpsjb.labprog.backend.dto.TurnoCalendarioDTO;
import unpsjb.labprog.backend.model.ConfiguracionExcepcional;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.User;

/**
 * Turnos y feriados de la grilla mensual.
 *
 * El alcance es el de siempre: el operador y el administrador ven su centro,
 * el médico solo los suyos, el paciente solo los propios y el superadmin elige
 * un centro. Pedir otro centro o los turnos de otro médico responde 403.
 */
@Service
public class CalendarioService {

    /** La grilla lunes-domingo de un mes cabe en 42 días. */
    static final int MAX_DIAS = 42;

    private final TurnoRepository turnoRepository;
    private final ConfiguracionExcepcionalRepository feriadoRepository;
    private final MedicoRepository medicoRepository;
    private final CentroAccessGuard centroAccess;
    private final PacienteAccessGuard pacienteAccess;

    public CalendarioService(TurnoRepository turnoRepository,
            ConfiguracionExcepcionalRepository feriadoRepository,
            MedicoRepository medicoRepository,
            CentroAccessGuard centroAccess,
            PacienteAccessGuard pacienteAccess) {
        this.turnoRepository = turnoRepository;
        this.feriadoRepository = feriadoRepository;
        this.medicoRepository = medicoRepository;
        this.centroAccess = centroAccess;
        this.pacienteAccess = pacienteAccess;
    }

    @Transactional(readOnly = true)
    public CalendarioMesDTO consultar(LocalDate desde, LocalDate hasta, Integer centroId,
            Integer medicoId, Integer especialidadId, Integer consultorioId) {
        validarRango(desde, hasta);

        User user = AuditContext.getCurrentUserObject();
        Alcance alcance = resolver(user, centroId, medicoId);
        if (consultorioId != null && user != null && user.getRole() != Role.PACIENTE) {
            centroAccess.exigirConsultorio(consultorioId);
        }

        List<TurnoCalendarioDTO> turnos = turnoRepository.buscarParaCalendario(
                desde, hasta, alcance.centroId, alcance.medicoId, especialidadId, consultorioId, alcance.pacienteId);

        if (alcance.sumarCentrosDeLosTurnos) {
            for (TurnoCalendarioDTO turno : turnos) {
                if (turno.getCentroId() != null) {
                    alcance.centrosFeriado.add(turno.getCentroId());
                }
            }
        }

        return new CalendarioMesDTO(desde, hasta, turnos, feriados(desde, hasta, alcance));
    }

    private Alcance resolver(User user, Integer centroPedido, Integer medicoPedido) {
        Alcance alcance = new Alcance();
        if (user == null || user.getRole() == null) {
            alcance.centroId = centroPedido;
            alcance.medicoId = medicoPedido;
            alcance.feriadosSinFiltro = centroPedido == null;
            if (centroPedido != null) {
                alcance.centrosFeriado.add(centroPedido);
            }
            return alcance;
        }
        switch (user.getRole()) {
            case PACIENTE -> alcancePaciente(centroPedido, medicoPedido, alcance);
            case MEDICO -> alcanceMedico(user, centroPedido, medicoPedido, alcance);
            case OPERADOR, ADMINISTRADOR -> alcanceCentro(centroPedido, medicoPedido, alcance);
            case SUPERADMIN -> alcanceSuperadmin(centroPedido, medicoPedido, alcance);
            default -> throw new AccessDeniedException(CentroAccessGuard.MENSAJE);
        }
        return alcance;
    }

    private void alcancePaciente(Integer centroPedido, Integer medicoPedido, Alcance alcance) {
        Integer ficha = pacienteAccess.fichaPropia().orElseThrow(() -> new AccessDeniedException(
                "Tu cuenta no tiene una ficha de paciente vinculada"));
        alcance.pacienteId = ficha;
        alcance.medicoId = medicoPedido;
        alcance.centroId = centroPedido;
        List<Integer> suyos = turnoRepository.findCentroIdsByPacienteId(ficha);
        if (suyos == null) {
            suyos = List.of();
        }
        if (centroPedido == null) {
            alcance.centrosFeriado.addAll(suyos);
        } else if (suyos.contains(centroPedido)) {
            alcance.centrosFeriado.add(centroPedido);
        }
    }

    private void alcanceMedico(User user, Integer centroPedido, Integer medicoPedido, Alcance alcance) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new AccessDeniedException("No tenés una ficha de médico vinculada");
        }
        Medico ficha = medicoRepository.findByEmailIgnoreCase(user.getEmail().trim())
                .orElseThrow(() -> new AccessDeniedException("No tenés una ficha de médico vinculada"));
        if (medicoPedido != null && !medicoPedido.equals(ficha.getId())) {
            throw new AccessDeniedException(CentroAccessGuard.MENSAJE);
        }
        alcance.medicoId = ficha.getId();
        if (centroPedido != null) {
            centroAccess.exigirCentro(centroPedido);
            alcance.centroId = centroPedido;
            alcance.centrosFeriado.add(centroPedido);
        } else {
            Set<Integer> permitidos = centroAccess.centrosPermitidos();
            if (permitidos != null) {
                alcance.centrosFeriado.addAll(permitidos);
            }
            alcance.sumarCentrosDeLosTurnos = true;
        }
    }

    private void alcanceCentro(Integer centroPedido, Integer medicoPedido, Alcance alcance) {
        Integer centro = centroAccess.centroParaFiltro(centroPedido);
        alcance.centroId = centro;
        alcance.medicoId = medicoPedido;
        if (centro != null) {
            alcance.centrosFeriado.add(centro);
        }
    }

    private void alcanceSuperadmin(Integer centroPedido, Integer medicoPedido, Alcance alcance) {
        if (centroPedido == null) {
            throw new IllegalArgumentException("Elegí un centro");
        }
        alcance.centroId = centroPedido;
        alcance.medicoId = medicoPedido;
        alcance.centrosFeriado.add(centroPedido);
    }

    private List<FeriadoCalendarioDTO> feriados(LocalDate desde, LocalDate hasta, Alcance alcance) {
        List<ConfiguracionExcepcional> filas;
        if (alcance.feriadosSinFiltro) {
            filas = feriadoRepository.feriadosTodosEnRango(desde, hasta);
        } else if (alcance.centrosFeriado.isEmpty()) {
            filas = feriadoRepository.feriadosGlobalesEnRango(desde, hasta);
        } else {
            filas = feriadoRepository.feriadosEnRango(desde, hasta, alcance.centrosFeriado);
        }
        List<FeriadoCalendarioDTO> dtos = new ArrayList<>(filas.size());
        for (ConfiguracionExcepcional feriado : filas) {
            dtos.add(aFeriado(feriado));
        }
        return dtos;
    }

    private static FeriadoCalendarioDTO aFeriado(ConfiguracionExcepcional feriado) {
        FeriadoCalendarioDTO dto = new FeriadoCalendarioDTO();
        dto.setId(feriado.getId());
        dto.setFecha(feriado.getFecha());
        dto.setFechaFin(feriado.getFechaFin());
        dto.setDescripcion(feriado.getDescripcion());
        if (feriado.getCentroAtencion() != null) {
            dto.setCentroAtencionId(feriado.getCentroAtencion().getId());
            dto.setCentroAtencionNombre(feriado.getCentroAtencion().getNombre());
        }
        return dto;
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Indicá desde y hasta");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha hasta es anterior a desde");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > MAX_DIAS) {
            throw new IllegalArgumentException("El rango no puede superar " + MAX_DIAS + " días");
        }
    }

    private static final class Alcance {
        private Integer centroId;
        private Integer medicoId;
        private Integer pacienteId;
        private final Set<Integer> centrosFeriado = new LinkedHashSet<>();
        private boolean feriadosSinFiltro;
        private boolean sumarCentrosDeLosTurnos;
    }
}
