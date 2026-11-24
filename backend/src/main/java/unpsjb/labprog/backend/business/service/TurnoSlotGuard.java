package unpsjb.labprog.backend.business.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.exception.TurnoTomadoException;
import unpsjb.labprog.backend.model.Turno;

/**
 * Un solo turno activo por médico, fecha y hora de inicio.
 *
 * <p>El candado en memoria ordena las reservas de este proceso. El índice único
 * parcial {@code ux_turno_slot_activo} (activos, sin los sobreturnos pedidos a
 * propósito) cubre la carrera entre procesos. Si la base rechaza el insert, la
 * respuesta es {@link TurnoTomadoException}.
 *
 * <p>Un sobreturno marcado a propósito no entra en el índice ni en este candado:
 * el personal de mostrador ya confirmó que quiere pisar el horario.
 */
@Component
public class TurnoSlotGuard {

    public static final String INDICE = "ux_turno_slot_activo";

    private final TurnoRepository turnoRepository;
    private final ConcurrentHashMap<String, Object> candados = new ConcurrentHashMap<>();

    public TurnoSlotGuard(TurnoRepository turnoRepository) {
        this.turnoRepository = turnoRepository;
    }

    public Turno guardar(Turno turno) {
        if (turno == null) {
            throw new IllegalArgumentException("El turno es obligatorio.");
        }
        if (esSobreturno(turno) || !tieneSlot(turno)) {
            return traducir(turno);
        }
        Object candado = candados.computeIfAbsent(clave(turno), k -> new Object());
        synchronized (candado) {
            rechazarSiOcupado(turno);
            return traducir(turno);
        }
    }

    private Turno traducir(Turno turno) {
        try {
            return turnoRepository.save(turno);
        } catch (DataIntegrityViolationException ex) {
            throw new TurnoTomadoException();
        }
    }

    private void rechazarSiOcupado(Turno turno) {
        List<Turno> ocupados = turnoRepository.bloquearSlotActivo(
                turno.getStaffMedico().getId(), turno.getFecha(), turno.getHoraInicio());
        if (ocupados == null) {
            return;
        }
        for (Turno otro : ocupados) {
            if (otro == null) {
                continue;
            }
            if (turno.getId() != null && turno.getId().equals(otro.getId())) {
                continue;
            }
            throw new TurnoTomadoException();
        }
    }

    private static boolean tieneSlot(Turno turno) {
        return turno.getStaffMedico() != null && turno.getStaffMedico().getId() != null
                && turno.getFecha() != null && turno.getHoraInicio() != null;
    }

    static boolean esSobreturno(Turno turno) {
        return turno != null && Boolean.TRUE.equals(turno.getSobreturno());
    }

    private static String clave(Turno turno) {
        return turno.getStaffMedico().getId() + "|" + turno.getFecha() + "|" + turno.getHoraInicio();
    }
}
