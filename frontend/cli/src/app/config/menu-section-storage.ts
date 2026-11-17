/**
 * Preferencias de paneles abiertos del sidebar.
 * Viven en sessionStorage, una entrada por usuario, y se borran al cerrar sesión o volver a entrar.
 */
export const MENU_SECTION_STATE_PREFIX = 'cheturno.sidebar.sections.';

export function menuSectionStateKey(userKey: string): string {
  return MENU_SECTION_STATE_PREFIX + encodeURIComponent(userKey.trim().toLowerCase());
}

export function readMenuSectionState(
  storage: Storage | null | undefined,
  userKey: string
): Record<string, boolean> | null {
  if (!storage || !userKey.trim()) {
    return null;
  }

  try {
    const raw = storage.getItem(menuSectionStateKey(userKey));
    if (!raw) {
      return null;
    }

    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null;
    }

    const state: Record<string, boolean> = {};
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value === 'boolean') {
        state[key] = value;
      }
    }
    return state;
  } catch {
    return null;
  }
}

export function writeMenuSectionState(
  storage: Storage | null | undefined,
  userKey: string,
  state: Record<string, boolean>
): void {
  if (!storage || !userKey.trim()) {
    return;
  }
  storage.setItem(menuSectionStateKey(userKey), JSON.stringify(state));
}

export function clearAllMenuSectionState(storage: Storage | null | undefined): void {
  if (!storage) {
    return;
  }

  const keys: string[] = [];
  for (let index = 0; index < storage.length; index++) {
    const key = storage.key(index);
    if (key?.startsWith(MENU_SECTION_STATE_PREFIX)) {
      keys.push(key);
    }
  }
  for (const key of keys) {
    storage.removeItem(key);
  }
}
