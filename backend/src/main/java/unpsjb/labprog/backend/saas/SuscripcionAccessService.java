package unpsjb.labprog.backend.saas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.ConsultorioRepository;
import unpsjb.labprog.backend.business.repository.StaffMedicoRepository;
import unpsjb.labprog.backend.business.repository.SuscripcionEventoRepository;
import unpsjb.labprog.backend.business.repository.SuscripcionRepository;
import unpsjb.labprog.backend.business.repository.UserRepository;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.PlanSuscripcion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Suscripcion;
import unpsjb.labprog.backend.model.SuscripcionEvento;

@Service
public class SuscripcionAccessService {

    @Autowired
    private CentroAtencionRepository centroAtencionRepository;

    @Autowired
    private SuscripcionRepository suscripcionRepository;

    @Autowired
    private StaffMedicoRepository staffMedicoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsultorioRepository consultorioRepository;

    @Autowired
    private SuscripcionEventoRepository eventoRepository;

    @Transactional
    public void assertOperacion(Integer centroId, RecursoSaas recurso) {
        assertOperacion(centroId, recurso, null);
    }

    /**
     * Sin suscripción (centro legado todavía no vinculado) no se bloquea,
     * para no dejar afuera datos de prueba ni un alta hecha por SUPERADMIN.
     * Con suscripción, el trial vencido o la mora dejan la cuenta en solo lectura
     * y los topes del plan rechazan el alta.
     */
    @Transactional
    public void assertOperacion(Integer centroId, RecursoSaas recurso, Integer medicoId) {
        Suscripcion suscripcion = buscarPorCentro(centroId).orElse(null);
        if (suscripcion == null || suscripcion.getPlan() == null || suscripcion.getOrganizacion() == null) {
            return;
        }
        sincronizar(suscripcion);
        if (!suscripcion.getEstado().permiteEscribir()) {
            throw new ReglaSuscripcionException(mensajeSoloLectura(suscripcion.getEstado()));
        }
        Integer orgId = suscripcion.getOrganizacion().getId();
        if (recurso == RecursoSaas.MEDICO && medicoId != null
                && staffMedicoRepository.countMedicoEnOrganizacion(orgId, medicoId) > 0) {
            return;
        }
        UsoOrganizacion uso = uso(orgId);
        LimitesPlan.validarNuevo(suscripcion.getPlan(), uso, recurso);
        ampliarCupoSiHaceFalta(suscripcion, uso, recurso);
    }

    @Transactional
    public void assertReporteBasico(Integer centroId) {
        Suscripcion suscripcion = buscarPorCentro(centroId).orElse(null);
        if (suscripcion == null || suscripcion.getPlan() == null) {
            return;
        }
        PlanSuscripcion plan = suscripcion.getPlan();
        if (!plan.isReportesBasicos() && !plan.isReportesConsolidados()) {
            throw new ReglaSuscripcionException(
                    "El plan " + plan.getNombre() + " no incluye reportes. Podés cambiar de plan desde Mi plan.");
        }
    }

    @Transactional
    public void assertReporteConsolidado(Integer centroId) {
        Suscripcion suscripcion = requerirPorCentro(centroId);
        if (!suscripcion.getPlan().isReportesConsolidados()) {
            throw new ReglaSuscripcionException(
                    "El plan " + suscripcion.getPlan().getNombre() + " no incluye reportes consolidados de la red.");
        }
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Suscripcion> buscarPorCentro(Integer centroId) {
        if (centroId == null) {
            return java.util.Optional.empty();
        }
        CentroAtencion centro = centroAtencionRepository.findById(centroId).orElse(null);
        if (centro == null || centro.getOrganizacion() == null || centro.getOrganizacion().getId() == null) {
            return java.util.Optional.empty();
        }
        return suscripcionRepository.findByOrganizacion_Id(centro.getOrganizacion().getId());
    }

    @Transactional
    public Suscripcion requerirPorCentro(Integer centroId) {
        if (centroId == null) {
            throw new ReglaSuscripcionException("Este usuario no administra un centro con suscripción.");
        }
        return buscarPorCentro(centroId)
                .orElseThrow(() -> new ReglaSuscripcionException("El centro no tiene una suscripción asociada."));
    }

    @Transactional
    public Suscripcion sincronizar(Suscripcion suscripcion) {
        if (suscripcion == null) {
            return null;
        }
        EstadoSuscripcion vigente = SuscripcionMaquinaEstados.vencerSiCorresponde(suscripcion, RelojArgentina.ahora());
        if (vigente != null && vigente != suscripcion.getEstado()) {
            EstadoSuscripcion anterior = suscripcion.getEstado();
            suscripcion.setEstado(vigente);
            suscripcionRepository.save(suscripcion);
            registrar(suscripcion, TipoEventoSuscripcion.VENCIMIENTO,
                    "Pasó de " + anterior + " a " + vigente + " por vencimiento.", "sistema");
        }
        return suscripcion;
    }

    @Transactional(readOnly = true)
    public UsoOrganizacion uso(Integer organizacionId) {
        if (organizacionId == null) {
            return new UsoOrganizacion(0, 0, 0, 0, 0);
        }
        return new UsoOrganizacion(
                staffMedicoRepository.countDistinctMedicosByOrganizacion(organizacionId),
                userRepository.countByRoleAndCentroAtencion_Organizacion_Id(Role.OPERADOR, organizacionId),
                consultorioRepository.countByCentroAtencion_Organizacion_Id(organizacionId),
                centroAtencionRepository.countByOrganizacion_Id(organizacionId),
                centroAtencionRepository.countEspecialidadesByOrganizacion(organizacionId));
    }

    public void registrar(Suscripcion suscripcion, TipoEventoSuscripcion tipo, String detalle, String actor) {
        SuscripcionEvento evento = new SuscripcionEvento();
        evento.setSuscripcion(suscripcion);
        evento.setTipo(tipo);
        evento.setDetalle(detalle);
        evento.setActor(actor == null ? "sistema" : actor);
        evento.setFecha(RelojArgentina.ahora());
        eventoRepository.save(evento);
    }

    private void ampliarCupoSiHaceFalta(Suscripcion suscripcion, UsoOrganizacion uso, RecursoSaas recurso) {
        PlanSuscripcion plan = suscripcion.getPlan();
        if (recurso != RecursoSaas.MEDICO || plan.getPrecioPorProfesional() == null
                || plan.getPrecioPorProfesional().signum() <= 0) {
            return;
        }
        int siguiente = (int) uso.medicos() + 1;
        int maximo = plan.getMaxProfesionales() == null ? Integer.MAX_VALUE : plan.getMaxProfesionales();
        if (suscripcion.getProfesionalesContratados() < siguiente && siguiente <= maximo) {
            suscripcion.setProfesionalesContratados(siguiente);
            suscripcionRepository.save(suscripcion);
        }
    }

    public static String mensajeSoloLectura(EstadoSuscripcion estado) {
        String etiqueta = switch (estado) {
            case VENCIDA -> "vencida";
            case MOROSA -> "morosa";
            case SUSPENDIDA -> "suspendida";
            case CANCELADA -> "cancelada";
            default -> estado.name().toLowerCase();
        };
        return "La suscripción está " + etiqueta
                + ". La cuenta es de solo lectura: no se pueden crear turnos ni recursos nuevos. Los datos de los pacientes se conservan.";
    }
}
