import { Role, ROLE_HIERARCHY } from '../inicio-sesion/auth.service';
import { filterItemsByPrimaryRole, getMenuSectionsForUser, homeRouteForRole, personalizeMenuItem, portalPacienteHabilitado } from './menu.config';
import { etiquetaCambioDeVista, puedeCambiarDeVista, seccionesDeLaVista } from './vista-panel';
import { clearAllVistaPanel, readVistaPanel, vistaPanelKey, writeVistaPanel } from './vista-panel-storage';
import {
  clearAllMenuSectionState,
  menuSectionStateKey,
  readMenuSectionState,
  writeMenuSectionState
} from './menu-section-storage';
import {
  ROLE_PRIVILEGE_ORDER,
  defaultOpenSectionIds,
  inheritedRolesRankLower,
  resolveOpenSectionIds,
  routeMatches
} from './menu-section-state';

const ALL_ROLES: Role[] = [
  Role.SUPERADMIN,
  Role.ADMINISTRADOR,
  Role.OPERADOR,
  Role.MEDICO,
  Role.PACIENTE
];

function visibleSections(roles: Role[], primaryRole: Role) {
  return filterItemsByPrimaryRole(getMenuSectionsForUser(roles, primaryRole), primaryRole);
}

function memoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear() {
      map.clear();
    },
    getItem(key: string) {
      return map.has(key) ? map.get(key)! : null;
    },
    key(index: number) {
      return [...map.keys()][index] ?? null;
    },
    removeItem(key: string) {
      map.delete(key);
    },
    setItem(key: string, value: string) {
      map.set(key, value);
    }
  };
}

describe('jerarquía de paneles del sidebar', () => {
  it('ordena SUPERADMIN > ADMINISTRADOR > OPERADOR > MEDICO > PACIENTE', () => {
    expect(ROLE_PRIVILEGE_ORDER).toEqual([
      Role.SUPERADMIN,
      Role.ADMINISTRADOR,
      Role.OPERADOR,
      Role.MEDICO,
      Role.PACIENTE
    ]);
  });

  it('deja cada rol heredado por debajo del rol que lo incluye', () => {
    expect(inheritedRolesRankLower()).toBe(true);
    expect(ROLE_HIERARCHY[Role.SUPERADMIN]).toEqual(
      expect.arrayContaining([Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO, Role.PACIENTE])
    );
    expect(ROLE_HIERARCHY[Role.ADMINISTRADOR]).toEqual(
      expect.arrayContaining([Role.OPERADOR, Role.MEDICO, Role.PACIENTE])
    );
  });
});

