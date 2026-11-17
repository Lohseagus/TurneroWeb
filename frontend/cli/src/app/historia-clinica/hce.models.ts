export interface Cie10 {
  systemUri: string;
  code: string;
  display: string;
}

export interface AlergiaHce {
  id?: number;
  sustancia: string;
  reaccion?: string;
  criticidad?: string;
  systemUri?: string;
  code?: string;
  activa: boolean;
}

export interface MedicacionHce {
  id?: number;
  farmaco: string;
  dosis?: string;
  frecuencia?: string;
  systemUri?: string;
  code?: string;
  activa: boolean;
}

export interface VacunaHce {
  id?: number;
  nombre: string;
  fechaAplicacion?: string;
  dosis?: string;
  lote?: string;
}

export interface PerfilClinico {
  pacienteId: number;
  dni?: number;
  identificadorFederado?: string;
  nombre?: string;
  apellido?: string;
  antecedentesPersonales?: string;
  antecedentesFamiliares?: string;
  antecedentesQuirurgicos?: string;
  grupoSanguineo?: string;
  habitos?: string;
  comparteEntreCentros: boolean;
  alergias: AlergiaHce[];
  medicaciones: MedicacionHce[];
  vacunas: VacunaHce[];
}

export interface DiagnosticoHce {
  id?: number;
  systemUri?: string;
  code: string;
  display?: string;
  tipo?: string;
}

export interface EnmiendaHce {
  id?: number;
  consultaId?: number;
  texto: string;
  motivo: string;
  medicoId?: number;
  medicoNombre?: string;
  matricula?: string;
  creadaEn?: string;
}

export interface ConsultaHce {
  id?: number;
  pacienteId?: number;
  pacienteNombre?: string;
  pacienteApellido?: string;
  dni?: number;
  identificadorFederado?: string;
  turnoId?: number;
  medicoId?: number;
  medicoNombre?: string;
  matricula?: string;
  refepsId?: string;
  centroId?: number;
  centroNombre?: string;
  especialidadId?: number;
  especialidadNombre?: string;
  estado?: string;
  motivoConsulta?: string;
  anamnesis?: string;
  examenFisico?: string;
  indicaciones?: string;
  plan?: string;
  proximoControl?: string;
  tensionSistolica?: number;
  tensionDiastolica?: number;
  frecuenciaCardiaca?: number;
  temperatura?: number;
  pesoKg?: number;
  tallaCm?: number;
  imc?: number;
  diagnosticos: DiagnosticoHce[];
  enmiendas: EnmiendaHce[];
  creadaEn?: string;
  actualizadaEn?: string;
  cerradaEn?: string;
}

export interface TimelineItem {
  tipo: string;
  recursoId: string;
  consultaId?: number;
  fecha?: string;
  centroId?: number;
  centroNombre?: string;
  medicoId?: number;
  medicoNombre?: string;
  especialidadId?: number;
  especialidadNombre?: string;
  estado?: string;
  resumen?: string;
}

export interface AdjuntoHce {
  id: number;
  pacienteId: number;
  consultaId?: number;
  centroId?: number;
  nombreOriginal: string;
  contentType: string;
  tamanioBytes: number;
  tipo: string;
  subidoPor?: string;
  creadoEn?: string;
}

export interface PacienteResumenHce {
  id: number;
  nombre: string;
  apellido: string;
  dni?: number;
  consultas?: number;
  ultimaConsulta?: string;
}

export interface TurnoContextoHce {
  turnoId: number;
  pacienteId: number;
  pacienteNombre?: string;
  pacienteApellido?: string;
  dni?: number;
  centroId: number;
  centroNombre?: string;
  especialidadId?: number;
  especialidadNombre?: string;
  medicoId?: number;
  consultaId?: number;
}

export interface MetadataHce {
  pacienteId: number;
  tienePerfil: boolean;
  comparteEntreCentros: boolean;
  cantidadConsultas: number;
  cantidadBorradores: number;
  cantidadCerradas: number;
  cantidadAdjuntos: number;
  cantidadEnmiendas: number;
  cantidadRecetas?: number;
  cantidadOrdenes?: number;
  ultimaActividad?: string;
}

export const GRUPOS_SANGUINEOS = [
  { value: 'DESCONOCIDO', label: 'Desconocido' },
  { value: 'A_POSITIVO', label: 'A+' },
  { value: 'A_NEGATIVO', label: 'A-' },
  { value: 'B_POSITIVO', label: 'B+' },
  { value: 'B_NEGATIVO', label: 'B-' },
  { value: 'AB_POSITIVO', label: 'AB+' },
  { value: 'AB_NEGATIVO', label: 'AB-' },
  { value: 'O_POSITIVO', label: 'O+' },
  { value: 'O_NEGATIVO', label: 'O-' }
];
