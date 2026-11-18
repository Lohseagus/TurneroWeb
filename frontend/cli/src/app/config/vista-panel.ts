import { Role } from '../inicio-sesion/auth.service';
import { MenuSection } from './menu.config';
import { VistaPanel } from './vista-panel-storage';

export type { VistaPanel };

/**
 * El paciente puro no cambia de vista. El staff (médico, operador,
 * administración y superadmin) sí: ve un panel por vez.
 */
export function puedeCambiarDeVista(primaryRole: string | null | undefined): boolean {
  switch ((primaryRole ?? '').toUpperCase()) {
    case Role.MEDICO:
    case Role.OPERADOR:
    case Role.ADMINISTRADOR:
    case Role.SUPERADMIN:
      return true;
    default:
      return false;
  }
}

/** Texto del botón que está arriba de Cerrar sesión. */
export function etiquetaCambioDeVista(
  primaryRole: string | null | undefined,
  vista: VistaPanel
): string {
  if (vista !== 'paciente') {
    return 'Cambiar a paciente';
  }
  switch ((primaryRole ?? '').toUpperCase()) {
    case Role.MEDICO:
      return 'Cambiar a médico';
    case Role.OPERADOR:
      return 'Cambiar a operador';
    case Role.ADMINISTRADOR:
      return 'Cambiar a administración';
    case Role.SUPERADMIN:
      return 'Cambiar a superadmin';
    default:
      return 'Cambiar a paciente';
  }
}

/**
 * En la vista de staff no se apila el portal. En la de paciente solo quedan
 * Inicio, Mis turnos, Mi salud y Mi cuenta. Un PACIENTE puro no se filtra.
 */
export function seccionesDeLaVista(
  sections: MenuSection[],
  primaryRole: string | null | undefined,
  vista: VistaPanel
): MenuSection[] {
  if (!puedeCambiarDeVista(primaryRole)) {
    return sections;
  }
  if (vista === 'paciente') {
    return sections.filter(section => section.id === 'paciente');
  }
  return sections.filter(section => section.id !== 'paciente');
}