describe('cambio de vista del sidebar', () => {
  const rolesMedico = [Role.MEDICO, Role.PACIENTE];

  it('el staff ve solo su panel y, al cambiar, solo los cuatro ítems del paciente', () => {
    const secciones = filterItemsByPrimaryRole(getMenuSectionsForUser(rolesMedico, Role.MEDICO), Role.MEDICO);

    expect(puedeCambiarDeVista(Role.MEDICO)).toBe(true);
    expect(etiquetaCambioDeVista(Role.MEDICO, 'staff')).toBe('Cambiar a paciente');
    expect(seccionesDeLaVista(secciones, Role.MEDICO, 'staff').map(section => section.id)).toEqual(['medico']);

    const paciente = seccionesDeLaVista(secciones, Role.MEDICO, 'paciente');
    expect(etiquetaCambioDeVista(Role.MEDICO, 'paciente')).toBe('Cambiar a médico');
    expect(paciente.map(section => section.id)).toEqual(['paciente']);
    expect(paciente[0].items.map(item => item.label)).toEqual(['Inicio', 'Mis turnos', 'Mi salud', 'Mi cuenta']);
    expect(defaultOpenSectionIds(paciente, '/paciente-dashboard')).toEqual(['paciente']);
  });

  it('operador, administración y superadmin vuelven con el texto de su rol', () => {
    expect(etiquetaCambioDeVista(Role.OPERADOR, 'paciente')).toBe('Cambiar a operador');
    expect(etiquetaCambioDeVista(Role.ADMINISTRADOR, 'paciente')).toBe('Cambiar a administración');
    expect(etiquetaCambioDeVista(Role.SUPERADMIN, 'paciente')).toBe('Cambiar a superadmin');

    expect(seccionesDeLaVista(
      filterItemsByPrimaryRole(getMenuSectionsForUser([Role.OPERADOR, Role.PACIENTE], Role.OPERADOR), Role.OPERADOR),
      Role.OPERADOR,
      'staff'
    ).map(section => section.id)).toEqual(['operador']);

    expect(seccionesDeLaVista(
      filterItemsByPrimaryRole(
        getMenuSectionsForUser([Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO, Role.PACIENTE], Role.ADMINISTRADOR),
        Role.ADMINISTRADOR
      ),
      Role.ADMINISTRADOR,
      'staff'
    ).map(section => section.id)).toEqual(['operador', 'administrador']);

    expect(seccionesDeLaVista(
      filterItemsByPrimaryRole(getMenuSectionsForUser(ALL_ROLES, Role.SUPERADMIN), Role.SUPERADMIN),
      Role.SUPERADMIN,
      'staff'
    ).some(section => section.id === 'paciente')).toBe(false);
  });

  it('un PACIENTE puro no ve el botón y conserva su panel aunque no haya ficha', () => {
    const secciones = filterItemsByPrimaryRole(getMenuSectionsForUser([Role.PACIENTE], Role.PACIENTE), Role.PACIENTE);

    expect(puedeCambiarDeVista(Role.PACIENTE)).toBe(false);
    expect(portalPacienteHabilitado(Role.PACIENTE, false)).toBe(true);
    expect(portalPacienteHabilitado(Role.MEDICO, false)).toBe(false);
    expect(seccionesDeLaVista(secciones, Role.PACIENTE, 'staff').map(section => section.id)).toEqual(['paciente']);
  });

  it('recuerda la vista por usuario y la olvida al cerrar sesión', () => {
    const storage = memoryStorage();
    writeVistaPanel(storage, 'Medico@CheTurno.test', 'paciente');
    writeVistaPanel(storage, 'operador@cheturno.test', 'staff');

    expect(readVistaPanel(storage, 'medico@cheturno.test')).toBe('paciente');
    expect(storage.getItem(vistaPanelKey('medico@cheturno.test'))).toBe('paciente');
    expect(readVistaPanel(storage, 'operador@cheturno.test')).toBe('staff');

    clearAllVistaPanel(storage);

    expect(readVistaPanel(storage, 'medico@cheturno.test')).toBe('staff');
    expect(storage.length).toBe(0);
  });
});

describe('paneles abiertos por defecto', () => {
  it('SUPERADMIN con el resto de los roles abre solo Panel SuperAdmin', () => {
    const sections = visibleSections(ALL_ROLES, Role.SUPERADMIN);

    expect(sections.map(section => section.id)).toEqual([
      'medico',
      'paciente',
      'operador',
      'administrador',
      'superadmin'
    ]);
    expect(defaultOpenSectionIds(sections, '/')).toEqual(['superadmin']);
    expect(defaultOpenSectionIds(sections, '/saas')).toEqual(['superadmin']);
  });

  it('ADMINISTRADOR abre solo Panel de Administración y no muestra el Panel de Médico', () => {
    const sections = visibleSections(
      [Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO, Role.PACIENTE],
      Role.ADMINISTRADOR
    );

    expect(sections.map(section => section.id)).toEqual(['paciente', 'operador', 'administrador']);
    expect(defaultOpenSectionIds(sections, '/admin-dashboard')).toEqual(['administrador']);
    expect(defaultOpenSectionIds(sections, '/')).toEqual(['administrador']);
  });

  it('MEDICO con acceso de PACIENTE abre Panel de Médico y deja Panel de Paciente cerrado', () => {
    const sections = visibleSections([Role.MEDICO, Role.PACIENTE], Role.MEDICO);

    expect(sections.map(section => section.id)).toEqual(['medico', 'paciente']);
    expect(defaultOpenSectionIds(sections, '/medico-dashboard')).toEqual(['medico']);
    expect(sections.find(section => section.id === 'paciente')?.items.some(item => item.route === '/paciente-perfil')).toBe(false);
  });

  it('PACIENTE solo abre Panel de Paciente', () => {
    const sections = visibleSections([Role.PACIENTE], Role.PACIENTE);

    expect(sections.map(section => section.id)).toEqual(['paciente']);
    expect(defaultOpenSectionIds(sections, '/paciente-dashboard')).toEqual(['paciente']);
    expect(defaultOpenSectionIds(sections, '/')).toEqual(['paciente']);
  });

  it('OPERADOR abre solo su panel', () => {
    const sections = visibleSections([Role.OPERADOR, Role.PACIENTE], Role.OPERADOR);

    expect(sections.map(section => section.id)).toEqual(['paciente', 'operador']);
    expect(defaultOpenSectionIds(sections, '/mi-centro')).toEqual(['operador']);
    expect(defaultOpenSectionIds(sections, '/operador/turnos')).toEqual(['operador']);
    expect(defaultOpenSectionIds(sections, '/')).toEqual(['operador']);
  });
});

