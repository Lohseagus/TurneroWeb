/** Textos y opciones de la pantalla de auditoría. Los valores viajan como los guarda el backend. */

export interface OpcionAuditoria {
  value: string;
  label: string;
}

export const ROLES_AUDITORIA: OpcionAuditoria[] = [
  { value: "SUPERADMIN", label: "Superadmin" },
  { value: "ADMINISTRADOR", label: "Administrador" },
  { value: "OPERADOR", label: "Operador" },
  { value: "MEDICO", label: "Médico" },
  { value: "PACIENTE", label: "Paciente" },
];

export const ORIGENES_AUDITORIA: OpcionAuditoria[] = [
  { value: "USUARIO", label: "Usuario" },
  { value: "SISTEMA", label: "Sistema" },
];

export const ENTIDADES_AUDITORIA: OpcionAuditoria[] = [
  { value: "TURNO", label: "Turno" },
  { value: "USUARIO", label: "Usuario" },
  { value: "OPERADOR", label: "Operador" },
  { value: "MEDICO", label: "Médico" },
  { value: "PACIENTE", label: "Paciente" },
  { value: "CONSULTORIO", label: "Consultorio" },
  { value: "CENTRO_ATENCION", label: "Centro de atención" },
  { value: "ESPECIALIDAD", label: "Especialidad" },
  { value: "LISTA_ESPERA", label: "Lista de espera" },
  { value: "FERIADO", label: "Feriado" },
];

export const ACCIONES_AUDITORIA: OpcionAuditoria[] = [
  { value: "CREATE", label: "Alta" },
  { value: "UPDATE", label: "Modificación" },
  { value: "UPDATE_STATUS", label: "Cambio de estado" },
  { value: "CANCEL", label: "Cancelación" },
  { value: "CANCELLED_AUTO", label: "Cancelación automática" },
  { value: "CONFIRM", label: "Confirmación" },
  { value: "RESCHEDULE", label: "Reprogramación" },
  { value: "COMPLETE", label: "Atención completa" },
  { value: "DELETE", label: "Baja" },
  { value: "LOGIN", label: "Ingreso" },
  { value: "LOGOUT", label: "Salida" },
];

export function etiquetaDe(opciones: OpcionAuditoria[], valor?: string | null): string {
  if (!valor) {
    return "";
  }
  return opciones.find((opcion) => opcion.value === valor)?.label ?? valor;
}

/** Una fila sin origen (anterior al relleno) se muestra como usuario, salvo actor SISTEMA. */
export function etiquetaOrigen(source?: string | null, performedBy?: string | null): string {
  if ((source || "").toUpperCase() === "SISTEMA" || (performedBy || "").trim().toUpperCase() === "SISTEMA") {
    return "Sistema";
  }
  return "Usuario";
}

export function esOrigenSistema(source?: string | null, performedBy?: string | null): boolean {
  return etiquetaOrigen(source, performedBy) === "Sistema";
}

export function contarFiltros(filtros: {
  rol?: string;
  usuario?: string;
  entidad?: string;
  accion?: string;
  origen?: string;
  desde?: string;
  hasta?: string;
}): number {
  return [filtros.rol, filtros.usuario, filtros.entidad, filtros.accion, filtros.origen, filtros.desde, filtros.hasta]
    .filter((valor) => !!valor && valor.trim() !== "").length;
}
