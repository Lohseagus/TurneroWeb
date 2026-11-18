import { Role } from '../inicio-sesion/auth.service';

/**
 * Interfaz para un item individual del menú
 */
export interface MenuItem {
  label: string;
  route?: string;
  icon: string;
  action?: () => void;
  badge?: {
    value: number | string;
    color: 'primary' | 'danger' | 'warning' | 'success' | 'info';
  };
  /** Rutas que también marcan este ítem, sin mostrarse en el menú. */
  activeOn?: string[];
  divider?: boolean;
  description?: string;
  children?: MenuItem[];
  requiresPrimaryRole?: boolean; // Si true, solo se muestra cuando el usuario tiene el rol de la sección como rol primario
}

/**
 * Interfaz para una sección del menú asociada a un rol
 */
export interface MenuSection {
  id: string;
  label: string;
  icon: string;
  role: Role;
  order: number; // Para ordenar secciones
  items: MenuItem[];
}

/**
 * Configuración centralizada del menú por roles
 * 
 * IMPORTANTE: Las secciones se mostrarán solo si el usuario tiene el rol correspondiente
 * según userContext.allRoles (incluyendo roles heredados).
 */
export const MENU_CONFIG: MenuSection[] = [

  // ========================================
  // SECCIÓN: PACIENTE
  // ========================================
  {
    id: 'paciente',
    label: 'Panel de Paciente',
    icon: 'personal_injury',
    role: Role.PACIENTE,
    order: 2,
    items: [
      {
        label: 'Inicio',
        route: '/paciente-dashboard',
        icon: 'dashboard',
        description: 'Tu próximo turno'
      },
      {
        label: 'Mis turnos',
        route: '/paciente-turnos',
        icon: 'event_available',
        description: 'Próximos, pasados y pedir un turno',
        activeOn: ['/paciente-agenda', '/paciente-reagendar-turno', '/paciente-reprogramar-ausencia', '/paciente/encuesta']
      },
      {
        label: 'Mi salud',
        route: '/paciente-salud',
        icon: 'clinical_notes',
        description: 'Historia clínica, recetas y órdenes'
      },
      {
        label: 'Mi cuenta',
        route: '/paciente-cuenta',
        icon: 'account_circle',
        description: 'Perfil, horarios preferidos y notificaciones',
        badge: {
          value: 0,
          color: 'danger'
        }
      }
    ]
  },

  // ========================================
  // SECCIÓN: MÉDICO
  // ========================================
  {
    id: 'medico',
    label: 'Panel de Médico',
    icon: 'medical_services',
    role: Role.MEDICO,
    order: 1,
    items: [
      {
        label: 'Mi Dashboard',
        route: '/medico-dashboard',
        icon: 'dashboard',
        description: 'Panel principal del médico'
      },
      {
        label: 'Mis Horarios',
        route: '/medico-horarios',
        icon: 'schedule',
        description: 'Gestionar disponibilidad'
      },
      {
        label: 'Historia clínica',
        route: '/medico-hce',
        icon: 'clinical_notes',
        description: 'Evoluciones de los pacientes que atendés'
      },
      {
        label: 'Estadísticas',
        route: '/medico-estadisticas',
        icon: 'bar_chart',
        description: 'Métricas y reportes'
      },
      {
        label: 'Turno manual (extra)',
        route: '/turnos/new',
        icon: 'event_busy',
        description: 'Registrar un turno extra fuera de la agenda'
      }
    ]
  },

  // ========================================
  // SECCIÓN: OPERADOR
  // ========================================
  {
    id: 'operador',
    label: 'Panel de Operador',
    icon: 'admin_panel_settings',
    role: Role.OPERADOR,
    order: 3,
    items: [
      {
        label: 'Mi Dashboard',
        route: '/operador-dashboard',
        icon: 'dashboard',
        description: 'Panel principal del operador'
      },
      {
        label: 'Mi Centro',
        route: '/mi-centro',
        icon: 'local_hospital',
        description: 'Gestión centralizada de mi centro',
        requiresPrimaryRole: true
      },
      {
        label: 'Gestión de Turnos',
        route: '/turnos',
        icon: 'event',
        description: 'Administrar turnos del sistema'
      },
      {
        label: 'Turno manual (extra)',
        route: '/turnos/new',
        icon: 'event_busy',
        description: 'Registrar un turno extra fuera de la agenda'
      },
      {
        label: 'Pacientes',
        route: '/pacientes',
        icon: 'group',
        description: 'Gestionar pacientes'
      },
      {
        label: 'Mi Perfil',
        route: '/operador-perfil',
        icon: 'account_circle',
        description: 'Configuración personal',
        requiresPrimaryRole: true // Solo visible si el usuario es OPERADOR como rol primario
      },
      /*  {
         label: 'Solicitar Turno',
         route: '/turnos/new',
         icon: 'add_box',
         description: 'Reservar un nuevo turno médico'
       } */
      {
        label: 'Agenda de turnos',
        route: '/agenda',
        icon: 'event_note',
        description: 'Reservar un nuevo turno médico'
      },
      {
        label: 'Ausencias y feriados',
        route: '/agenda/dias-excepcionales',
        icon: 'event_busy',
        description: 'Feriados, mantenimiento y ausencias de profesionales'
      },
      {
        label: 'Lista de Espera',
        route: '/lista-espera',
        icon: 'pending_actions',
        description: 'Gestionar lista de espera de pacientes'
      },
      {
        label: 'Estadísticas Lista Espera', // 👈 NUEVO
        route: '/lista-espera-estadisticas',
        icon: 'trending_up',
        description: 'Métricas y análisis de lista de espera'
      }
    ]
  },


  // ========================================
  // SECCIÓN: ADMINISTRADOR
  // ========================================
  {
    id: 'administrador',
    label: 'Panel de Administración',
    icon: 'shield',
    role: Role.ADMINISTRADOR,
    order: 4,
    items: [
      {
        label: 'Dashboard',
        route: '/admin-dashboard',
        icon: 'dashboard',
        description: 'Panel de control administrativo'
      },
      {
        label: 'Panel Auditoria',
        route: '/turnos/audit-dashboard',
        icon: 'policy',
        description: 'Panel de control administrativo'
      },

      {
        label: 'Centros de Atención',
        route: '/centrosAtencion',
        icon: 'local_hospital',
        description: 'Gestionar centros médicos'
      },
      {
        label: 'Médicos',
        route: '/medicos',
        icon: 'medical_services',
        description: 'Administrar médicos'
      },
      {
        label: 'Ausencias y feriados',
        route: '/agenda/dias-excepcionales',
        icon: 'event_busy',
        description: 'Feriados, mantenimiento y ausencias de profesionales'
      },
      {
        label: 'Staff Médico',
        route: '/staffMedico',
        icon: 'badge',
        description: 'Asignaciones y vinculaciones'
      },
      {
        label: 'Obras Sociales',
        route: '/obraSocial',
        icon: 'health_and_safety',
        description: 'Gestionar obras sociales'
      },
      {
        label: 'Operadores',
        route: '/operadores',
        icon: 'support_agent',
        description: 'Operadores',
      },
      {
        label: 'Estadísticas Lista Espera',
        route: '/lista-espera-estadisticas',
        icon: 'trending_up',
        description: 'Métricas y análisis de lista de espera'
      },
      {
        label: 'Metricas',
        route: '/dashboard-gestion',
        icon: 'leaderboard',
        description: 'Métricas y análisis de gestión'
      },
      {
        label: 'Reporte de Atención',
        route: '/admin-reporte-atencion',
        icon: 'assessment',
        description: 'Reporte de atenciones completadas'
      },
      {
        label: 'Encuestas',
        route: '/admin/gestion-encuestas',
        icon: 'assignment',
        description: 'Crear y gestionar encuestas para pacientes'
      },
      {
        divider: true,
        label: '',
        icon: ''
      },
      {
        label: 'Mi Perfil',
        route: '/admin-perfil',
        icon: 'account_circle',
        description: 'Perfil de administrador',
        requiresPrimaryRole: true // Solo visible si el usuario es ADMINISTRADOR como rol primario
      },
      {
        label: 'Mi plan',
        route: '/mi-plan',
        icon: 'payments',
        description: 'Plan, prueba, uso y pagos',
        requiresPrimaryRole: true
      }

    ]
  },

  // ========================================
  // SECCIÓN: SUPERADMIN (Gestión Multi-Tenant)
  // ========================================
  {
    id: 'superadmin',
    label: 'Panel SuperAdmin',
    icon: 'shield_person',
    role: Role.SUPERADMIN,
    order: 5,
    items: [
      {
        label: 'Tablero SaaS',
        route: '/saas',
        icon: 'dashboard',
        description: 'Suscripciones, MRR, pruebas y cobros'
      },
      {
        label: 'Gestión de Centros',
        route: '/centrosAtencion',
        icon: 'domain',
        description: 'ABM de Centros de Atención (multi-tenant)'
      },
      {
        label: 'Gestión de Admins',
        route: '/admin/users',
        icon: 'admin_panel_settings',
        description: 'Crear y asignar administradores a centros'
      },
      {
        label: 'Auditoría Global',
        route: '/turnos/audit-dashboard',
        icon: 'policy',
        description: 'Ver logs de auditoría de todos los centros'
      },
      {
        label: 'Métricas del Sistema',
        route: '/dashboard-gestion',
        icon: 'analytics',
        description: 'Estadísticas globales de todos los centros'
      },
      {
        label: 'Suscripciones',
        route: '/saas/suscripciones',
        icon: 'list_alt',
        description: 'Listado de organizaciones suscriptas'
      },
      {
        label: 'Planes y precios',
        route: '/saas/planes',
        icon: 'sell',
        description: 'Precios netos, IVA, prueba y complementos'
      },
      {
        label: 'Pagos',
        route: '/saas/pagos',
        icon: 'receipt_long',
        description: 'Historial de cobros'
      },
      {
        divider: true,
        label: '',
        icon: ''
      },
      {
        label: 'Acceso Admin',
        route: '/admin-dashboard',
        icon: 'visibility',
        description: 'Ver panel de administrador (soporte)'
      }
    ]
  }
];

