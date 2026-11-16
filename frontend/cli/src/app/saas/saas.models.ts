export interface ConfiguracionSaas {
  ivaPorcentaje: number;
  diasTrial: number;
  descuentoAnualPorcentaje: number;
  moneda: string;
  proveedorPago: string;
}

export interface PrecioSaas {
  ciclo: string;
  netoMensual: number;
  neto: number;
  iva: number;
  total: number;
  profesionalesFacturados: number;
  sedesExtra: number;
  ivaPorcentaje: number;
  descuentoAnualPorcentaje: number;
}

export interface PlanSaas {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  precioBaseMensual: number;
  precioPorProfesional: number;
  minProfesionales: number | null;
  maxProfesionales: number | null;
  maxMedicos: number | null;
  maxOperadores: number | null;
  maxConsultorios: number | null;
  maxEspecialidades: number | null;
  maxCentros: number | null;
  sedesIncluidas: number;
  precioPorSedeExtra: number | null;
  descuentoAnualPorcentaje: number | null;
  reportesBasicos: boolean;
  reportesConsolidados: boolean;
  soportePrioritario: boolean;
  activo: boolean;
  orden: number;
  mpPreapprovalPlanIdMensual?: string | null;
  mpPreapprovalPlanIdAnual?: string | null;
  etiquetaPrecio: string;
  ejemploMensual: PrecioSaas;
  ejemploAnual: PrecioSaas;
  incluye: string[];
}

export interface AddonSaas {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  precioMensualNeto: number;
  precioMensualConIva: number;
  activo: boolean;
  placeholder: boolean;
}

export interface CatalogoSaas {
  configuracion: ConfiguracionSaas;
  planes: PlanSaas[];
  addons: AddonSaas[];
}

export interface UsoSaas {
  medicos: number;
  operadores: number;
  consultorios: number;
  centros: number;
  especialidades: number;
  maxMedicos: number | null;
  maxOperadores: number | null;
  maxConsultorios: number | null;
  maxCentros: number | null;
  maxEspecialidades: number | null;
  minProfesionales: number | null;
  maxProfesionales: number | null;
  sedesIncluidas: number;
  sedesExtra: number;
}

export interface PagoSaas {
  id: number;
  fecha: string;
  estado: string;
  tipo: string;
  ciclo: string;
  neto: number;
  iva: number;
  total: number;
  moneda: string;
  mpPaymentId: string;
  descripcion: string;
  organizacion?: string;
  plan?: string;
}

export interface SuscripcionSaas {
  id: number;
  estado: string;
  soloLectura: boolean;
  heredada: boolean;
  soportePrioritario: boolean;
  ciclo: string;
  profesionalesContratados: number;
  fechaInicio: string;
  fechaFinTrial: string | null;
  diasTrialRestantes: number | null;
  fechaInicioPeriodo: string | null;
  fechaFinPeriodo: string | null;
  mpPreapprovalId: string | null;
  mpStatus: string | null;
  motivo: string | null;
  organizacion: { id: number; nombre: string; cuit: string | null; emailContacto: string; telefono: string };
  plan: PlanSaas;
  uso: UsoSaas;
  precioVigente: PrecioSaas;
  precioMensual: PrecioSaas;
  precioAnual: PrecioSaas;
  addons: string[];
  centros: { id: number; nombre: string; localidad: string; provincia: string }[];
  pagos: PagoSaas[];
  eventos: { fecha: string; tipo: string; detalle: string; actor: string }[];
}

export interface ResumenSaas {
  activas: number;
  trials: number;
  trialsPorVencer: number;
  vencidas: number;
  morosas: number;
  suspendidas: number;
  canceladas: number;
  heredadasActivas: number;
  mrrNeto: number;
  ingresosNetosMes: number;
  ingresosConIvaMes: number;
  moneda: string;
  porPlan: { codigo: string; nombre: string; cantidad: number; mrrNeto: number }[];
  configuracion: ConfiguracionSaas;
}

export interface RegistroSaasResultado {
  suscripcionId: number;
  organizacionId: number;
  centroId: number;
  email: string;
  estado: string;
  planCodigo: string;
  fechaFinTrial: string;
  diasTrial: number;
  soloLectura: boolean;
}

export function formatoArs(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || Number.isNaN(Number(valor))) {
    return '—';
  }
  return new Intl.NumberFormat('es-AR', {
    style: 'currency',
    currency: 'ARS',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  }).format(Number(valor));
}

export function formatoFecha(valor: string | null | undefined): string {
  if (!valor) {
    return '—';
  }
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) {
    return valor;
  }
  return new Intl.DateTimeFormat('es-AR', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(fecha);
}

export function formatoPorcentaje(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || Number.isNaN(Number(valor))) {
    return '—';
  }
  return new Intl.NumberFormat('es-AR', {
    maximumFractionDigits: 1
  }).format(Number(valor)) + '%';
}

export function etiquetaEstado(estado: string | null | undefined): string {
  switch (estado) {
    case 'TRIAL': return 'Prueba';
    case 'ACTIVA': return 'Activa';
    case 'VENCIDA': return 'Vencida';
    case 'MOROSA': return 'Morosa';
    case 'SUSPENDIDA': return 'Suspendida';
    case 'CANCELADA': return 'Cancelada';
    default: return estado || '—';
  }
}

export function claseEstado(estado: string | null | undefined): string {
  switch (estado) {
    case 'TRIAL': return 'trial';
    case 'ACTIVA': return 'activa';
    case 'VENCIDA': return 'vencida';
    case 'MOROSA': return 'morosa';
    case 'SUSPENDIDA': return 'suspendida';
    case 'CANCELADA': return 'cancelada';
    default: return 'neutro';
  }
}

export function etiquetaPago(estado: string | null | undefined): string {
  switch (estado) {
    case 'PENDIENTE': return 'Pendiente';
    case 'APROBADO': return 'Aprobado';
    case 'RECHAZADO': return 'Rechazado';
    case 'REEMBOLSADO': return 'Reembolsado';
    default: return estado || '—';
  }
}

export function etiquetaTipoPago(tipo: string | null | undefined): string {
  switch (tipo) {
    case 'ALTA': return 'Alta';
    case 'RENOVACION': return 'Renovación';
    case 'CAMBIO_PLAN': return 'Cambio de plan';
    default: return tipo || '—';
  }
}

export function etiquetaCiclo(ciclo: string | null | undefined): string {
  switch (ciclo) {
    case 'ANUAL': return 'Anual';
    case 'MENSUAL': return 'Mensual';
    default: return ciclo || '—';
  }
}

export function tope(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? 'Sin tope' : String(valor);
}

export function iconoPlan(codigo: string | null | undefined): string {
  switch (codigo) {
    case 'PROFESIONAL': return 'stethoscope';
    case 'CONSULTORIO': return 'groups';
    case 'CENTRO': return 'local_hospital';
    case 'RED': return 'account_tree';
    default: return 'sell';
  }
}

export function esPlanRecomendado(codigo: string | null | undefined): boolean {
  return codigo === 'CENTRO';
}

export function porcentajeUso(actual: number, max: number | null | undefined): number {
  if (max == null || max <= 0) {
    return 100;
  }
  return Math.max(0, Math.min(100, Math.round((actual / max) * 100)));
}

export function nivelUso(actual: number, max: number | null | undefined): 'ok' | 'alerta' | 'limite' | 'libre' {
  if (max == null) {
    return 'libre';
  }
  const porcentaje = porcentajeUso(actual, max);
  if (porcentaje >= 100) {
    return 'limite';
  }
  if (porcentaje >= 80) {
    return 'alerta';
  }
  return 'ok';
}
