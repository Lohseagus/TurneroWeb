package unpsjb.labprog.backend.saas;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Contratos JSON del módulo SaaS. Los importes son ARS.
 * neto es sin IVA; total ya incluye el IVA configurado.
 */
public final class SaasDtos {

    private SaasDtos() {
    }

    public static class CatalogoPublico {
        public ConfiguracionDTO configuracion;
        public List<PlanDTO> planes = new ArrayList<>();
        public List<AddonDTO> addons = new ArrayList<>();
    }

    public static class ConfiguracionDTO {
        public BigDecimal ivaPorcentaje;
        public int diasTrial;
        public BigDecimal descuentoAnualPorcentaje;
        public String moneda;
        public String proveedorPago;
    }

    public static class PlanDTO {
        public Integer id;
        public String codigo;
        public String nombre;
        public String descripcion;
        public BigDecimal precioBaseMensual;
        public BigDecimal precioPorProfesional;
        public Integer minProfesionales;
        public Integer maxProfesionales;
        public Integer maxMedicos;
        public Integer maxOperadores;
        public Integer maxConsultorios;
        public Integer maxEspecialidades;
        public Integer maxCentros;
        public Integer sedesIncluidas;
        public BigDecimal precioPorSedeExtra;
        public BigDecimal descuentoAnualPorcentaje;
        public boolean reportesBasicos;
        public boolean reportesConsolidados;
        public boolean soportePrioritario;
        public boolean activo;
        public int orden;
        public String mpPreapprovalPlanIdMensual;
        public String mpPreapprovalPlanIdAnual;
        public String etiquetaPrecio;
        public PrecioDTO ejemploMensual;
        public PrecioDTO ejemploAnual;
        public List<String> incluye = new ArrayList<>();
    }

    public static class AddonDTO {
        public Integer id;
        public String codigo;
        public String nombre;
        public String descripcion;
        public BigDecimal precioMensualNeto;
        public BigDecimal precioMensualConIva;
        public boolean activo;
        public boolean placeholder;
    }

    public static class PrecioDTO {
        public String ciclo;
        public BigDecimal netoMensual;
        public BigDecimal neto;
        public BigDecimal iva;
        public BigDecimal total;
        public int profesionalesFacturados;
        public int sedesExtra;
        public BigDecimal ivaPorcentaje;
        public BigDecimal descuentoAnualPorcentaje;
    }

    public static class CotizacionRequest {
        public String planCodigo;
        public String ciclo;
        public Integer profesionalesContratados;
        public Integer sedes;
        public List<String> addons;
    }

    public static class RegistroRequest {
        public String planCodigo;
        public String ciclo;
        public Integer profesionalesContratados;
        public List<String> addons;
        public String organizacionNombre;
        public String cuit;
        public String emailContacto;
        public String centroNombre;
        public String direccion;
        public String localidad;
        public String provincia;
        public String telefonoCentro;
        public Double latitud;
        public Double longitud;
        public String adminNombre;
        public String adminApellido;
        public String adminEmail;
        public String adminPassword;
        public Long adminDni;
        public String adminTelefono;
    }

    public static class AvisoSuscripcion {
        public boolean soloLectura;
        public boolean sinSuscripcion;
        public String estado;
        public String mensaje;
        public Long diasTrialRestantes;
    }

    public static class RegistroResponse {
        public Integer suscripcionId;
        public Integer organizacionId;
        public Integer centroId;
        public String email;
        public String estado;
        public String planCodigo;
        public String fechaFinTrial;
        public int diasTrial;
        public boolean soloLectura;
    }

    public static class SedeRequest {
        public String nombre;
        public String direccion;
        public String localidad;
        public String provincia;
        public String telefono;
        public Double latitud;
        public Double longitud;
    }

    public static class CambioComercialRequest {
        public String planCodigo;
        public String ciclo;
        public Integer profesionalesContratados;
        public List<String> addons;
    }

    public static class PagoRequest {
        public String ciclo;
    }

    public static class ExtensionTrialRequest {
        public int dias;
    }

    public static class MotivoRequest {
        public String motivo;
    }

    public static class VincularCentroRequest {
        public Integer centroId;
    }

    public static class SuscripcionDetalle {
        public Integer id;
        public String estado;
        public boolean soloLectura;
        public boolean heredada;
        public boolean soportePrioritario;
        public String ciclo;
        public int profesionalesContratados;
        public String fechaInicio;
        public String fechaFinTrial;
        public Long diasTrialRestantes;
        public String fechaInicioPeriodo;
        public String fechaFinPeriodo;
        public String mpPreapprovalId;
        public String mpStatus;
        public String motivo;
        public OrganizacionDTO organizacion;
        public PlanDTO plan;
        public UsoDTO uso;
        public PrecioDTO precioVigente;
        public PrecioDTO precioMensual;
        public PrecioDTO precioAnual;
        public List<String> addons = new ArrayList<>();
        public List<CentroDTO> centros = new ArrayList<>();
        public List<PagoDTO> pagos = new ArrayList<>();
        public List<EventoDTO> eventos = new ArrayList<>();
    }

    public static class OrganizacionDTO {
        public Integer id;
        public String nombre;
        public String cuit;
        public String emailContacto;
        public String telefono;
    }

    public static class CentroDTO {
        public Integer id;
        public String nombre;
        public String localidad;
        public String provincia;
    }

    public static class UsoDTO {
        public long medicos;
        public long operadores;
        public long consultorios;
        public long centros;
        public long especialidades;
        public Integer maxMedicos;
        public Integer maxOperadores;
        public Integer maxConsultorios;
        public Integer maxCentros;
        public Integer maxEspecialidades;
        public Integer minProfesionales;
        public Integer maxProfesionales;
        public int sedesIncluidas;
        public int sedesExtra;
    }

    public static class PagoDTO {
        public Integer id;
        public String fecha;
        public String estado;
        public String tipo;
        public String ciclo;
        public BigDecimal neto;
        public BigDecimal iva;
        public BigDecimal total;
        public String moneda;
        public String mpPaymentId;
        public String descripcion;
        public String organizacion;
        public String plan;
    }

    public static class EventoDTO {
        public String fecha;
        public String tipo;
        public String detalle;
        public String actor;
    }

    public static class ResumenSaas {
        public long activas;
        public long trials;
        public long trialsPorVencer;
        public long vencidas;
        public long morosas;
        public long suspendidas;
        public long canceladas;
        public long heredadasActivas;
        public BigDecimal mrrNeto;
        public BigDecimal ingresosNetosMes;
        public BigDecimal ingresosConIvaMes;
        public String moneda;
        public List<ConteoPlan> porPlan = new ArrayList<>();
        public ConfiguracionDTO configuracion;
    }

    public static class ConteoPlan {
        public String codigo;
        public String nombre;
        public long cantidad;
        public BigDecimal mrrNeto;
    }
}