describe('apertura por ruta activa', () => {
  it('abre el panel de la ruta además del rol más alto', () => {
    const medico = visibleSections([Role.MEDICO, Role.PACIENTE], Role.MEDICO);
    expect(defaultOpenSectionIds(medico, '/paciente-agenda')).toEqual(['medico', 'paciente']);
    expect(defaultOpenSectionIds(medico, '/paciente-agenda?from=home')).toEqual(['medico', 'paciente']);

    const superadmin = visibleSections(ALL_ROLES, Role.SUPERADMIN);
    expect(defaultOpenSectionIds(superadmin, '/medico-dashboard')).toEqual(['medico', 'superadmin']);

    const admin = visibleSections(
      [Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO, Role.PACIENTE],
      Role.ADMINISTRADOR
    );
    expect(defaultOpenSectionIds(admin, '/pacientes')).toEqual(['operador', 'administrador']);
  });

  it('no trata la página de inicio como parte de un panel', () => {
    const sections = visibleSections(ALL_ROLES, Role.SUPERADMIN);
    expect(routeMatches('/paciente-dashboard', '/')).toBe(false);
    expect(routeMatches('/', '/')).toBe(true);
    expect(sections.some(section => section.items.some(item => item.route === '/'))).toBe(false);
    expect(defaultOpenSectionIds(visibleSections([Role.PACIENTE], Role.PACIENTE), '/')).toEqual(['paciente']);
  });

  it('vuelve a abrir un panel cerrado a mano cuando la ruta activa está adentro', () => {
    const sections = visibleSections([Role.MEDICO, Role.PACIENTE], Role.MEDICO);
    const stored = { medico: true, paciente: false };

    expect(resolveOpenSectionIds(sections, '/medico-dashboard', stored)).toEqual(['medico']);
    expect(resolveOpenSectionIds(sections, '/paciente-turnos', stored)).toEqual(['medico', 'paciente']);
  });

  it('respeta el cierre manual del rol más alto cuando la ruta no es de ese panel', () => {
    const sections = visibleSections(ALL_ROLES, Role.SUPERADMIN);
    const stored = {
      medico: false,
      paciente: true,
      operador: false,
      administrador: false,
      superadmin: false
    };

    expect(resolveOpenSectionIds(sections, '/', stored)).toEqual(['paciente']);
    expect(resolveOpenSectionIds(sections, '/saas', stored)).toEqual(['paciente', 'superadmin']);
  });
});

describe('sessionStorage de paneles por usuario', () => {
  it('guarda el estado de cada usuario por separado y lo borra al cerrar sesión', () => {
    const storage = memoryStorage();
    writeMenuSectionState(storage, 'Medico@CheTurno.test', { medico: true, paciente: false });
    writeMenuSectionState(storage, 'superadmin@cheturno.test', { superadmin: true, medico: false });

    expect(readMenuSectionState(storage, 'medico@cheturno.test')).toEqual({
      medico: true,
      paciente: false
    });
    expect(readMenuSectionState(storage, 'superadmin@cheturno.test')).toEqual({
      superadmin: true,
      medico: false
    });
    expect(storage.getItem(menuSectionStateKey('medico@cheturno.test'))).toContain('"paciente":false');

    clearAllMenuSectionState(storage);

    expect(readMenuSectionState(storage, 'medico@cheturno.test')).toBeNull();
    expect(readMenuSectionState(storage, 'superadmin@cheturno.test')).toBeNull();
    expect(storage.length).toBe(0);
  });

  it('sin preferencia guardada vuelve al panel del rol más alto', () => {
    const sections = visibleSections([Role.MEDICO, Role.PACIENTE], Role.MEDICO);
    expect(resolveOpenSectionIds(sections, '/medico-horarios', null)).toEqual(['medico']);
  });
});

