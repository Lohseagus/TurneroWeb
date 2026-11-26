import { Role } from '../inicio-sesion/auth.service';
import { FeaturesPlan } from '../saas/saas.models';
import { MenuItem, aplicarFeaturesPlan, getMenuSectionsForUser } from './menu.config';

function profesional(overrides: Partial<FeaturesPlan> = {}): FeaturesPlan {
  return {
    enforcementEnabled: true,
    aplica: true,
    planCodigo: 'PROFESIONAL',
    planNombre: 'Profesional',
    features: [],
    catalogo: [
      { codigo: 'REPORTES', etiqueta: 'Reportes y métricas', incluida: false, planMinimoCodigo: 'CONSULTORIO', planMinimoNombre: 'Consultorio' },
      { codigo: 'REPORTES_CONSOLIDADOS', etiqueta: 'Reportes consolidados de la red', incluida: false, planMinimoCodigo: 'RED', planMinimoNombre: 'Red' },
      { codigo: 'AUDITORIA', etiqueta: 'Panel de auditoría', incluida: false, planMinimoCodigo: 'CONSULTORIO', planMinimoNombre: 'Consultorio' },
      { codigo: 'ENCUESTAS', etiqueta: 'Encuestas de satisfacción', incluida: false, planMinimoCodigo: 'CONSULTORIO', planMinimoNombre: 'Consultorio' },
      { codigo: 'SOPORTE_PRIORITARIO', etiqueta: 'Soporte prioritario', incluida: false, planMinimoCodigo: 'RED', planMinimoNombre: 'Red' }
    ],
    ...overrides
  };
}

function itemsAdmin(estado: FeaturesPlan | null): MenuItem[] {
  const secciones = aplicarFeaturesPlan(
    getMenuSectionsForUser([Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO, Role.PACIENTE], Role.ADMINISTRADOR),
    estado
  );
  return secciones.find(s => s.id === 'administrador')!.items;
}

function item(items: MenuItem[], route: string): MenuItem {
  return items.find(i => i.route === route)!;
}

describe('aplicarFeaturesPlan', () => {
  it('con plan Profesional y la bandera prendida deshabilita auditoría, métricas, reporte y encuestas', () => {
    const items = itemsAdmin(profesional());
    for (const route of ['/turnos/audit-dashboard', '/dashboard-gestion', '/admin-reporte-atencion', '/admin/gestion-encuestas']) {
      expect(item(items, route).disabled, route).toBe(true);
      expect(item(items, route).disabledHint, route).toBe('Disponible en plan Consultorio');
    }
    expect(item(items, '/medicos').disabled).toBeFalsy();
    expect(item(items, '/mi-plan').disabled).toBeFalsy();
  });

  it('con la bandera apagada no cambia nada', () => {
    const sinFeatures = itemsAdmin(null);
    const apagada = itemsAdmin(profesional({ enforcementEnabled: false }));
    expect(apagada).toEqual(sinFeatures);
    expect(apagada.some(i => i.disabled)).toBe(false);
  });

  it('si la compuerta no aplica al usuario no oculta nada', () => {
    expect(itemsAdmin(profesional({ aplica: false })).some(i => i.disabled)).toBe(false);
  });

  it('un plan que incluye la función deja el ítem habilitado', () => {
    const items = itemsAdmin(profesional({ planCodigo: 'CENTRO', features: ['REPORTES', 'AUDITORIA', 'ENCUESTAS'] }));
    expect(items.some(i => i.disabled)).toBe(false);
  });
});
