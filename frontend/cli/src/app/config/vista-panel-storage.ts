/**
 * Vista activa del sidebar para quien tiene panel de staff y portal de paciente.
 * Vive en sessionStorage, una entrada por usuario, y se borra al cerrar sesión.
 */
export const VISTA_PANEL_PREFIX = 'cheturno.vista.';

export type VistaPanel = 'staff' | 'paciente';

export function vistaPanelKey(userKey: string): string {
  return VISTA_PANEL_PREFIX + encodeURIComponent(userKey.trim().toLowerCase());
}

export function readVistaPanel(
  storage: Storage | null | undefined,
  userKey: string
): VistaPanel {
  if (!storage || !userKey.trim()) {
    return 'staff';
  }
  try {
    return storage.getItem(vistaPanelKey(userKey)) === 'paciente' ? 'paciente' : 'staff';
  } catch {
    return 'staff';
  }
}

export function writeVistaPanel(
  storage: Storage | null | undefined,
  userKey: string,
  vista: VistaPanel
): void {
  if (!storage || !userKey.trim()) {
    return;
  }
  try {
    if (vista === 'staff') {
      storage.removeItem(vistaPanelKey(userKey));
      return;
    }
    storage.setItem(vistaPanelKey(userKey), vista);
  } catch {
    // sessionStorage puede estar bloqueado. La vista sigue en memoria.
  }
}

export function clearAllVistaPanel(storage: Storage | null | undefined): void {
  if (!storage) {
    return;
  }
  try {
    const keys: string[] = [];
    for (let index = 0; index < storage.length; index++) {
      const key = storage.key(index);
      if (key?.startsWith(VISTA_PANEL_PREFIX)) {
        keys.push(key);
      }
    }
    for (const key of keys) {
      storage.removeItem(key);
    }
  } catch {
    // Nada que limpiar si el storage no responde.
  }
}
