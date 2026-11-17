import { Role, ROLE_HIERARCHY } from '../inicio-sesion/auth.service';
import { filterItemsByPrimaryRole, getMenuSectionsForUser } from './menu.config';
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

describe('paneles abiertos por defecto', () => {
  it('SUPERADMIN con el resto de los roles abre solo Panel SuperAdmin', () => {
    const sections = visibleSections(ALL_ROLES, Role.SUPERADMIN);

    expect(sections.map(section => section.id)).toEqual([
      'medico',
      'paciente',
      'operador',
      'administrador',
      'superadmin',
      'gestion'
    ]);
    expect(defaultOpenSectionIds(sections, '/')).toEqual(['superadmin']);
    expect(defaultOpenSectionIds(sections, '/saas')).toEqual(['superadmin']);
  });

  it('ADMINISTRADOR abre solo Panel de Administración y no muestra Médico ni Gestión General', () => {
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

  it('OPERADOR abre Panel de Operador y Gestión General, que comparten ese rol', () => {
    const sections = visibleSections([Role.OPERADOR, Role.PACIENTE], Role.OPERADOR);

    expect(defaultOpenSectionIds(sections, '/operador-dashboard')).toEqual(['operador', 'gestion']);
    expect(defaultOpenSectionIds(sections, '/')).toEqual(['operador', 'gestion']);
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

  it('no trata Inicio como parte de un panel', () => {
    const sections = visibleSections([Role.PACIENTE], Role.PACIENTE);
    expect(routeMatches('/paciente-dashboard', '/')).toBe(false);
    expect(routeMatches('/', '/')).toBe(true);
    expect(defaultOpenSectionIds(sections, '/')).toEqual(['paciente']);
  });

  it('vuelve a abrir un panel cerrado a mano cuando la ruta activa está adentro', () => {
    const sections = visibleSections([Role.MEDICO, Role.PACIENTE], Role.MEDICO);
    const stored = { medico: true, paciente: false };

    expect(resolveOpenSectionIds(sections, '/medico-dashboard', stored)).toEqual(['medico']);
    expect(resolveOpenSectionIds(sections, '/paciente-historial', stored)).toEqual(['medico', 'paciente']);
  });

  it('respeta el cierre manual del rol más alto cuando la ruta no es de ese panel', () => {
    const sections = visibleSections(ALL_ROLES, Role.SUPERADMIN);
    const stored = {
      medico: false,
      paciente: true,
      operador: false,
      administrador: false,
      superadmin: false,
      gestion: false
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
