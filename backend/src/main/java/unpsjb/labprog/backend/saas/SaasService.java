package unpsjb.labprog.backend.saas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.AddonMensajeriaRepository;
import unpsjb.labprog.backend.business.repository.CentroAtencionRepository;
import unpsjb.labprog.backend.business.repository.ConfiguracionSaasRepository;
import unpsjb.labprog.backend.business.repository.FacturaSuscripcionRepository;
import unpsjb.labprog.backend.business.repository.OrganizacionRepository;
import unpsjb.labprog.backend.business.repository.PagoSuscripcionRepository;
import unpsjb.labprog.backend.business.repository.PlanSuscripcionRepository;
import unpsjb.labprog.backend.business.repository.SuscripcionAddonRepository;
import unpsjb.labprog.backend.business.repository.SuscripcionRepository;
import unpsjb.labprog.backend.business.repository.UserRepository;
import unpsjb.labprog.backend.config.AuditContext;
import unpsjb.labprog.backend.config.TenantContext;
import unpsjb.labprog.backend.model.AddonMensajeria;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.ConfiguracionSaas;
import unpsjb.labprog.backend.model.FacturaSuscripcion;
import unpsjb.labprog.backend.model.Organizacion;
import unpsjb.labprog.backend.model.PagoSuscripcion;
import unpsjb.labprog.backend.model.PlanSuscripcion;
import unpsjb.labprog.backend.model.Role;
import unpsjb.labprog.backend.model.Suscripcion;
import unpsjb.labprog.backend.model.SuscripcionAddon;
import unpsjb.labprog.backend.model.SuscripcionEvento;
import unpsjb.labprog.backend.model.User;
import unpsjb.labprog.backend.saas.SaasDtos.AddonDTO;
import unpsjb.labprog.backend.saas.SaasDtos.CambioComercialRequest;
import unpsjb.labprog.backend.saas.SaasDtos.CatalogoPublico;
import unpsjb.labprog.backend.saas.SaasDtos.CentroDTO;
import unpsjb.labprog.backend.saas.SaasDtos.ConfiguracionDTO;
import unpsjb.labprog.backend.saas.SaasDtos.ConteoPlan;
import unpsjb.labprog.backend.saas.SaasDtos.CotizacionRequest;
import unpsjb.labprog.backend.saas.SaasDtos.EventoDTO;
import unpsjb.labprog.backend.saas.SaasDtos.OrganizacionDTO;
import unpsjb.labprog.backend.saas.SaasDtos.PagoDTO;
import unpsjb.labprog.backend.saas.SaasDtos.PlanDTO;
import unpsjb.labprog.backend.saas.SaasDtos.PrecioDTO;
import unpsjb.labprog.backend.saas.SaasDtos.RegistroRequest;
import unpsjb.labprog.backend.saas.SaasDtos.RegistroResponse;
import unpsjb.labprog.backend.saas.SaasDtos.ResumenSaas;
import unpsjb.labprog.backend.saas.SaasDtos.SedeRequest;
import unpsjb.labprog.backend.saas.SaasDtos.SuscripcionDetalle;
import unpsjb.labprog.backend.saas.SaasDtos.UsoDTO;
import unpsjb.labprog.backend.saas.pasarela.EventoPasarela;
import unpsjb.labprog.backend.saas.pasarela.PasarelaNoImplementadaException;
import unpsjb.labprog.backend.saas.pasarela.PasarelaPago;
import unpsjb.labprog.backend.saas.pasarela.ResultadoPlanExterno;
import unpsjb.labprog.backend.saas.pasarela.ResultadoPreapproval;
import unpsjb.labprog.backend.saas.pasarela.SolicitudPlanExterno;
import unpsjb.labprog.backend.saas.pasarela.SolicitudPreapproval;
import unpsjb.labprog.backend.saas.pasarela.TipoNotificacionPasarela;
import unpsjb.labprog.backend.business.repository.SuscripcionEventoRepository;

