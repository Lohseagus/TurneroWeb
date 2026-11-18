import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { UserContextService } from '../services/user-context.service';

export const SOLAPAS_TURNOS = ['por-confirmar', 'agenda', 'todos', 'lista-espera'] as const;
export type SolapaTurnos = (typeof SOLAPAS_TURNOS)[number];
export type VistaTurnos = 'lista' | 'estadisticas' | 'avanzada';

export interface DestinoSolapa {
  tab: SolapaTurnos;
  vista?: Exclude<VistaTurnos, 'lista'>;
}

/** Rutas viejas del operador y la solapa que las reemplaza. /turnos/new sigue siendo el formulario. */
export function destinoDeRutaOperador(path: string | null | undefined): DestinoSolapa | 'mi-centro' | null {
  switch ((path ?? '').replace(/^\//, '')) {
    case 'operador-dashboard':
      return 'mi-centro';
    case 'turnos':
      return { tab: 'todos' };
    case 'turnos/advanced-search':
      return { tab: 'todos', vista: 'avanzada' };
    case 'agenda':
      return { tab: 'agenda' };
    case 'lista-espera':
      return { tab: 'lista-espera' };
    case 'lista-espera-estadisticas':
      return { tab: 'lista-espera', vista: 'estadisticas' };
    default:
      return null;
  }
}

export function solapaDesdeQuery(tab: string | null | undefined, vista: string | null | undefined): {
  solapa: SolapaTurnos;
  vista: VistaTurnos;
} {
  const solapa = (SOLAPAS_TURNOS as readonly string[]).includes(tab ?? '')
    ? (tab as SolapaTurnos)
    : 'por-confirmar';
  if (solapa === 'lista-espera' && vista === 'estadisticas') {
    return { solapa, vista: 'estadisticas' };
  }
  if (solapa === 'todos' && vista === 'avanzada') {
    return { solapa, vista: 'avanzada' };
  }
  return { solapa, vista: 'lista' };
}

/**
 * El operador primario llega a la solapa nueva. Administrador y superadmin
 * siguen viendo la pantalla que ya tenían en esa ruta.
 */
export const redirigirPanelOperador: CanActivateFn = (route) => {
  const contexto = inject(UserContextService);
  if (!contexto.isOperador) {
    return true;
  }
  const destino = destinoDeRutaOperador(route.routeConfig?.path);
  if (!destino) {
    return true;
  }
  const router = inject(Router);
  if (destino === 'mi-centro') {
    return router.createUrlTree(['/mi-centro']);
  }
  return router.createUrlTree(['/operador/turnos'], {
    queryParams: {
      ...route.queryParams,
      tab: destino.tab,
      ...(destino.vista ? { vista: destino.vista } : {})
    }
  });
};
