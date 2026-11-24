/** Grilla mensual lunes a domingo, con etiquetas en español (Argentina). */

export const DIAS_CORTO = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"] as const;

export const MESES = [
  "enero",
  "febrero",
  "marzo",
  "abril",
  "mayo",
  "junio",
  "julio",
  "agosto",
  "septiembre",
  "octubre",
  "noviembre",
  "diciembre",
] as const;

/** Cuántos turnos se muestran en la celda. El resto se cuenta. */
export const PRIMEROS = 3;

export interface TurnoGrilla {
  id: number;
  fecha: string;
  horaInicio: string;
  horaFin?: string;
  estado: string;
  pacienteId?: number | null;
  pacienteNombre?: string;
  pacienteApellido?: string;
  medicoNombre?: string;
  medicoApellido?: string;
  especialidadNombre?: string;
  consultorioNombre?: string;
  centroNombre?: string;
  asistio?: boolean | null;
}

export interface FeriadoGrilla {
  id: number;
  fecha: string;
  fechaFin?: string | null;
  descripcion?: string | null;
  centroAtencionId?: number | null;
  centroAtencionNombre?: string | null;
}

export interface DiaGrilla {
  fecha: string;
  numero: number;
  esMesActual: boolean;
  esHoy: boolean;
  total: number;
  feriado: boolean;
  feriadoTexto: string | null;
  /** Los primeros, para la celda. */
  primeros: TurnoGrilla[];
  /** Todos, para la lista del día. */
  turnos: TurnoGrilla[];
}

export type RolCalendario = "paciente" | "medico" | "staff";

export interface AccionesTurno {
  confirmar: boolean;
  reagendar: boolean;
  cancelar: boolean;
  ver: boolean;
  consulta: boolean;
  historia: boolean;
  asistencia: boolean;
}

const SIN_ACCIONES: AccionesTurno = {
  confirmar: false,
  reagendar: false,
  cancelar: false,
  ver: false,
  consulta: false,
  historia: false,
  asistencia: false,
};

/** Acepta yyyy-MM-dd (pedido) y dd-MM-yyyy (formato global de Jackson). */
export function fechaIso(valor: unknown): string {
  const texto = String(valor ?? "");
  const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(texto);
  if (iso) {
    return `${iso[1]}-${iso[2]}-${iso[3]}`;
  }
  const dmy = /^(\d{2})-(\d{2})-(\d{4})/.exec(texto);
  if (dmy) {
    return `${dmy[3]}-${dmy[2]}-${dmy[1]}`;
  }
  return texto.slice(0, 10);
}

export function normalizarTurno(turno: TurnoGrilla): TurnoGrilla {
  return {
    ...turno,
    fecha: fechaIso(turno.fecha),
    horaInicio: String(turno.horaInicio ?? "").slice(0, 5),
  };
}

export function normalizarFeriado(feriado: FeriadoGrilla): FeriadoGrilla {
  return {
    ...feriado,
    fecha: fechaIso(feriado.fecha),
    fechaFin: feriado.fechaFin ? fechaIso(feriado.fechaFin) : null,
  };
}

export function isoDe(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, "0");
  const dia = String(fecha.getDate()).padStart(2, "0");
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

export function parseIso(iso: string): Date {
  const [anio, mes, dia] = iso.split("-").map(Number);
  return new Date(anio, (mes || 1) - 1, dia || 1);
}

export function tituloMes(anio: number, mes: number): string {
  return `${MESES[mes - 1]} ${anio}`;
}

export function etiquetaDia(iso: string): string {
  const fecha = parseIso(iso);
  const nombre = DIAS_CORTO[(fecha.getDay() + 6) % 7];
  return `${nombre} ${fecha.getDate()} de ${MESES[fecha.getMonth()]}`;
}

/** Lunes de la semana del día 1 hasta el domingo de la semana del último día. */
export function rangoMesVisible(anio: number, mes: number): { desde: string; hasta: string } {
  const primero = new Date(anio, mes - 1, 1);
  const inicio = new Date(anio, mes - 1, 1 - ((primero.getDay() + 6) % 7));
  const ultimo = new Date(anio, mes, 0);
  const fin = new Date(anio, mes - 1, ultimo.getDate() + (6 - ((ultimo.getDay() + 6) % 7)));
  return { desde: isoDe(inicio), hasta: isoDe(fin) };
}

