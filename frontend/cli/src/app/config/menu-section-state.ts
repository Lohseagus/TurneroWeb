import { Role, ROLE_HIERARCHY } from '../inicio-sesion/auth.service';

/**
 * Privilegio de mayor a menor.
 *
 * ROLE_HIERARCHY dice qué roles incluye cada uno (herencia). No ordena a
 * OPERADOR y MEDICO entre sí: los dos heredan solo PACIENTE. Este orden es
 * el que usa el sidebar para elegir el único panel abierto al entrar.
 */
export const ROLE_PRIVILEGE_ORDER: readonly Role[] = [
  Role.SUPERADMIN,
  Role.ADMINISTRADOR,
  Role.OPERADOR,
  Role.MEDICO,
  Role.PACIENTE
];

export interface MenuSectionLike {
  id: string;
  role: Role | string;
  items: ReadonlyArray<{
    route?: string;
    divider?: boolean;
  }>;
}

export function normalizeMenuPath(url: string | null | undefined): string {
  const path = (url ?? '').split(/[?#]/)[0];
  if (!path) {
    return '/';
  }
  return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path;
}

/**
 * Misma regla que el resaltado del ítem activo: coincidencia exacta,
 * o un subpath. La raíz solo coincide con la raíz.
 */
export function routeMatches(currentUrl: string, itemRoute: string | undefined): boolean {
  if (!itemRoute) {
    return false;
  }

  const path = normalizeMenuPath(currentUrl);
  const target = normalizeMenuPath(itemRoute);
  if (target === '/') {
    return path === '/';
  }
  return path === target || path.startsWith(`${target}/`);
}

export function sectionContainsActiveRoute(section: MenuSectionLike, activeRoute: string): boolean {
  return section.items.some(item => !item.divider && routeMatches(activeRoute, item.route));
}

/**
 * Rol visible de mayor privilegio. Se mira la sección, no la lista cruda de roles,
 * así un panel filtrado (por ejemplo Médico para un administrador) no queda "abierto" sin verse.
 */
export function highestVisibleRole(sections: readonly MenuSectionLike[]): Role | null {
  const present = new Set(sections.map(section => String(section.role).toUpperCase()));
  for (const role of ROLE_PRIVILEGE_ORDER) {
    if (present.has(role)) {
      return role;
    }
  }
  return null;
}

/**
 * Paneles abiertos cuando el usuario todavía no tocó el menú en esta sesión:
 * los de su rol más alto, más el que contiene la ruta activa.
 */
export function defaultOpenSectionIds(
  sections: readonly MenuSectionLike[],
  activeRoute: string
): string[] {
  const top = highestVisibleRole(sections);
  const open = new Set<string>();

  for (const section of sections) {
    if (top && String(section.role).toUpperCase() === top) {
      open.add(section.id);
    }
    if (sectionContainsActiveRoute(section, activeRoute)) {
      open.add(section.id);
    }
  }

  return sections.map(section => section.id).filter(id => open.has(id));
}

/**
 * Combina la preferencia manual de la sesión con la ruta activa.
 * `stored === null` significa que no hay preferencia: se usan los defaults.
 * Una sección guardada como cerrada se vuelve a abrir si la ruta actual está adentro.
 */
export function resolveOpenSectionIds(
  sections: readonly MenuSectionLike[],
  activeRoute: string,
  stored: Readonly<Record<string, boolean>> | null
): string[] {
  if (!stored) {
    return defaultOpenSectionIds(sections, activeRoute);
  }

  const open = new Set<string>();
  for (const section of sections) {
    if (stored[section.id] === true || sectionContainsActiveRoute(section, activeRoute)) {
      open.add(section.id);
    }
  }

  return sections.map(section => section.id).filter(id => open.has(id));
}

/**
 * Cada rol heredado tiene que rankear más abajo que quien lo incluye.
 * Sirve para detectar si ROLE_HIERARCHY y el orden del sidebar se desalinean.
 */
export function inheritedRolesRankLower(order: readonly Role[] = ROLE_PRIVILEGE_ORDER): boolean {
  return order.every(role => {
    const rank = order.indexOf(role);
    return (ROLE_HIERARCHY[role] ?? []).every(inherited => order.indexOf(inherited) > rank);
  });
}