describe('Mi Centro e Inicio', () => {
  it('deja Mi Centro en el panel del operador y no lo duplica para SUPERADMIN', () => {
    const operador = visibleSections([Role.OPERADOR, Role.PACIENTE], Role.OPERADOR);
    const miCentro = operador.find(section => section.id === 'operador')?.items.find(item => item.route === '/mi-centro');
    expect(miCentro?.label).toBe('Mi Centro');
    expect(personalizeMenuItem(miCentro!, Role.OPERADOR, 12).route).toBe('/centrosAtencion/12');

    const superadmin = visibleSections(ALL_ROLES, Role.SUPERADMIN);
    expect(superadmin.map(section => section.id)).not.toContain('gestion');
    const operadorDeSuper = superadmin.find(section => section.id === 'operador');
    expect(operadorDeSuper?.items.some(item => item.route === '/mi-centro' || item.label === 'Mi Centro')).toBe(false);
    expect(superadmin.find(section => section.id === 'superadmin')?.items.some(item =>
      item.label === 'Gestión de Centros' && item.route === '/centrosAtencion'
    )).toBe(true);
  });

  it('el operador primario ve el menú unificado y el administrador conserva el panel de operador de siempre', () => {
    const operador = visibleSections([Role.OPERADOR, Role.PACIENTE], Role.OPERADOR);
    expect(operador.find(section => section.id === 'operador')?.items.map(item => item.label)).toEqual([
      'Mi Centro',
      'Turnos',
      'Pacientes',
      'Ausencias y feriados',
      'Mi Perfil'
    ]);
    const turnos = operador.find(section => section.id === 'operador')?.items.find(item => item.label === 'Turnos');
    expect(turnos?.route).toBe('/operador/turnos');
    expect(turnos?.requiresPrimaryRole).toBeFalsy();
    expect(operador.find(section => section.id === 'operador')?.items.find(item => item.label === 'Mi Perfil')?.requiresPrimaryRole)
      .toBe(true);

    const admin = visibleSections(
      [Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO, Role.PACIENTE],
      Role.ADMINISTRADOR
    );
    expect(admin.find(section => section.id === 'operador')?.items.map(item => item.label)).toEqual([
      'Mi Dashboard',
      'Gestión de Turnos',
      'Turno manual (extra)',
      'Pacientes',
      'Agenda de turnos',
      'Ausencias y feriados',
      'Lista de Espera',
      'Estadísticas Lista Espera'
    ]);
    expect(admin.find(section => section.id === 'administrador')?.items.map(item => item.label)).toContain('Dashboard');
  });

  it('el administrador conserva Mi Centro en su propio panel', () => {
    const admin = visibleSections(
      [Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO, Role.PACIENTE],
      Role.ADMINISTRADOR
    );
    expect(admin.find(section => section.id === 'operador')?.items.some(item => item.route === '/mi-centro')).toBe(false);
    const centros = admin.find(section => section.id === 'administrador')?.items.find(item => item.route === '/centrosAtencion');
    const personalized = personalizeMenuItem(centros!, Role.ADMINISTRADOR, 4);
    expect(personalized.label).toBe('Mi Centro');
    expect(personalized.route).toBe('/centrosAtencion/4');
  });

  it('el logo apunta al inicio de cada rol y ningún panel contiene Inicio', () => {
    expect(homeRouteForRole(Role.SUPERADMIN)).toBe('/saas');
    expect(homeRouteForRole(Role.ADMINISTRADOR)).toBe('/admin-dashboard');
    expect(homeRouteForRole(Role.OPERADOR)).toBe('/mi-centro');
    expect(homeRouteForRole(Role.MEDICO)).toBe('/medico-dashboard');
    expect(homeRouteForRole(Role.PACIENTE)).toBe('/paciente-dashboard');
    expect(homeRouteForRole(null)).toBe('/');

    const sections = visibleSections(ALL_ROLES, Role.SUPERADMIN);
    expect(sections.some(section => section.label === 'General' || section.id === 'gestion')).toBe(false);
    expect(sections.some(section => section.items.some(item => item.route === '/'))).toBe(false);
    expect(sections.find(section => section.id === 'paciente')?.items.find(item => item.label === 'Inicio')?.route)
      .toBe('/paciente-dashboard');
  });
});
