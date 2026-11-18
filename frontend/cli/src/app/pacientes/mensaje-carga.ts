/**
 * Texto para mostrar cuando una carga del portal de paciente falla.
 * Un 0 es el navegador sin respuesta: no hay status_text del backend.
 */
export function mensajeDeCarga(error: unknown, respaldo: string): string {
  const http = error as {
    status?: number;
    error?: { status_text?: unknown; message?: unknown };
  };
  const crudo = http?.error?.status_text ?? http?.error?.message;
  if (typeof crudo === 'string' && crudo.trim()) {
    return crudo.trim();
  }
  if (http?.status === 0) {
    return 'No hay conexión con el servidor. Reintentá.';
  }
  return respaldo;
}