/**
 * Destino del logo CheTurno. Es el mismo lugar al que llega cada rol después de entrar.
 * Inicio ya no es un ítem del menú: repetirlo en cada panel abriría todos los grupos
 * cuando la ruta es `/`.
 */
export function homeRouteForRole(role: string | null | undefined): string {
  switch (role?.toUpperCase()) {
    case Role.SUPERADMIN:
      return '/saas';
    case Role.ADMINISTRADOR:
      return '/admin-dashboard';
    case Role.OPERADOR:
      return '/operador-dashboard';
    case Role.MEDICO:
      return '/medico-dashboard';
    case Role.PACIENTE:
      return '/paciente-dashboard';
    default:
      return '/';
  }
}

/**
 * Ajusta el enlace de centros según el rol, sin duplicar "Mi Centro" para SUPERADMIN.
 * ADMINISTRADOR ya lo tiene en su panel. OPERADOR entra por /mi-centro, que el guard
 * manda a /centrosAtencion/{id}.
 */
export function personalizeMenuItem(
  item: MenuItem,
  primaryRole: string | null | undefined,
  centroAtencionId?: number | null
): MenuItem {
  const role = primaryRole?.toUpperCase();

  if (item.route === '/centrosAtencion') {
    if (role === Role.SUPERADMIN) {
      return { ...item, label: 'Gestión de Centros', route: '/centrosAtencion' };
    }
    if (role === Role.ADMINISTRADOR) {
      return {
        ...item,
        label: 'Mi Centro',
        route: centroAtencionId ? `/centrosAtencion/${centroAtencionId}` : '/centrosAtencion'
      };
    }
  }

  if (item.route === '/mi-centro' && centroAtencionId) {
    return { ...item, route: `/centrosAtencion/${centroAtencionId}` };
  }

  return item;
}