export function armarGrilla(
  anio: number,
  mes: number,
  hoy: string,
  turnos: TurnoGrilla[],
  feriados: FeriadoGrilla[]
): DiaGrilla[] {
  const { desde, hasta } = rangoMesVisible(anio, mes);
  const porFecha = new Map<string, TurnoGrilla[]>();
  for (const turno of turnos) {
    const lista = porFecha.get(turno.fecha) ?? [];
    lista.push(turno);
    porFecha.set(turno.fecha, lista);
  }
  for (const lista of porFecha.values()) {
    lista.sort((a, b) => a.horaInicio.localeCompare(b.horaInicio) || a.id - b.id);
  }

  const dias: DiaGrilla[] = [];
  const cursor = parseIso(desde);
  const limite = parseIso(hasta);
  while (cursor.getTime() <= limite.getTime()) {
    const fecha = isoDe(cursor);
    const delDia = porFecha.get(fecha) ?? [];
    const marcas = feriados.filter((feriado) => cubre(feriado, fecha));
    dias.push({
      fecha,
      numero: cursor.getDate(),
      esMesActual: cursor.getMonth() === mes - 1,
      esHoy: fecha === hoy,
      total: delDia.length,
      feriado: marcas.length > 0,
      feriadoTexto: marcas.length
        ? marcas.map((feriado) => feriado.descripcion || "Feriado").join(" · ")
        : null,
      primeros: delDia.slice(0, PRIMEROS),
      turnos: delDia,
    });
    cursor.setDate(cursor.getDate() + 1);
  }
  return dias;
}

export function rolDe(primary: string | null | undefined): RolCalendario {
  switch ((primary ?? "").toUpperCase()) {
    case "MEDICO":
      return "medico";
    case "OPERADOR":
    case "ADMINISTRADOR":
    case "SUPERADMIN":
      return "staff";
    default:
      return "paciente";
  }
}

/**
 * Acciones que cada rol ya tiene en su pantalla de turnos.
 * El paciente mira el mes, sin confirmar ni cancelar desde acá.
 */
export function accionesDe(
  rol: RolCalendario,
  estado: string,
  fecha: string,
  hoy: string,
  pacienteId: number | null | undefined
): AccionesTurno {
  const estadoUp = (estado || "").toUpperCase();
  if (rol === "paciente") {
    return { ...SIN_ACCIONES };
  }
  if (rol === "medico") {
    const puedeAsistencia =
      fecha <= hoy && ["PROGRAMADO", "CONFIRMADO", "COMPLETO", "AUSENTE"].includes(estadoUp);
    return {
      ...SIN_ACCIONES,
      consulta: estadoUp !== "CANCELADO",
      historia: pacienteId != null,
      asistencia: puedeAsistencia,
    };
  }
  const activo = estadoUp === "PROGRAMADO" || estadoUp === "CONFIRMADO" || estadoUp === "REAGENDADO";
  return {
    ...SIN_ACCIONES,
    confirmar: estadoUp === "PROGRAMADO" || estadoUp === "REAGENDADO",
    reagendar: activo,
    cancelar: activo,
    ver: true,
  };
}

export function etiquetaEstado(estado: string): string {
  switch ((estado || "").toUpperCase()) {
    case "PROGRAMADO":
      return "Programado";
    case "CONFIRMADO":
      return "Confirmado";
    case "REAGENDADO":
      return "Reagendado";
    case "AUSENTE":
      return "Ausente";
    case "CANCELADO":
      return "Cancelado";
    case "COMPLETO":
      return "Completo";
    default:
      return estado || "";
  }
}

export function claseEstado(estado: string): string {
  const conocido = ["programado", "confirmado", "reagendado", "ausente", "cancelado", "completo"];
  const clave = (estado || "").toLowerCase();
  return conocido.includes(clave) ? `estado-${clave}` : "estado-otro";
}

function cubre(feriado: FeriadoGrilla, iso: string): boolean {
  const fin = feriado.fechaFin || feriado.fecha;
  return feriado.fecha <= iso && iso <= fin;
}