@Service
@Transactional
public class SaasService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final BigDecimal CIEN = new BigDecimal("100");

    @Autowired
    private PlanSuscripcionRepository planRepository;
    @Autowired
    private ConfiguracionSaasRepository configuracionRepository;
    @Autowired
    private AddonMensajeriaRepository addonRepository;
    @Autowired
    private OrganizacionRepository organizacionRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private SuscripcionAddonRepository suscripcionAddonRepository;
    @Autowired
    private PagoSuscripcionRepository pagoRepository;
    @Autowired
    private FacturaSuscripcionRepository facturaRepository;
    @Autowired
    private SuscripcionEventoRepository eventoRepository;
    @Autowired
    private CentroAtencionRepository centroAtencionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasarelaPago pasarelaPago;
    @Autowired
    private SuscripcionAccessService access;

    public CatalogoPublico catalogoPublico() {
        asegurarDatosIniciales();
        ConfiguracionSaas config = configuracion();
        CatalogoPublico catalogo = new CatalogoPublico();
        catalogo.configuracion = configuracionDto(config);
        catalogo.planes = planRepository.findByActivoTrueOrderByOrdenAsc().stream()
                .map(plan -> armarPlan(plan, config, false))
                .toList();
        catalogo.addons = addonRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(addon -> armarAddon(addon, config))
                .toList();
        return catalogo;
    }

    public CatalogoPublico catalogoAdmin() {
        asegurarDatosIniciales();
        ConfiguracionSaas config = configuracion();
        CatalogoPublico catalogo = new CatalogoPublico();
        catalogo.configuracion = configuracionDto(config);
        catalogo.planes = planRepository.findAllByOrderByOrdenAsc().stream()
                .map(plan -> armarPlan(plan, config, true))
                .toList();
        catalogo.addons = addonRepository.findAll().stream()
                .sorted(Comparator.comparing(AddonMensajeria::getNombre))
                .map(addon -> armarAddon(addon, config))
                .toList();
        return catalogo;
    }

    public PrecioDTO cotizar(CotizacionRequest request) {
        PlanSuscripcion plan = requerirPlanActivo(request == null ? null : request.planCodigo);
        CicloFacturacion ciclo = ciclo(request == null ? null : request.ciclo);
        int sedes = request == null || request.sedes == null || request.sedes < 1 ? 1 : request.sedes;
        int cupo = request == null ? 1 : cupoInicial(plan, request.profesionalesContratados);
        List<BigDecimal> addons = importesAddons(resolverAddons(request == null ? null : request.addons));
        return precioDto(CalculadoraPrecioSaas.calcular(plan, configuracion(), cupo, sedes, addons, ciclo));
    }

    public RegistroResponse registrar(RegistroRequest request) {
        asegurarDatosIniciales();
        if (request == null) {
            throw new ReglaSuscripcionException("Faltan los datos del registro.");
        }
        PlanSuscripcion plan = requerirPlanActivo(request.planCodigo);
        CicloFacturacion ciclo = ciclo(request.ciclo);
        int cupo = cupoInicial(plan, request.profesionalesContratados);
        List<AddonMensajeria> addons = resolverAddons(request.addons);

        String email = requerido(request.adminEmail, "El email del administrador").toLowerCase();
        if (!email.contains("@") || !email.contains(".")) {
            throw new ReglaSuscripcionException("El email del administrador no es válido.");
        }
        if (request.adminPassword == null || request.adminPassword.length() < 6) {
            throw new ReglaSuscripcionException("La contraseña debe tener al menos 6 caracteres.");
        }
        if (request.adminDni == null || request.adminDni <= 0) {
            throw new ReglaSuscripcionException("El DNI del administrador es obligatorio.");
        }
        String telefonoAdmin = requerido(request.adminTelefono, "El teléfono del administrador");
        if (userRepository.existsByEmail(email)) {
            throw new ReglaSuscripcionException("Ya existe un usuario con el email " + email + ".");
        }
        if (userRepository.existsByDni(request.adminDni)) {
            throw new ReglaSuscripcionException("Ya existe un usuario con ese DNI.");
        }
        if (userRepository.existsByTelefono(telefonoAdmin)) {
            throw new ReglaSuscripcionException("Ya existe un usuario con ese teléfono.");
        }

        String nombreCentro = requerido(request.centroNombre, "El nombre del centro");
        String direccion = requerido(request.direccion, "La dirección");
        if (centroAtencionRepository.existsByNombre(nombreCentro)) {
            throw new ReglaSuscripcionException("Ya existe un centro de atención con ese nombre.");
        }
        if (centroAtencionRepository.existsByDireccion(direccion)) {
            throw new ReglaSuscripcionException("Ya existe un centro de atención con esa dirección.");
        }
        String telefonoCentro = requerido(request.telefonoCentro, "El teléfono del centro");
        if (!telefonoCentro.matches("\\d+")) {
            throw new ReglaSuscripcionException("El teléfono del centro solo puede contener números.");
        }
        validarCoordenadas(request.latitud, request.longitud);

        LocalDateTime ahora = RelojArgentina.ahora();
        ConfiguracionSaas config = configuracion();

        Organizacion organizacion = new Organizacion();
        organizacion.setNombre(texto(request.organizacionNombre) == null ? nombreCentro : request.organizacionNombre.trim());
        organizacion.setCuit(texto(request.cuit));
        organizacion.setEmailContacto(texto(request.emailContacto) == null ? email : request.emailContacto.trim());
        organizacion.setTelefono(telefonoCentro);
        organizacion.setFechaAlta(ahora);
        organizacion = organizacionRepository.save(organizacion);

        CentroAtencion centro = new CentroAtencion();
        centro.setNombre(nombreCentro);
        centro.setDireccion(direccion);
        centro.setLocalidad(requerido(request.localidad, "La localidad"));
        centro.setProvincia(requerido(request.provincia, "La provincia"));
        centro.setTelefono(telefonoCentro);
        centro.setLatitud(request.latitud);
        centro.setLongitud(request.longitud);
        centro.setOrganizacion(organizacion);
        centro = centroAtencionRepository.save(centro);

        User admin = new User();
        admin.setNombre(requerido(request.adminNombre, "El nombre"));
        admin.setApellido(requerido(request.adminApellido, "El apellido"));
        admin.setDni(request.adminDni);
        admin.setEmail(email);
        admin.setTelefono(telefonoAdmin);
        admin.setHashedPassword(passwordEncoder.encode(request.adminPassword));
        admin.setRole(Role.ADMINISTRADOR);
        admin.setCentroAtencion(centro);
        admin.setEnabled(true);
        admin.setAccountNonExpired(true);
        admin.setAccountNonLocked(true);
        admin.setCredentialsNonExpired(true);
        admin.setEmailVerified(true);
        admin.setEmailVerifiedAt(ahora);
        userRepository.save(admin);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setOrganizacion(organizacion);
        suscripcion.setPlan(plan);
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);
        suscripcion.setCiclo(ciclo);
        suscripcion.setProfesionalesContratados(cupo);
        suscripcion.setFechaInicio(ahora);
        suscripcion.setFechaFinTrial(ahora.plusDays(config.getDiasTrial()));
        suscripcion.setHeredada(false);
        suscripcion = suscripcionRepository.save(suscripcion);
        guardarAddons(suscripcion, addons);
        access.registrar(suscripcion, TipoEventoSuscripcion.INICIO_TRIAL,
                "Prueba de " + config.getDiasTrial() + " días del plan " + plan.getNombre() + ", sin tarjeta.",
                email);

        RegistroResponse response = new RegistroResponse();
        response.suscripcionId = suscripcion.getId();
        response.organizacionId = organizacion.getId();
        response.centroId = centro.getId();
        response.email = email;
        response.estado = suscripcion.getEstado().name();
        response.planCodigo = plan.getCodigo();
        response.fechaFinTrial = fecha(suscripcion.getFechaFinTrial());
        response.diasTrial = config.getDiasTrial();
        response.soloLectura = false;
        return response;
    }

    public SuscripcionDetalle miSuscripcion() {
        Integer centroId = TenantContext.getCurrentCentroId();
        Suscripcion suscripcion = access.sincronizar(access.requerirPorCentro(centroId));
        return detalle(suscripcion);
    }

    public PrecioDTO pagarMiSuscripcion(String cicloRaw) {
        Suscripcion suscripcion = access.sincronizar(access.requerirPorCentro(TenantContext.getCurrentCentroId()));
        return cobrar(suscripcion, ciclo(cicloRaw), suscripcion.getEstado() == EstadoSuscripcion.ACTIVA
                ? TipoMovimientoPago.RENOVACION
                : TipoMovimientoPago.ALTA);
    }

    public SuscripcionDetalle actualizarMiPlan(CambioComercialRequest request) {
        Suscripcion suscripcion = access.sincronizar(access.requerirPorCentro(TenantContext.getCurrentCentroId()));
        cambiarComercial(suscripcion, request);
        return detalle(suscripcionRepository.findById(suscripcion.getId()).orElseThrow());
    }

    public CentroDTO crearSede(SedeRequest request) {
        Suscripcion suscripcion = access.sincronizar(access.requerirPorCentro(TenantContext.getCurrentCentroId()));
        if (suscripcion.isSoloLectura()) {
            throw new ReglaSuscripcionException(SuscripcionAccessService.mensajeSoloLectura(suscripcion.getEstado()));
        }
        if (request == null) {
            throw new ReglaSuscripcionException("Faltan los datos de la sede.");
        }
        String nombre = requerido(request.nombre, "El nombre de la sede");
        String direccion = requerido(request.direccion, "La dirección");
        if (centroAtencionRepository.existsByNombre(nombre)) {
            throw new ReglaSuscripcionException("Ya existe un centro de atención con ese nombre.");
        }
        if (centroAtencionRepository.existsByDireccion(direccion)) {
            throw new ReglaSuscripcionException("Ya existe un centro de atención con esa dirección.");
        }
        String telefono = requerido(request.telefono, "El teléfono");
        if (!telefono.matches("\\d+")) {
            throw new ReglaSuscripcionException("El teléfono del centro solo puede contener números.");
        }
        validarCoordenadas(request.latitud, request.longitud);
        UsoOrganizacion uso = access.uso(suscripcion.getOrganizacion().getId());
        LimitesPlan.validarNuevo(suscripcion.getPlan(), uso, RecursoSaas.CENTRO);

        CentroAtencion centro = new CentroAtencion();
        centro.setNombre(nombre);
        centro.setDireccion(direccion);
        centro.setLocalidad(requerido(request.localidad, "La localidad"));
        centro.setProvincia(requerido(request.provincia, "La provincia"));
        centro.setTelefono(telefono);
        centro.setLatitud(request.latitud);
        centro.setLongitud(request.longitud);
        centro.setOrganizacion(suscripcion.getOrganizacion());
        centro = centroAtencionRepository.save(centro);
        access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PLAN,
                "Se agregó la sede " + centro.getNombre() + ".", actor());
        return centroDto(centro);
    }

    public Map<String, Object> reporteConsolidado() {
        Integer centroId = TenantContext.getCurrentCentroId();
        access.assertReporteConsolidado(centroId);
        Suscripcion suscripcion = access.requerirPorCentro(centroId);
        List<Map<String, Object>> sedes = new ArrayList<>();
        for (CentroAtencion centro : centroAtencionRepository.findByOrganizacion_Id(suscripcion.getOrganizacion().getId())) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("centroId", centro.getId());
            fila.put("nombre", centro.getNombre());
            fila.put("localidad", centro.getLocalidad());
            sedes.add(fila);
        }
        Map<String, Object> reporte = new HashMap<>();
        reporte.put("organizacion", suscripcion.getOrganizacion().getNombre());
        reporte.put("plan", suscripcion.getPlan().getCodigo());
        reporte.put("soportePrioritario", suscripcion.getPlan().isSoportePrioritario());
        reporte.put("sedes", sedes);
        reporte.put("uso", usoDto(suscripcion));
        return reporte;
    }

    public ResumenSaas resumen() {
        asegurarDatosIniciales();
        sincronizarTodas();
        ConfiguracionSaas config = configuracion();
        ResumenSaas resumen = new ResumenSaas();
        resumen.activas = suscripcionRepository.countByEstado(EstadoSuscripcion.ACTIVA);
        resumen.trials = suscripcionRepository.countByEstado(EstadoSuscripcion.TRIAL);
        resumen.vencidas = suscripcionRepository.countByEstado(EstadoSuscripcion.VENCIDA);
        resumen.morosas = suscripcionRepository.countByEstado(EstadoSuscripcion.MOROSA);
        resumen.suspendidas = suscripcionRepository.countByEstado(EstadoSuscripcion.SUSPENDIDA);
        resumen.canceladas = suscripcionRepository.countByEstado(EstadoSuscripcion.CANCELADA);
        resumen.trialsPorVencer = suscripcionRepository
                .trialsQueVencenHasta(EstadoSuscripcion.TRIAL, RelojArgentina.ahora().plusDays(7)).size();
        resumen.heredadasActivas = suscripcionRepository.findByEstado(EstadoSuscripcion.ACTIVA).stream()
                .filter(Suscripcion::isHeredada).count();
        resumen.moneda = config.getMoneda();
        resumen.configuracion = configuracionDto(config);

        BigDecimal mrr = BigDecimal.ZERO;
        Map<String, ConteoPlan> porPlan = new HashMap<>();
        for (Suscripcion suscripcion : suscripcionRepository.findAll()) {
            ConteoPlan conteo = porPlan.computeIfAbsent(suscripcion.getPlan().getCodigo(), codigo -> {
                ConteoPlan creado = new ConteoPlan();
                creado.codigo = codigo;
                creado.nombre = suscripcion.getPlan().getNombre();
                creado.mrrNeto = BigDecimal.ZERO;
                return creado;
            });
            conteo.cantidad++;
            if (suscripcion.getEstado() == EstadoSuscripcion.ACTIVA && !suscripcion.isHeredada()) {
                BigDecimal neto = precioDe(suscripcion, CicloFacturacion.MENSUAL).netoMensual();
                mrr = mrr.add(neto);
                conteo.mrrNeto = conteo.mrrNeto.add(neto);
            }
        }
        resumen.mrrNeto = mrr.setScale(2, RoundingMode.HALF_UP);
        resumen.porPlan = porPlan.values().stream()
                .sorted(Comparator.comparing(item -> item.codigo))
                .toList();

        LocalDateTime desde = RelojArgentina.ahora().toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime hasta = desde.plusMonths(1);
        BigDecimal netoMes = BigDecimal.ZERO;
        BigDecimal totalMes = BigDecimal.ZERO;
        for (PagoSuscripcion pago : pagoRepository.findByFechaBetweenAndEstado(desde, hasta, EstadoPago.APROBADO)) {
            netoMes = netoMes.add(pago.getNeto());
            totalMes = totalMes.add(pago.getTotal());
        }
        resumen.ingresosNetosMes = netoMes.setScale(2, RoundingMode.HALF_UP);
        resumen.ingresosConIvaMes = totalMes.setScale(2, RoundingMode.HALF_UP);
        return resumen;
    }

    public List<SuscripcionDetalle> listar(String estado, String plan, String q) {
        sincronizarTodas();
        EstadoSuscripcion filtroEstado = texto(estado) == null ? null : EstadoSuscripcion.valueOf(estado.trim().toUpperCase());
        List<Suscripcion> encontradas = suscripcionRepository.buscar(filtroEstado, texto(plan), texto(q));
        return encontradas.stream().map(this::detalle).toList();
    }

    public SuscripcionDetalle detalleAdmin(Integer id) {
        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new ReglaSuscripcionException("Suscripción no encontrada."));
        return detalle(access.sincronizar(suscripcion));
    }

    public SuscripcionDetalle extenderTrial(Integer id, int dias) {
        if (dias < 1 || dias > 365) {
            throw new ReglaSuscripcionException("La extensión tiene que ser de 1 a 365 días.");
        }
        Suscripcion suscripcion = requerir(id);
        if (suscripcion.getEstado() != EstadoSuscripcion.TRIAL && suscripcion.getEstado() != EstadoSuscripcion.VENCIDA) {
            throw new ReglaSuscripcionException("Solo se extiende la prueba de una suscripción en trial o vencida.");
        }
        LocalDateTime ahora = RelojArgentina.ahora();
        LocalDateTime base = suscripcion.getFechaFinTrial();
        if (base == null || base.isBefore(ahora)) {
            base = ahora;
        }
        suscripcion.setFechaFinTrial(base.plusDays(dias));
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);
        suscripcionRepository.save(suscripcion);
        access.registrar(suscripcion, TipoEventoSuscripcion.EXTENSION_TRIAL,
                "Prueba extendida " + dias + " días, hasta " + fecha(suscripcion.getFechaFinTrial()) + ".", actor());
        return detalle(suscripcion);
    }

    public SuscripcionDetalle suspender(Integer id, String motivo) {
        Suscripcion suscripcion = requerir(id);
        if (suscripcion.getEstado() == EstadoSuscripcion.CANCELADA) {
            throw new ReglaSuscripcionException("Una suscripción cancelada no se suspende: hay que darla de alta de nuevo.");
        }
        if (suscripcion.getEstado() != EstadoSuscripcion.SUSPENDIDA) {
            suscripcion.setEstadoAnterior(suscripcion.getEstado());
            suscripcion.setEstado(EstadoSuscripcion.SUSPENDIDA);
        }
        suscripcion.setMotivo(texto(motivo));
        suscripcionRepository.save(suscripcion);
        tocarPasarela(suscripcion, true);
        access.registrar(suscripcion, TipoEventoSuscripcion.SUSPENSION,
                texto(motivo) == null ? "Suscripción suspendida." : motivo.trim(), actor());
        return detalle(suscripcion);
    }

    public SuscripcionDetalle reactivar(Integer id) {
        Suscripcion suscripcion = requerir(id);
        if (suscripcion.getEstado() != EstadoSuscripcion.SUSPENDIDA) {
            throw new ReglaSuscripcionException("Solo se reactiva una suscripción suspendida.");
        }
        EstadoSuscripcion anterior = suscripcion.getEstadoAnterior() == null
                ? (suscripcion.isHeredada() ? EstadoSuscripcion.ACTIVA : EstadoSuscripcion.TRIAL)
                : suscripcion.getEstadoAnterior();
        suscripcion.setEstado(anterior);
        suscripcion.setEstadoAnterior(null);
        suscripcion.setMotivo(null);
        suscripcionRepository.save(suscripcion);
        access.sincronizar(suscripcion);
        tocarPasarela(suscripcion, false);
        access.registrar(suscripcion, TipoEventoSuscripcion.REACTIVACION,
                "Suscripción reactivada en estado " + suscripcion.getEstado() + ".", actor());
        return detalle(suscripcion);
    }

    public SuscripcionDetalle cancelar(Integer id, String motivo) {
        Suscripcion suscripcion = requerir(id);
        suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
        suscripcion.setMotivo(texto(motivo));
        suscripcionRepository.save(suscripcion);
        if (suscripcion.getMpPreapprovalId() != null) {
            try {
                ResultadoPreapproval resultado = pasarelaPago.cancelar(suscripcion.getMpPreapprovalId());
                suscripcion.setMpStatus(resultado.estadoMp());
            } catch (PasarelaNoImplementadaException e) {
                access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PASARELA, e.getMessage(), actor());
            }
        }
        access.registrar(suscripcion, TipoEventoSuscripcion.CANCELACION,
                texto(motivo) == null ? "Suscripción cancelada. Los datos se conservan en solo lectura." : motivo.trim(),
                actor());
        return detalle(suscripcion);
    }

    public SuscripcionDetalle cambiarPlanAdmin(Integer id, CambioComercialRequest request) {
        Suscripcion suscripcion = access.sincronizar(requerir(id));
        cambiarComercial(suscripcion, request);
        return detalle(suscripcionRepository.findById(id).orElseThrow());
    }

    public void vincularCentro(Integer suscripcionId, Integer centroId) {
        Suscripcion suscripcion = requerir(suscripcionId);
        CentroAtencion centro = centroAtencionRepository.findById(centroId)
                .orElseThrow(() -> new ReglaSuscripcionException("Centro no encontrado."));
        if (centro.getOrganizacion() != null
                && !centro.getOrganizacion().getId().equals(suscripcion.getOrganizacion().getId())) {
            throw new ReglaSuscripcionException("Ese centro ya pertenece a otra organización.");
        }
        if (centro.getOrganizacion() == null) {
            UsoOrganizacion uso = access.uso(suscripcion.getOrganizacion().getId());
            LimitesPlan.validarNuevo(suscripcion.getPlan(), uso, RecursoSaas.CENTRO);
            centro.setOrganizacion(suscripcion.getOrganizacion());
            centroAtencionRepository.save(centro);
            access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PLAN,
                    "Se vinculó el centro " + centro.getNombre() + ".", actor());
        }
    }

    public PlanDTO actualizarPlan(Integer id, PlanDTO request) {
        PlanSuscripcion plan = planRepository.findById(id)
                .orElseThrow(() -> new ReglaSuscripcionException("Plan no encontrado."));
        if (request == null) {
            throw new ReglaSuscripcionException("Faltan los datos del plan.");
        }
        if (request.nombre != null && !request.nombre.isBlank()) {
            plan.setNombre(request.nombre.trim());
        }
        if (request.descripcion != null) {
            plan.setDescripcion(request.descripcion);
        }
        if (request.precioBaseMensual != null) {
            exigirNoNegativo(request.precioBaseMensual, "El precio base");
            plan.setPrecioBaseMensual(request.precioBaseMensual);
        }
        if (request.precioPorProfesional != null) {
            exigirNoNegativo(request.precioPorProfesional, "El precio por profesional");
            plan.setPrecioPorProfesional(request.precioPorProfesional);
        }
        plan.setMinProfesionales(request.minProfesionales);
        plan.setMaxProfesionales(request.maxProfesionales);
        plan.setMaxMedicos(request.maxMedicos);
        plan.setMaxOperadores(request.maxOperadores);
        plan.setMaxConsultorios(request.maxConsultorios);
        plan.setMaxEspecialidades(request.maxEspecialidades);
        plan.setMaxCentros(request.maxCentros);
        if (request.sedesIncluidas != null) {
            if (request.sedesIncluidas < 0) {
                throw new ReglaSuscripcionException("Las sedes incluidas no pueden ser negativas.");
            }
            plan.setSedesIncluidas(request.sedesIncluidas);
        }
        if (request.precioPorSedeExtra != null) {
            exigirNoNegativo(request.precioPorSedeExtra, "El precio por sede extra");
        }
        plan.setPrecioPorSedeExtra(request.precioPorSedeExtra);
        if (request.descuentoAnualPorcentaje != null) {
            exigirPorcentaje(request.descuentoAnualPorcentaje, "El descuento anual");
        }
        plan.setDescuentoAnualPorcentaje(request.descuentoAnualPorcentaje);
        plan.setReportesBasicos(request.reportesBasicos);
        plan.setReportesConsolidados(request.reportesConsolidados);
        plan.setSoportePrioritario(request.soportePrioritario);
        plan.setActivo(request.activo);
        plan.setMpPreapprovalPlanIdMensual(texto(request.mpPreapprovalPlanIdMensual));
        plan.setMpPreapprovalPlanIdAnual(texto(request.mpPreapprovalPlanIdAnual));
        planRepository.save(plan);
        return armarPlan(plan, configuracion(), true);
    }

    public ConfiguracionDTO actualizarConfiguracion(ConfiguracionDTO request) {
        if (request == null) {
            throw new ReglaSuscripcionException("Falta la configuración.");
        }
        ConfiguracionSaas config = configuracion();
        if (request.ivaPorcentaje != null) {
            exigirPorcentaje(request.ivaPorcentaje, "El IVA");
            config.setIvaPorcentaje(request.ivaPorcentaje);
        }
        if (request.diasTrial < 1 || request.diasTrial > 365) {
            throw new ReglaSuscripcionException("La prueba tiene que durar entre 1 y 365 días.");
        }
        config.setDiasTrial(request.diasTrial);
        if (request.descuentoAnualPorcentaje != null) {
            exigirPorcentaje(request.descuentoAnualPorcentaje, "El descuento anual");
            config.setDescuentoAnualPorcentaje(request.descuentoAnualPorcentaje);
        }
        if (texto(request.moneda) != null) {
            config.setMoneda(request.moneda.trim().toUpperCase());
        }
        configuracionRepository.save(config);
        return configuracionDto(config);
    }

    public AddonDTO actualizarAddon(Integer id, AddonDTO request) {
        AddonMensajeria addon = addonRepository.findById(id)
                .orElseThrow(() -> new ReglaSuscripcionException("Complemento no encontrado."));
        if (request == null) {
            throw new ReglaSuscripcionException("Faltan los datos del complemento.");
        }
        if (texto(request.nombre) != null) {
            addon.setNombre(request.nombre.trim());
        }
        if (request.descripcion != null) {
            addon.setDescripcion(request.descripcion);
        }
        if (request.precioMensualNeto != null) {
            exigirNoNegativo(request.precioMensualNeto, "El precio del complemento");
            addon.setPrecioMensualNeto(request.precioMensualNeto);
        }
        addon.setActivo(request.activo);
        addonRepository.save(addon);
        return armarAddon(addon, configuracion());
    }

    public List<PagoDTO> listarPagos() {
        return pagoRepository.findTop200ByOrderByFechaDesc().stream().map(this::pagoDto).toList();
    }

    public void aplicarEventoPasarela(EventoPasarela evento) {
        if (evento == null || evento.tipo() == TipoNotificacionPasarela.IGNORADA) {
            return;
        }
        Suscripcion suscripcion = localizar(evento);
        if (suscripcion == null) {
            return;
        }
        if (evento.tipo() == TipoNotificacionPasarela.PAYMENT && evento.recursoId() != null
                && pagoRepository.existsByMpPaymentId(evento.recursoId())) {
            return;
        }
        if (evento.tipo() == TipoNotificacionPasarela.PREAPPROVAL) {
            EstadoSuscripcion previo = suscripcion.getEstado();
            EstadoSuscripcion mapeado = MercadoPagoEstadoMapper.desdePreapproval(evento.estadoMp(), previo);
            suscripcion.setMpStatus(evento.estadoMp());
            if (evento.recursoId() != null) {
                suscripcion.setMpPreapprovalId(evento.recursoId());
            }
            if (mapeado == EstadoSuscripcion.SUSPENDIDA && previo != EstadoSuscripcion.SUSPENDIDA) {
                suscripcion.setEstadoAnterior(previo);
            }
            if (mapeado == EstadoSuscripcion.ACTIVA) {
                abrirPeriodoSiCorresponde(suscripcion, suscripcion.getCiclo());
                suscripcion.setHeredada(false);
            }
            if (mapeado != null) {
                suscripcion.setEstado(mapeado);
            }
            suscripcionRepository.save(suscripcion);
            access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PASARELA,
                    "Mercado Pago informó " + evento.estadoMp() + " para " + evento.recursoId() + ".", "mercadopago");
            return;
        }
        EstadoPago estadoPago = MercadoPagoEstadoMapper.desdePago(evento.estadoMp());
        PrecioCalculado precio = precioDe(suscripcion, suscripcion.getCiclo());
        if (estadoPago == EstadoPago.APROBADO) {
            abrirPeriodoSiCorresponde(suscripcion, suscripcion.getCiclo());
            suscripcion.setHeredada(false);
            suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
            suscripcionRepository.save(suscripcion);
        } else if (estadoPago == EstadoPago.RECHAZADO && suscripcion.getEstado() == EstadoSuscripcion.ACTIVA) {
            suscripcion.setEstado(EstadoSuscripcion.MOROSA);
            suscripcionRepository.save(suscripcion);
        }
        guardarPago(suscripcion, precio.netoPeriodo(), precio.iva(), precio.total(), estadoPago,
                TipoMovimientoPago.RENOVACION, suscripcion.getCiclo(), evento.recursoId(),
                "Pago informado por Mercado Pago");
    }

    public void asegurarDatosIniciales() {
        ConfiguracionSaas config = configuracionRepository.findById(ConfiguracionSaas.ID).orElse(null);
        if (config == null) {
            config = new ConfiguracionSaas();
            config.setId(ConfiguracionSaas.ID);
            config.setIvaPorcentaje(new BigDecimal("21.00"));
            config.setDiasTrial(30);
            config.setDescuentoAnualPorcentaje(new BigDecimal("15.00"));
            config.setMoneda("ARS");
            configuracionRepository.save(config);
        }
        asegurarPlan(CodigosPlan.PROFESIONAL, "Profesional",
                "Consultorio individual: un médico, una sede y hasta un operador.",
                new BigDecimal("25000.00"), BigDecimal.ZERO, null, 1, 1, 1, 1, null, 1, 1, null,
                false, false, false, 1);
        asegurarPlan(CodigosPlan.CONSULTORIO, "Consultorio",
                "Consultorio compartido. Se cobra por profesional, de 2 a 10, en una sola sede. Incluye reportes básicos.",
                BigDecimal.ZERO, new BigDecimal("18000.00"), 2, 10, 10, null, null, null, 1, 1, null,
                true, false, false, 2);
        asegurarPlan(CodigosPlan.CENTRO, "Centro",
                "Centro médico de una sede, con médicos, operadores y reportes sin tope.",
                new BigDecimal("200000.00"), BigDecimal.ZERO, null, null, null, null, null, null, 1, 1, null,
                true, false, false, 3);
        asegurarPlan(CodigosPlan.RED, "Red",
                "Red de centros. El precio incluye 3 sedes. Cada sede adicional se cobra aparte. Reportes consolidados y soporte prioritario.",
                new BigDecimal("350000.00"), BigDecimal.ZERO, null, null, null, null, null, null, null, 3,
                new BigDecimal("120000.00"), true, true, true, 4);
        asegurarAddon("WHATSAPP", "WhatsApp",
                "Recordatorios por WhatsApp. Complemento de referencia: el envío todavía no está conectado.",
                new BigDecimal("12000.00"));
        asegurarAddon("SMS", "SMS",
                "Avisos por SMS. Complemento de referencia: el envío todavía no está conectado.",
                new BigDecimal("7000.00"));
        heredarCentrosExistentes();
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "America/Argentina/Buenos_Aires")
    public void sincronizarVencimientos() {
        sincronizarTodas();
    }

    private void heredarCentrosExistentes() {
        PlanSuscripcion centroPlan = planRepository.findByCodigo(CodigosPlan.CENTRO).orElse(null);
        if (centroPlan == null) {
            return;
        }
        LocalDateTime ahora = RelojArgentina.ahora();
        for (CentroAtencion centro : centroAtencionRepository.findByOrganizacionIsNull()) {
            Organizacion organizacion = new Organizacion();
            organizacion.setNombre(centro.getNombre());
            organizacion.setEmailContacto("centro-" + centro.getId() + "@heredado.cheturno");
            organizacion.setTelefono(centro.getTelefono());
            organizacion.setFechaAlta(ahora);
            organizacion = organizacionRepository.save(organizacion);
            centro.setOrganizacion(organizacion);
            centroAtencionRepository.save(centro);

            Suscripcion suscripcion = new Suscripcion();
            suscripcion.setOrganizacion(organizacion);
            suscripcion.setPlan(centroPlan);
            suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
            suscripcion.setCiclo(CicloFacturacion.MENSUAL);
            suscripcion.setProfesionalesContratados(1);
            suscripcion.setFechaInicio(ahora);
            suscripcion.setFechaInicioPeriodo(ahora);
            suscripcion.setFechaFinPeriodo(ahora.plusYears(100));
            suscripcion.setHeredada(true);
            suscripcion.setMotivo("Suscripción activa heredada para no interrumpir la operación.");
            suscripcion = suscripcionRepository.save(suscripcion);
            access.registrar(suscripcion, TipoEventoSuscripcion.HEREDADA, suscripcion.getMotivo(), "sistema");
        }
    }

    private void asegurarPlan(String codigo, String nombre, String descripcion, BigDecimal base,
            BigDecimal porProfesional, Integer minProf, Integer maxProf, Integer maxMedicos, Integer maxOperadores,
            Integer maxConsultorios, Integer maxEspecialidades, Integer maxCentros, int sedesIncluidas,
            BigDecimal precioSedeExtra, boolean reportes, boolean consolidados, boolean soporte, int orden) {
        if (planRepository.findByCodigo(codigo).isPresent()) {
            return;
        }
        PlanSuscripcion plan = new PlanSuscripcion();
        plan.setCodigo(codigo);
        plan.setNombre(nombre);
        plan.setDescripcion(descripcion);
        plan.setPrecioBaseMensual(base);
        plan.setPrecioPorProfesional(porProfesional);
        plan.setMinProfesionales(minProf);
        plan.setMaxProfesionales(maxProf);
        plan.setMaxMedicos(maxMedicos);
        plan.setMaxOperadores(maxOperadores);
        plan.setMaxConsultorios(maxConsultorios);
        plan.setMaxEspecialidades(maxEspecialidades);
        plan.setMaxCentros(maxCentros);
        plan.setSedesIncluidas(sedesIncluidas);
        plan.setPrecioPorSedeExtra(precioSedeExtra);
        plan.setReportesBasicos(reportes);
        plan.setReportesConsolidados(consolidados);
        plan.setSoportePrioritario(soporte);
        plan.setActivo(true);
        plan.setOrden(orden);
        planRepository.save(plan);
    }

    private void asegurarAddon(String codigo, String nombre, String descripcion, BigDecimal precio) {
        if (addonRepository.findByCodigo(codigo).isPresent()) {
            return;
        }
        AddonMensajeria addon = new AddonMensajeria();
        addon.setCodigo(codigo);
        addon.setNombre(nombre);
        addon.setDescripcion(descripcion);
        addon.setPrecioMensualNeto(precio);
        addon.setActivo(true);
        addon.setPlaceholder(true);
        addonRepository.save(addon);
    }

    private PrecioDTO cobrar(Suscripcion suscripcion, CicloFacturacion ciclo, TipoMovimientoPago tipo) {
        if (suscripcion.getEstado() == EstadoSuscripcion.CANCELADA) {
            throw new ReglaSuscripcionException("La suscripción está cancelada.");
        }
        if (suscripcion.getEstado() == EstadoSuscripcion.SUSPENDIDA) {
            throw new ReglaSuscripcionException("La suscripción está suspendida. Reactivala antes de registrar un pago.");
        }
        PrecioCalculado precio = precioDe(suscripcion, ciclo);
        String planExternoId = asegurarPlanExterno(suscripcion.getPlan(), ciclo, precio.total());
        SolicitudPreapproval solicitud = new SolicitudPreapproval(
                suscripcion.getId(),
                "suscripcion-" + suscripcion.getId(),
                planExternoId,
                "CheTurno " + suscripcion.getPlan().getNombre() + " " + ciclo.name().toLowerCase(),
                suscripcion.getOrganizacion().getEmailContacto(),
                ciclo,
                precio.total(),
                configuracion().getMoneda());
        ResultadoPreapproval resultado;
        try {
            resultado = pasarelaPago.crearPreapproval(solicitud);
        } catch (PasarelaNoImplementadaException e) {
            throw new ReglaSuscripcionException(e.getMessage());
        }
        suscripcion.setMpPreapprovalId(resultado.preapprovalId());
        suscripcion.setMpStatus(resultado.estadoMp());
        EstadoSuscripcion mapeado = MercadoPagoEstadoMapper.desdePreapproval(resultado.estadoMp(), suscripcion.getEstado());
        EstadoPago estadoPago = MercadoPagoEstadoMapper.desdePago(resultado.estadoMp());
        if (mapeado == EstadoSuscripcion.ACTIVA || estadoPago == EstadoPago.APROBADO) {
            abrirPeriodo(suscripcion, ciclo);
            suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
            suscripcion.setHeredada(false);
        }
        suscripcion.setCiclo(ciclo);
        suscripcionRepository.save(suscripcion);
        guardarPago(suscripcion, precio.netoPeriodo(), precio.iva(), precio.total(), estadoPago, tipo, ciclo,
                resultado.paymentId(), "Pago " + tipo.name().toLowerCase() + " vía " + pasarelaPago.proveedor());
        access.registrar(suscripcion, TipoEventoSuscripcion.PAGO,
                "Pago " + estadoPago + " por " + precio.total() + " " + configuracion().getMoneda() + ".", actor());
        return precioDto(precio);
    }

    private void cambiarComercial(Suscripcion suscripcion, CambioComercialRequest request) {
        if (request == null) {
            throw new ReglaSuscripcionException("Faltan los datos del cambio.");
        }
        if (suscripcion.getEstado() == EstadoSuscripcion.CANCELADA) {
            throw new ReglaSuscripcionException("La suscripción está cancelada.");
        }
        PlanSuscripcion plan = texto(request.planCodigo) == null
                ? suscripcion.getPlan()
                : requerirPlanActivo(request.planCodigo);
        UsoOrganizacion uso = access.uso(suscripcion.getOrganizacion().getId());
        int cupo = request.profesionalesContratados == null
                ? suscripcion.getProfesionalesContratados()
                : request.profesionalesContratados;
        if (plan.getPrecioPorProfesional() != null && plan.getPrecioPorProfesional().signum() > 0) {
            cupo = Math.max(cupo, (int) uso.medicos());
        }
        LimitesPlan.validarCabeEnPlan(plan, uso, cupo);
        if (plan.getPrecioPorProfesional() != null && plan.getPrecioPorProfesional().signum() > 0) {
            int minimo = plan.getMinProfesionales() == null ? 1 : plan.getMinProfesionales();
            cupo = Math.max(cupo, minimo);
        }
        BigDecimal netoAntes = precioDe(suscripcion, CicloFacturacion.MENSUAL).netoMensual();
        String planAnterior = suscripcion.getPlan().getCodigo();
        suscripcion.setPlan(plan);
        suscripcion.setProfesionalesContratados(cupo);
        if (texto(request.ciclo) != null) {
            suscripcion.setCiclo(ciclo(request.ciclo));
        }
        if (request.addons != null) {
            guardarAddons(suscripcion, resolverAddons(request.addons));
        }
        suscripcionRepository.save(suscripcion);
        BigDecimal netoDespues = precioDe(suscripcion, CicloFacturacion.MENSUAL).netoMensual();
        if (suscripcion.getEstado() == EstadoSuscripcion.ACTIVA && !suscripcion.isHeredada()
                && netoDespues.compareTo(netoAntes) > 0) {
            cobrarDiferencia(suscripcion, netoDespues.subtract(netoAntes));
        } else {
            actualizarMontoPasarela(suscripcion);
        }
        access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PLAN,
                "Plan " + planAnterior + " → " + plan.getCodigo() + ", cupo " + cupo + ".", actor());
    }

    private void cobrarDiferencia(Suscripcion suscripcion, BigDecimal diferenciaNeta) {
        ConfiguracionSaas config = configuracion();
        BigDecimal iva = diferenciaNeta.multiply(config.getIvaPorcentaje()).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal total = diferenciaNeta.setScale(2, RoundingMode.HALF_UP).add(iva);
        SolicitudPreapproval solicitud = new SolicitudPreapproval(
                suscripcion.getId(),
                "suscripcion-" + suscripcion.getId(),
                idPlanExterno(suscripcion.getPlan(), CicloFacturacion.MENSUAL),
                "Diferencia de plan CheTurno",
                suscripcion.getOrganizacion().getEmailContacto(),
                CicloFacturacion.MENSUAL,
                total,
                config.getMoneda());
        String paymentId = null;
        try {
            ResultadoPreapproval resultado = pasarelaPago.crearPreapproval(solicitud);
            paymentId = resultado.paymentId();
        } catch (PasarelaNoImplementadaException e) {
            access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PASARELA, e.getMessage(), actor());
            return;
        }
        guardarPago(suscripcion, diferenciaNeta.setScale(2, RoundingMode.HALF_UP), iva, total, EstadoPago.APROBADO,
                TipoMovimientoPago.CAMBIO_PLAN, CicloFacturacion.MENSUAL, paymentId,
                "Diferencia por cambio de plan");
        actualizarMontoPasarela(suscripcion);
    }

    private void actualizarMontoPasarela(Suscripcion suscripcion) {
        if (suscripcion.getMpPreapprovalId() == null) {
            return;
        }
        PrecioCalculado precio = precioDe(suscripcion, suscripcion.getCiclo());
        SolicitudPreapproval solicitud = new SolicitudPreapproval(
                suscripcion.getId(),
                "suscripcion-" + suscripcion.getId(),
                idPlanExterno(suscripcion.getPlan(), suscripcion.getCiclo()),
                "CheTurno " + suscripcion.getPlan().getNombre(),
                suscripcion.getOrganizacion().getEmailContacto(),
                suscripcion.getCiclo(),
                precio.total(),
                configuracion().getMoneda());
        try {
            pasarelaPago.actualizarMonto(suscripcion.getMpPreapprovalId(), solicitud);
        } catch (PasarelaNoImplementadaException e) {
            access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PASARELA, e.getMessage(), actor());
        }
    }

    private String asegurarPlanExterno(PlanSuscripcion plan, CicloFacturacion ciclo, BigDecimal total) {
        String existente = idPlanExterno(plan, ciclo);
        try {
            ResultadoPlanExterno resultado = pasarelaPago.asegurarPlan(new SolicitudPlanExterno(
                    plan.getCodigo(), plan.getNombre(), ciclo, total, configuracion().getMoneda(), existente));
            if (ciclo == CicloFacturacion.ANUAL) {
                plan.setMpPreapprovalPlanIdAnual(resultado.preapprovalPlanId());
            } else {
                plan.setMpPreapprovalPlanIdMensual(resultado.preapprovalPlanId());
            }
            planRepository.save(plan);
            return resultado.preapprovalPlanId();
        } catch (PasarelaNoImplementadaException e) {
            throw new ReglaSuscripcionException(e.getMessage());
        }
    }

    private String idPlanExterno(PlanSuscripcion plan, CicloFacturacion ciclo) {
        if (ciclo == CicloFacturacion.ANUAL) {
            return plan.getMpPreapprovalPlanIdAnual();
        }
        return plan.getMpPreapprovalPlanIdMensual();
    }

    private void abrirPeriodoSiCorresponde(Suscripcion suscripcion, CicloFacturacion ciclo) {
        LocalDateTime fin = suscripcion.getFechaFinPeriodo();
        if (suscripcion.getEstado() == EstadoSuscripcion.ACTIVA && fin != null && fin.isAfter(RelojArgentina.ahora())) {
            return;
        }
        abrirPeriodo(suscripcion, ciclo == null ? CicloFacturacion.MENSUAL : ciclo);
    }

    private void abrirPeriodo(Suscripcion suscripcion, CicloFacturacion ciclo) {
        LocalDateTime ahora = RelojArgentina.ahora();
        LocalDateTime finAnterior = suscripcion.getFechaFinPeriodo();
        if (suscripcion.getEstado() == EstadoSuscripcion.ACTIVA && finAnterior != null && finAnterior.isAfter(ahora)) {
            suscripcion.setFechaFinPeriodo(ciclo == CicloFacturacion.ANUAL
                    ? finAnterior.plusYears(1)
                    : finAnterior.plusMonths(1));
        } else {
            suscripcion.setFechaInicioPeriodo(ahora);
            suscripcion.setFechaFinPeriodo(ciclo == CicloFacturacion.ANUAL ? ahora.plusYears(1) : ahora.plusMonths(1));
        }
        suscripcion.setCiclo(ciclo);
    }

    private void guardarPago(Suscripcion suscripcion, BigDecimal neto, BigDecimal iva, BigDecimal total,
            EstadoPago estado, TipoMovimientoPago tipo, CicloFacturacion ciclo, String mpPaymentId, String descripcion) {
        PagoSuscripcion pago = new PagoSuscripcion();
        pago.setSuscripcion(suscripcion);
        pago.setNeto(neto);
        pago.setIva(iva);
        pago.setTotal(total);
        pago.setMoneda(configuracion().getMoneda());
        pago.setEstado(estado);
        pago.setTipo(tipo);
        pago.setCiclo(ciclo);
        pago.setMpPaymentId(mpPaymentId);
        pago.setReferencia("suscripcion-" + suscripcion.getId());
        pago.setFecha(RelojArgentina.ahora());
        pago.setDescripcion(descripcion);
        pago = pagoRepository.save(pago);
        if (estado == EstadoPago.APROBADO) {
            FacturaSuscripcion factura = new FacturaSuscripcion();
            factura.setNumero("FC-" + RelojArgentina.ahora().getYear() + "-" + String.format("%06d", pago.getId()));
            factura.setSuscripcion(suscripcion);
            factura.setPago(pago);
            factura.setNeto(neto);
            factura.setIva(iva);
            factura.setTotal(total);
            factura.setMoneda(pago.getMoneda());
            factura.setFechaEmision(pago.getFecha());
            factura.setPeriodoDesde(suscripcion.getFechaInicioPeriodo());
            factura.setPeriodoHasta(suscripcion.getFechaFinPeriodo());
            factura.setEstado("PAGADA");
            facturaRepository.save(factura);
        }
    }

    private void guardarAddons(Suscripcion suscripcion, List<AddonMensajeria> addons) {
        List<SuscripcionAddon> actuales = suscripcionAddonRepository.findBySuscripcion_Id(suscripcion.getId());
        for (SuscripcionAddon actual : actuales) {
            actual.setActivo(false);
        }
        suscripcionAddonRepository.saveAll(actuales);
        for (AddonMensajeria addon : addons) {
            SuscripcionAddon vinculo = actuales.stream()
                    .filter(item -> item.getAddon().getId().equals(addon.getId()))
                    .findFirst()
                    .orElseGet(SuscripcionAddon::new);
            vinculo.setSuscripcion(suscripcion);
            vinculo.setAddon(addon);
            vinculo.setActivo(true);
            suscripcionAddonRepository.save(vinculo);
        }
    }

    private Suscripcion localizar(EventoPasarela evento) {
        if (evento.recursoId() != null && evento.tipo() == TipoNotificacionPasarela.PREAPPROVAL) {
            Suscripcion porId = suscripcionRepository.findByMpPreapprovalId(evento.recursoId()).orElse(null);
            if (porId != null) {
                return porId;
            }
        }
        if (evento.externalReference() != null && evento.externalReference().startsWith("suscripcion-")) {
            try {
                Integer id = Integer.valueOf(evento.externalReference().substring("suscripcion-".length()));
                return suscripcionRepository.findById(id).orElse(null);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void tocarPasarela(Suscripcion suscripcion, boolean pausar) {
        if (suscripcion.getMpPreapprovalId() == null) {
            return;
        }
        try {
            ResultadoPreapproval resultado = pausar
                    ? pasarelaPago.pausar(suscripcion.getMpPreapprovalId())
                    : pasarelaPago.reactivar(suscripcion.getMpPreapprovalId());
            suscripcion.setMpStatus(resultado.estadoMp());
            suscripcionRepository.save(suscripcion);
        } catch (PasarelaNoImplementadaException e) {
            access.registrar(suscripcion, TipoEventoSuscripcion.CAMBIO_PASARELA, e.getMessage(), actor());
        }
    }

    private void sincronizarTodas() {
        for (Suscripcion suscripcion : suscripcionRepository.findAll()) {
            access.sincronizar(suscripcion);
        }
    }

    private SuscripcionDetalle detalle(Suscripcion suscripcion) {
        ConfiguracionSaas config = configuracion();
        SuscripcionDetalle dto = new SuscripcionDetalle();
        dto.id = suscripcion.getId();
        dto.estado = suscripcion.getEstado().name();
        dto.soloLectura = !suscripcion.getEstado().permiteEscribir();
        dto.heredada = suscripcion.isHeredada();
        dto.soportePrioritario = suscripcion.getPlan().isSoportePrioritario();
        dto.ciclo = suscripcion.getCiclo().name();
        dto.profesionalesContratados = suscripcion.getProfesionalesContratados();
        dto.fechaInicio = fecha(suscripcion.getFechaInicio());
        dto.fechaFinTrial = fecha(suscripcion.getFechaFinTrial());
        dto.fechaInicioPeriodo = fecha(suscripcion.getFechaInicioPeriodo());
        dto.fechaFinPeriodo = fecha(suscripcion.getFechaFinPeriodo());
        dto.mpPreapprovalId = suscripcion.getMpPreapprovalId();
        dto.mpStatus = suscripcion.getMpStatus();
        dto.motivo = suscripcion.getMotivo();
        if (suscripcion.getEstado() == EstadoSuscripcion.TRIAL && suscripcion.getFechaFinTrial() != null) {
            long dias = ChronoUnit.DAYS.between(RelojArgentina.ahora().toLocalDate(),
                    suscripcion.getFechaFinTrial().toLocalDate());
            dto.diasTrialRestantes = Math.max(0, dias);
        }
        OrganizacionDTO organizacion = new OrganizacionDTO();
        organizacion.id = suscripcion.getOrganizacion().getId();
        organizacion.nombre = suscripcion.getOrganizacion().getNombre();
        organizacion.cuit = suscripcion.getOrganizacion().getCuit();
        organizacion.emailContacto = suscripcion.getOrganizacion().getEmailContacto();
        organizacion.telefono = suscripcion.getOrganizacion().getTelefono();
        dto.organizacion = organizacion;
        dto.plan = armarPlan(suscripcion.getPlan(), config, true);
        dto.uso = usoDto(suscripcion);
        dto.precioMensual = precioDto(precioDe(suscripcion, CicloFacturacion.MENSUAL));
        dto.precioAnual = precioDto(precioDe(suscripcion, CicloFacturacion.ANUAL));
        dto.precioVigente = suscripcion.getCiclo() == CicloFacturacion.ANUAL ? dto.precioAnual : dto.precioMensual;
        dto.addons = suscripcionAddonRepository.findBySuscripcion_IdAndActivoTrue(suscripcion.getId()).stream()
                .map(item -> item.getAddon().getCodigo())
                .toList();
        dto.centros = centroAtencionRepository.findByOrganizacion_Id(suscripcion.getOrganizacion().getId()).stream()
                .map(this::centroDto)
                .toList();
        dto.pagos = pagoRepository.findBySuscripcion_IdOrderByFechaDesc(suscripcion.getId()).stream()
                .limit(20)
                .map(this::pagoDto)
                .toList();
        dto.eventos = eventoRepository.findBySuscripcion_IdOrderByFechaDesc(suscripcion.getId()).stream()
                .limit(20)
                .map(this::eventoDto)
                .toList();
        return dto;
    }

    private UsoDTO usoDto(Suscripcion suscripcion) {
        UsoOrganizacion uso = access.uso(suscripcion.getOrganizacion().getId());
        PlanSuscripcion plan = suscripcion.getPlan();
        UsoDTO dto = new UsoDTO();
        dto.medicos = uso.medicos();
        dto.operadores = uso.operadores();
        dto.consultorios = uso.consultorios();
        dto.centros = uso.centros();
        dto.especialidades = uso.especialidades();
        dto.maxMedicos = plan.getMaxMedicos();
        dto.maxOperadores = plan.getMaxOperadores();
        dto.maxConsultorios = plan.getMaxConsultorios();
        dto.maxCentros = plan.getMaxCentros();
        dto.maxEspecialidades = plan.getMaxEspecialidades();
        dto.minProfesionales = plan.getMinProfesionales();
        dto.maxProfesionales = plan.getMaxProfesionales();
        dto.sedesIncluidas = plan.getSedesIncluidas() == null ? 0 : plan.getSedesIncluidas();
        dto.sedesExtra = (int) Math.max(0, uso.centros() - dto.sedesIncluidas);
        return dto;
    }

    private PrecioCalculado precioDe(Suscripcion suscripcion, CicloFacturacion ciclo) {
        int sedes = (int) centroAtencionRepository.countByOrganizacion_Id(suscripcion.getOrganizacion().getId());
        if (sedes < 1) {
            sedes = 1;
        }
        List<BigDecimal> addons = suscripcionAddonRepository.findBySuscripcion_IdAndActivoTrue(suscripcion.getId())
                .stream()
                .map(item -> item.getAddon().getPrecioMensualNeto())
                .toList();
        return CalculadoraPrecioSaas.calcular(
                suscripcion.getPlan(),
                configuracion(),
                suscripcion.getProfesionalesContratados(),
                sedes,
                addons,
                ciclo);
    }

    private PlanDTO armarPlan(PlanSuscripcion plan, ConfiguracionSaas config, boolean incluirInactivos) {
        PlanDTO dto = new PlanDTO();
        dto.id = plan.getId();
        dto.codigo = plan.getCodigo();
        dto.nombre = plan.getNombre();
        dto.descripcion = plan.getDescripcion();
        dto.precioBaseMensual = plan.getPrecioBaseMensual();
        dto.precioPorProfesional = plan.getPrecioPorProfesional();
        dto.minProfesionales = plan.getMinProfesionales();
        dto.maxProfesionales = plan.getMaxProfesionales();
        dto.maxMedicos = plan.getMaxMedicos();
        dto.maxOperadores = plan.getMaxOperadores();
        dto.maxConsultorios = plan.getMaxConsultorios();
        dto.maxEspecialidades = plan.getMaxEspecialidades();
        dto.maxCentros = plan.getMaxCentros();
        dto.sedesIncluidas = plan.getSedesIncluidas();
        dto.precioPorSedeExtra = plan.getPrecioPorSedeExtra();
        dto.descuentoAnualPorcentaje = plan.getDescuentoAnualPorcentaje();
        dto.reportesBasicos = plan.isReportesBasicos();
        dto.reportesConsolidados = plan.isReportesConsolidados();
        dto.soportePrioritario = plan.isSoportePrioritario();
        dto.activo = plan.isActivo();
        dto.orden = plan.getOrden();
        if (incluirInactivos) {
            dto.mpPreapprovalPlanIdMensual = plan.getMpPreapprovalPlanIdMensual();
            dto.mpPreapprovalPlanIdAnual = plan.getMpPreapprovalPlanIdAnual();
        }
        boolean porAsiento = plan.getPrecioPorProfesional() != null && plan.getPrecioPorProfesional().signum() > 0;
        dto.etiquetaPrecio = porAsiento ? "por profesional + IVA" : "+ IVA";
        int seats = porAsiento
                ? (plan.getMinProfesionales() == null ? 1 : plan.getMinProfesionales())
                : 1;
        dto.ejemploMensual = precioDto(CalculadoraPrecioSaas.calcular(
                plan, config, seats, 1, List.of(), CicloFacturacion.MENSUAL));
        dto.ejemploAnual = precioDto(CalculadoraPrecioSaas.calcular(
                plan, config, seats, 1, List.of(), CicloFacturacion.ANUAL));
        dto.incluye = descripcionIncluye(plan);
        return dto;
    }

    private List<String> descripcionIncluye(PlanSuscripcion plan) {
        List<String> items = new ArrayList<>();
        if (plan.getPrecioPorProfesional() != null && plan.getPrecioPorProfesional().signum() > 0) {
            items.add("De " + plan.getMinProfesionales() + " a " + plan.getMaxProfesionales() + " profesionales");
        } else if (plan.getMaxMedicos() == null) {
            items.add("Médicos sin tope");
        } else {
            items.add(plan.getMaxMedicos() + (plan.getMaxMedicos() == 1 ? " médico" : " médicos"));
        }
        if (plan.getMaxOperadores() == null) {
            items.add("Operadores sin tope");
        } else {
            items.add("Hasta " + plan.getMaxOperadores() + (plan.getMaxOperadores() == 1 ? " operador" : " operadores"));
        }
        if (plan.getMaxCentros() != null && plan.getMaxCentros() == 1) {
            items.add("1 sede");
        } else if (plan.getSedesIncluidas() != null && plan.getSedesIncluidas() > 1) {
            items.add(plan.getSedesIncluidas() + " sedes incluidas");
        }
        if (plan.getPrecioPorSedeExtra() != null) {
            items.add("Sede adicional con precio aparte");
        }
        if (plan.getMaxConsultorios() != null) {
            items.add("Hasta " + plan.getMaxConsultorios() + " consultorio");
        }
        if (plan.isReportesBasicos()) {
            items.add("Reportes");
        }
        if (plan.isReportesConsolidados()) {
            items.add("Reportes consolidados de la red");
        }
        if (plan.isSoportePrioritario()) {
            items.add("Soporte prioritario");
        }
        items.add("Prueba sin tarjeta");
        return items;
    }

    private AddonDTO armarAddon(AddonMensajeria addon, ConfiguracionSaas config) {
        AddonDTO dto = new AddonDTO();
        dto.id = addon.getId();
        dto.codigo = addon.getCodigo();
        dto.nombre = addon.getNombre();
        dto.descripcion = addon.getDescripcion();
        dto.precioMensualNeto = addon.getPrecioMensualNeto();
        dto.precioMensualConIva = CalculadoraPrecioSaas.conIva(addon.getPrecioMensualNeto(), config.getIvaPorcentaje());
        dto.activo = addon.isActivo();
        dto.placeholder = addon.isPlaceholder();
        return dto;
    }

    private PrecioDTO precioDto(PrecioCalculado precio) {
        PrecioDTO dto = new PrecioDTO();
        dto.ciclo = precio.ciclo().name();
        dto.netoMensual = precio.netoMensual();
        dto.neto = precio.netoPeriodo();
        dto.iva = precio.iva();
        dto.total = precio.total();
        dto.profesionalesFacturados = precio.profesionalesFacturados();
        dto.sedesExtra = precio.sedesExtra();
        dto.ivaPorcentaje = precio.ivaPorcentaje();
        dto.descuentoAnualPorcentaje = precio.descuentoAnualPorcentaje();
        return dto;
    }

    private PagoDTO pagoDto(PagoSuscripcion pago) {
        PagoDTO dto = new PagoDTO();
        dto.id = pago.getId();
        dto.fecha = fecha(pago.getFecha());
        dto.estado = pago.getEstado().name();
        dto.tipo = pago.getTipo().name();
        dto.ciclo = pago.getCiclo().name();
        dto.neto = pago.getNeto();
        dto.iva = pago.getIva();
        dto.total = pago.getTotal();
        dto.moneda = pago.getMoneda();
        dto.mpPaymentId = pago.getMpPaymentId();
        dto.descripcion = pago.getDescripcion();
        if (pago.getSuscripcion() != null && pago.getSuscripcion().getOrganizacion() != null) {
            dto.organizacion = pago.getSuscripcion().getOrganizacion().getNombre();
            dto.plan = pago.getSuscripcion().getPlan().getCodigo();
        }
        return dto;
    }

    private EventoDTO eventoDto(SuscripcionEvento evento) {
        EventoDTO dto = new EventoDTO();
        dto.fecha = fecha(evento.getFecha());
        dto.tipo = evento.getTipo().name();
        dto.detalle = evento.getDetalle();
        dto.actor = evento.getActor();
        return dto;
    }

    private CentroDTO centroDto(CentroAtencion centro) {
        CentroDTO dto = new CentroDTO();
        dto.id = centro.getId();
        dto.nombre = centro.getNombre();
        dto.localidad = centro.getLocalidad();
        dto.provincia = centro.getProvincia();
        return dto;
    }

    private ConfiguracionDTO configuracionDto(ConfiguracionSaas config) {
        ConfiguracionDTO dto = new ConfiguracionDTO();
        dto.ivaPorcentaje = config.getIvaPorcentaje();
        dto.diasTrial = config.getDiasTrial();
        dto.descuentoAnualPorcentaje = config.getDescuentoAnualPorcentaje();
        dto.moneda = config.getMoneda();
        dto.proveedorPago = pasarelaPago.proveedor();
        return dto;
    }

    private ConfiguracionSaas configuracion() {
        return configuracionRepository.findById(ConfiguracionSaas.ID).orElseGet(() -> {
            ConfiguracionSaas creada = new ConfiguracionSaas();
            creada.setId(ConfiguracionSaas.ID);
            creada.setIvaPorcentaje(new BigDecimal("21.00"));
            creada.setDiasTrial(30);
            creada.setDescuentoAnualPorcentaje(new BigDecimal("15.00"));
            creada.setMoneda("ARS");
            return configuracionRepository.save(creada);
        });
    }

    private PlanSuscripcion requerirPlanActivo(String codigo) {
        if (texto(codigo) == null) {
            throw new ReglaSuscripcionException("Elegí un plan.");
        }
        PlanSuscripcion plan = planRepository.findByCodigo(codigo.trim().toUpperCase())
                .orElseThrow(() -> new ReglaSuscripcionException("El plan " + codigo + " no existe."));
        if (!plan.isActivo()) {
            throw new ReglaSuscripcionException("El plan " + plan.getNombre() + " no está disponible.");
        }
        return plan;
    }

    private Suscripcion requerir(Integer id) {
        return suscripcionRepository.findById(id)
                .orElseThrow(() -> new ReglaSuscripcionException("Suscripción no encontrada."));
    }

    private List<AddonMensajeria> resolverAddons(List<String> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return List.of();
        }
        List<AddonMensajeria> addons = new ArrayList<>();
        for (String codigo : codigos) {
            if (texto(codigo) == null) {
                continue;
            }
            AddonMensajeria addon = addonRepository.findByCodigo(codigo.trim().toUpperCase())
                    .orElseThrow(() -> new ReglaSuscripcionException("Complemento desconocido: " + codigo));
            if (!addon.isActivo()) {
                throw new ReglaSuscripcionException("El complemento " + addon.getNombre() + " no está disponible.");
            }
            addons.add(addon);
        }
        return addons;
    }

    private List<BigDecimal> importesAddons(List<AddonMensajeria> addons) {
        return addons.stream().map(AddonMensajeria::getPrecioMensualNeto).toList();
    }

    private int cupoInicial(PlanSuscripcion plan, Integer pedido) {
        if (plan.getPrecioPorProfesional() != null && plan.getPrecioPorProfesional().signum() > 0) {
            int minimo = plan.getMinProfesionales() == null ? 1 : plan.getMinProfesionales();
            int maximo = plan.getMaxProfesionales() == null ? Integer.MAX_VALUE : plan.getMaxProfesionales();
            int seats = pedido == null ? minimo : pedido;
            if (seats < minimo || seats > maximo) {
                throw new ReglaSuscripcionException(
                        "El plan " + plan.getNombre() + " se contrata de " + minimo + " a " + maximo + " profesionales.");
            }
            return seats;
        }
        return 1;
    }

    private CicloFacturacion ciclo(String raw) {
        if (texto(raw) == null) {
            return CicloFacturacion.MENSUAL;
        }
        try {
            return CicloFacturacion.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaSuscripcionException("El ciclo de facturación tiene que ser MENSUAL o ANUAL.");
        }
    }

    private void validarCoordenadas(Double latitud, Double longitud) {
        if (latitud == null || longitud == null || latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw new ReglaSuscripcionException("Las coordenadas del centro no son válidas.");
        }
    }

    private void exigirNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor.signum() < 0) {
            throw new ReglaSuscripcionException(etiqueta + " no puede ser negativo.");
        }
    }

    private void exigirPorcentaje(BigDecimal valor, String etiqueta) {
        if (valor.signum() < 0 || valor.compareTo(CIEN) > 0) {
            throw new ReglaSuscripcionException(etiqueta + " tiene que estar entre 0 y 100.");
        }
    }

    private String requerido(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaSuscripcionException(etiqueta + " es obligatorio.");
        }
        return valor.trim();
    }

    private String texto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String fecha(LocalDateTime valor) {
        return valor == null ? null : valor.format(FECHA);
    }

    private String actor() {
        String actual = AuditContext.getCurrentUser();
        return actual == null || actual.isBlank() ? "sistema" : actual;
    }
}