/**
 * Helper para obtener secciones de menú para un conjunto de roles
 * @param userRoles Roles del usuario actual
 * @returns Secciones de menú filtradas y ordenadas
 */
export function getMenuSectionsForRoles(userRoles: Role[]): MenuSection[] {
  return MENU_CONFIG
    .filter(section => userRoles.includes(section.role))
    .sort((a, b) => a.order - b.order);
}

/**
 * Secciones que el usuario ve. El rol primario ADMINISTRADOR no muestra el Panel de Médico.
 */
export function getMenuSectionsForUser(userRoles: Role[], primaryRole?: string | null): MenuSection[] {
  const sections = getMenuSectionsForRoles(userRoles);
  if (primaryRole?.toUpperCase() === Role.ADMINISTRADOR) {
    return sections.filter(section => section.id !== 'medico');
  }
  return sections;
}

/** Texto del vacío cuando la cuenta no tiene fila Paciente. */
export const MENSAJE_SIN_FICHA_PACIENTE = 'Tu cuenta no tiene ficha de paciente';

/**
 * El portal de las rutas de paciente. Un PACIENTE primario entra igual.
 * El staff sin ficha ve el vacío de esas rutas; el sidebar ya no apila el panel.
 */
export function portalPacienteHabilitado(
  primaryRole: string | null | undefined,
  tieneFichaPaciente: boolean
): boolean {
  if ((primaryRole ?? '').toUpperCase() === Role.PACIENTE) {
    return true;
  }
  return tieneFichaPaciente;
}

/**
 * Oculta ítems marcados como `requiresPrimaryRole` cuando el rol primario no es el de la sección.
 */
export function filterItemsByPrimaryRole(sections: MenuSection[], primaryRole?: string | null): MenuSection[] {
  const primary = primaryRole?.toUpperCase() ?? '';
  return sections.map(section => ({
    ...section,
    items: section.items.filter(item => {
      if (!item.requiresPrimaryRole) {
        return true;
      }
      if (!primary) {
        return false;
      }
      return primary === String(section.role).toUpperCase();
    })
  }));
}

/**
 * Helper para obtener todos los items de menú sin duplicados
 * (útil para búsqueda o navegación global)
 * @param userRoles Roles del usuario actual
 * @returns Lista de items únicos
 */
export function getAllMenuItems(userRoles: Role[]): MenuItem[] {
  const sections = getMenuSectionsForRoles(userRoles);
  const itemsMap = new Map<string, MenuItem>();

  sections.forEach(section => {
    section.items.forEach(item => {
      if (item.route && !itemsMap.has(item.route)) {
        itemsMap.set(item.route, item);
      }
    });
  });

  return Array.from(itemsMap.values());
}

/**
 * Helper para verificar si una ruta está en el menú del usuario
 * @param route Ruta a verificar
 * @param userRoles Roles del usuario
 * @returns true si el usuario tiene acceso a esa ruta según el menú
 */
export function isRouteInUserMenu(route: string, userRoles: Role[]): boolean {
  const allItems = getAllMenuItems(userRoles);
  return allItems.some(item => item.route === route);
}
