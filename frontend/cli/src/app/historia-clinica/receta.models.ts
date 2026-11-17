export interface CatalogoMedicamento {
  id: number;
  nombreGenerico: string;
  concentracion?: string;
  formaFarmaceutica?: string;
  via?: string;
  tipo: string;
  lista: string;
}

export interface RecetaItem {
  id?: number;
  orden?: number;
  nombreGenerico: string;
  marcaComercial?: string;
  concentracion?: string;
  formaFarmaceutica?: string;
  presentacion?: string;
  cantidad?: number | null;
  cantidadEnLetras?: string;
  dosis?: string;
  frecuencia?: string;
  duracion?: string;
  via?: string;
  indicaciones?: string;
  noSustituir: boolean;
  justificacionNoSustituir?: string;
  lista?: string;
  catalogoId?: number;
}

export interface Receta {
  id?: number;
  consultaId?: number;
  pacienteId?: number;
  medicoId?: number;
  centroId?: number;
  tipo: string;
  estado?: string;
  identificador?: string;
  cuir?: string;
  codigoVerificacion?: string;
  urlVerificacion?: string;
  hashContenido?: string;
  integridad?: boolean;
  tipoFirma?: string;
  fechaEmision?: string;
  fechaVencimiento?: string;
  diagnosticoCode?: string;
  diagnosticoDisplay?: string;
  pacienteNombre?: string;
  pacienteApellido?: string;
  pacienteDni?: number;
  pacienteSexo?: string;
  pacienteNacimiento?: string;
  obraSocialNombre?: string;
  numeroAfiliado?: string;
  prescriptorNombre?: string;
  prescriptorApellido?: string;
  matricula?: string;
  matriculaNacional?: string;
  matriculaProvincial?: string;
  refepsId?: string;
  especialidadNombre?: string;
  centroNombre?: string;
  motivoAnulacion?: string;
  anuladoPor?: string;
  anuladoEn?: string;
  items: RecetaItem[];
}

export interface OrdenItem {
  id?: number;
  orden?: number;
  nombrePractica: string;
  systemUri?: string;
  code?: string;
}

export interface OrdenEstudio {
  id?: number;
  consultaId?: number;
  estado?: string;
  urgencia: string;
  indicacionClinica?: string;
  identificador?: string;
  cuir?: string;
  urlVerificacion?: string;
  hashContenido?: string;
  integridad?: boolean;
  fechaEmision?: string;
  fechaVencimiento?: string;
  diagnosticoCode?: string;
  diagnosticoDisplay?: string;
  pacienteNombre?: string;
  pacienteApellido?: string;
  prescriptorNombre?: string;
  prescriptorApellido?: string;
  matricula?: string;
  centroNombre?: string;
  motivoAnulacion?: string;
  items: OrdenItem[];
}

export interface ItemVerificacion {
  nombre: string;
  concentracion?: string;
  forma?: string;
  cantidad?: number;
  cantidadEnLetras?: string;
  noSustituir?: boolean;
  codigo?: string;
}

export interface VerificacionPublica {
  tipoDocumento: string;
  estado: string;
  vigente: boolean;
  integridad: boolean;
  fechaEmision?: string;
  fechaVencimiento?: string;
  prescriptor?: string;
  matricula?: string;
  matriculaNacional?: string;
  matriculaProvincial?: string;
  pacienteIniciales?: string;
  dniParcial?: string;
  mensaje?: string;
  items: ItemVerificacion[];
}

export function itemVacio(): RecetaItem {
  return { nombreGenerico: '', noSustituir: false, via: 'oral', lista: 'NINGUNA' };
}
