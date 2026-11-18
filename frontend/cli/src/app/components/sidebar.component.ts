import { Component, OnInit, OnDestroy, Output, EventEmitter, ChangeDetectionStrategy, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink, NavigationEnd } from '@angular/router';
import { Subscription, Observable, combineLatest } from 'rxjs';
import { filter } from 'rxjs/operators';
import { MenuService } from '../services/menu.service';
import { VistaPanelService } from '../services/vista-panel.service';
import { UserContextService, UserContext } from '../services/user-context.service';
import { AuthService } from '../inicio-sesion/auth.service';
import { MenuItem, MenuSection } from '../config/menu.config';
import { etiquetaCambioDeVista, puedeCambiarDeVista, VistaPanel } from '../config/vista-panel';
import { PacienteSesionService } from '../pacientes/paciente-sesion.service';
import { Paciente } from '../pacientes/paciente';
import {
  readMenuSectionState,
  writeMenuSectionState
} from '../config/menu-section-storage';
import {
  resolveOpenSectionIds,
  itemMatchesRoute
} from '../config/menu-section-state';
import { homeRouteForRole } from '../config/menu.config';

/**
 * Componente del menú lateral (sidebar) dinámico basado en roles.
 *
 * Cada panel de rol es un grupo colapsable. El staff ve un solo panel por vez
 * y cambia con el botón de arriba de Cerrar sesión. Lo que el usuario abre
 * o cierra a mano queda en sessionStorage hasta el logout o un nuevo login.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
  animations: []
})
export class SidebarComponent implements OnInit, OnDestroy {
  
  @Output() toggleSidebar = new EventEmitter<void>();
  
  public userContext$!: Observable<UserContext | null>;
  public menuSectionsWithBadges$!: Observable<MenuSection[]>;
  
  private subscriptions: Subscription[] = [];
  private currentRoute = '';
  private sections: MenuSection[] = [];
  private userKey: string | null = null;
  private sectionSignature = '';
  /** null: el usuario no tocó los paneles en esta sesión. */
  private preference: Record<string, boolean> | null = null;
  private ignoreClickUntil = 0;

  openSectionIds = new Set<string>();
  homeRoute = '/';
  muestraCambioVista = false;
  etiquetaCambioVista = 'Cambiar a paciente';
  altaAbierta = false;
  altaEnCurso = false;
  altaError = '';
  camposFaltantes: CampoFicha[] = [];
  altaForm: Record<CampoFicha, string> = { nombre: '', apellido: '', dni: '', telefono: '' };
  private vistaActual: VistaPanel = 'staff';

  constructor(
    public menuService: MenuService,
    private userContextService: UserContextService,
    private vistaPanel: VistaPanelService,
    private pacienteSesion: PacienteSesionService,
    private authService: AuthService,
    private router: Router,
    private host: ElementRef<HTMLElement>
  ) {}

  ngOnInit(): void {
    this.userContext$ = this.userContextService.userContext$;
    this.menuSectionsWithBadges$ = this.menuService.getMenuSectionsWithBadges();
    this.currentRoute = this.router.url;

    const menuSub = combineLatest([
      this.menuSectionsWithBadges$,
      this.userContextService.userContext$
    ]).subscribe(([sections, context]) => {
      this.sections = sections ?? [];
      this.syncUserPreference(context);
    });

    const routeSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.currentRoute = event.urlAfterRedirects || event.url;
        this.rebuildOpenState();
        
        // En mobile, cerrar sidebar después de navegar
        this.menuService.sidebarState$.subscribe(state => {
          if (state.isMobile && state.isOpen) {
            this.menuService.closeSidebar();
          }
        }).unsubscribe();
      });
    
    const vistaSub = combineLatest([
      this.userContextService.userContext$,
      this.vistaPanel.vista$
    ]).subscribe(([context, vista]) => {
      const role = context?.isAuthenticated ? context.primaryRole : null;
      this.muestraCambioVista = !!context?.isAuthenticated && puedeCambiarDeVista(role);
      this.vistaActual = this.muestraCambioVista ? vista : 'staff';
      this.etiquetaCambioVista = etiquetaCambioDeVista(role, this.vistaActual);
      this.homeRoute = this.destinoInicio(context);
    });

    this.subscriptions.push(menuSub, routeSub, vistaSub);
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  /**
   * Verifica si una ruta está activa
   */
  isItemActive(item: MenuItem): boolean {
    return itemMatchesRoute(this.currentRoute, item);
  }

  isSectionOpen(sectionId: string): boolean {
    return this.openSectionIds.has(sectionId);
  }

  toggleSection(sectionId: string): void {
    const next = new Set(this.openSectionIds);
    if (next.has(sectionId)) {
      next.delete(sectionId);
    } else {
      next.add(sectionId);
    }
    this.openSectionIds = next;
    this.persistOpenState();
  }

  /**
   * Enter y Espacio activan el botón. preventDefault evita el click sintético
   * que volvería a invertir el panel; el margen cubre el click de keyup.
   */
  onSectionKeydown(event: KeyboardEvent, sectionId: string): void {
    if (event.key !== 'Enter' && event.key !== ' ') {
      return;
    }
    event.preventDefault();
    this.ignoreClickUntil = Date.now() + 400;
    this.toggleSection(sectionId);
  }

  onSectionClick(sectionId: string): void {
    if (Date.now() < this.ignoreClickUntil) {
      this.ignoreClickUntil = 0;
      return;
    }
    this.toggleSection(sectionId);
  }

  /**
   * Maneja el click en un item del menú
   */
  onItemClick(): void {
    // Cerrar sidebar en mobile después de un pequeño delay
    setTimeout(() => {
      this.menuService.sidebarState$.subscribe(state => {
        if (state.isMobile) {
          this.menuService.closeSidebar();
        }
      }).unsubscribe();
    }, 100);
  }

  /**
   * Emite evento para toggle del sidebar (controla el parent).
   * En mobile el drawer lo abre el estado del MenuService, no la clase del padre.
   */
  onToggleSidebar(): void {
    if (typeof window !== 'undefined' && window.innerWidth < 768) {
      this.menuService.toggleSidebar();
    }
    this.toggleSidebar.emit();
  }

  /**
   * Cierra la sesión. AuthService borra la vista guardada.
   */
  logout(): void {
    this.altaAbierta = false;
    this.authService.logout();
  }

  cambiarVista(): void {
    if (!this.muestraCambioVista || this.altaEnCurso) {
      return;
    }
    const context = this.userContextService.getCurrentContext();
    if (this.vistaActual === 'paciente') {
      this.cerrarAlta();
      this.vistaPanel.activar('staff');
      this.router.navigate([this.destinoInicio(context)]);
      return;
    }
    this.pacienteSesion.tieneFicha().subscribe(tiene => {
      if (tiene) {
        this.irAPaciente();
        return;
      }
      this.altaAbierta = true;
      this.altaError = '';
      this.camposFaltantes = [];
      this.mostrarSidebar();
    });
  }

  cancelarAlta(): void {
    this.cerrarAlta();
  }

  confirmarAlta(): void {
    if (this.altaEnCurso) {
      return;
    }
    if (this.camposFaltantes.some(campo => !this.altaForm[campo].trim())) {
      this.altaError = 'Completá los datos que faltan';
      return;
    }
    if (this.camposFaltantes.includes('dni') && !/^\d+$/.test(this.altaForm.dni.trim())) {
      this.altaError = 'El DNI tiene que ser numérico';
      return;
    }
    this.altaEnCurso = true;
    this.altaError = '';
    this.pacienteSesion.crear(this.cuerpoAlta()).subscribe({
      next: () => {
        this.altaEnCurso = false;
        this.cerrarAlta();
        this.irAPaciente();
      },
      error: (error: unknown) => {
        this.altaEnCurso = false;
        const campos = camposDeError(error);
        if (campos.length) {
          this.camposFaltantes = campos;
          this.altaError = '';
          this.mostrarSidebar();
          return;
        }
        this.altaError = mensajeDeError(error);
      }
    });
  }

  etiquetaCampo(campo: CampoFicha): string {
    switch (campo) {
      case 'nombre':
        return 'Nombre';
      case 'apellido':
        return 'Apellido';
      case 'dni':
        return 'DNI';
      case 'telefono':
        return 'Teléfono';
    }
  }

  /**
   * Obtiene el nombre display del rol
   */
  getRoleDisplay(role: string): string {
    const roleMap: Record<string, string> = {
      'PACIENTE': 'Paciente',
      'MEDICO': 'Médico',
      'OPERADOR': 'Operador',
      'ADMINISTRADOR': 'Administrador',
      'SUPERADMIN': 'Super Administrador'
    };
    return roleMap[role] || role;
  }

  private syncUserPreference(context: UserContext | null): void {
    this.homeRoute = this.destinoInicio(context);
    const nextKey = context?.isAuthenticated ? this.userStorageKey(context) : null;
    if (!nextKey) {
      this.userKey = null;
      this.preference = null;
      this.sectionSignature = '';
      this.openSectionIds = new Set();
      return;
    }

    const stored = readMenuSectionState(this.sessionStorage(), nextKey);
    const signature = this.signatureOf(this.sections);
    const userChanged = nextKey !== this.userKey;
    const preferenceChanged = !this.samePreference(stored, this.preference);
    const sectionsChanged = signature !== this.sectionSignature;

    this.userKey = nextKey;
    if (userChanged || preferenceChanged || sectionsChanged) {
      this.preference = stored;
      this.sectionSignature = signature;
      this.rebuildOpenState();
    }
  }

  private rebuildOpenState(): void {
    if (!this.userKey) {
      this.openSectionIds = new Set();
      return;
    }
    this.openSectionIds = new Set(
      resolveOpenSectionIds(this.sections, this.currentRoute, this.preference)
    );
  }

  private persistOpenState(): void {
    if (!this.userKey) {
      return;
    }

    const preference: Record<string, boolean> = { ...(this.preference ?? {}) };
    for (const section of this.sections) {
      preference[section.id] = this.openSectionIds.has(section.id);
    }
    this.preference = preference;
    this.sectionSignature = this.signatureOf(this.sections);
    writeMenuSectionState(this.sessionStorage(), this.userKey, preference);
  }

  private userStorageKey(context: UserContext): string {
    const email = context.email?.trim();
    if (email) {
      return email;
    }
    if (context.id) {
      return `id:${context.id}`;
    }
    return 'session';
  }

  private signatureOf(sections: MenuSection[]): string {
    return sections
      .map(section => `${section.id}:${section.items.map(item => item.route ?? '').join(',')}`)
      .join('|');
  }

  private samePreference(
    left: Record<string, boolean> | null,
    right: Record<string, boolean> | null
  ): boolean {
    if (left === right) {
      return true;
    }
    if (!left || !right) {
      return false;
    }
    const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
    for (const key of keys) {
      if ((left[key] ?? false) !== (right[key] ?? false)) {
        return false;
      }
    }
    return true;
  }

  private destinoInicio(context: UserContext | null): string {
    if (this.vistaActual === 'paciente' && context?.isAuthenticated && puedeCambiarDeVista(context.primaryRole)) {
      return '/paciente-dashboard';
    }
    return context?.isAuthenticated ? homeRouteForRole(context.primaryRole) : '/';
  }

  private irAPaciente(): void {
    this.vistaPanel.activar('paciente');
    this.router.navigate(['/paciente-dashboard']);
  }

  private cerrarAlta(): void {
    this.altaAbierta = false;
    this.altaError = '';
    this.camposFaltantes = [];
    this.altaForm = { nombre: '', apellido: '', dni: '', telefono: '' };
  }

  private cuerpoAlta(): Partial<Paciente> {
    const cuerpo: Partial<Paciente> = {};
    for (const campo of this.camposFaltantes) {
      const valor = this.altaForm[campo].trim();
      if (campo === 'dni') {
        cuerpo.dni = Number(valor);
      } else if (campo === 'nombre') {
        cuerpo.nombre = valor;
      } else if (campo === 'apellido') {
        cuerpo.apellido = valor;
      } else {
        cuerpo.telefono = valor;
      }
    }
    return cuerpo;
  }

  private mostrarSidebar(): void {
    if (this.host.nativeElement.classList.contains('sidebar-closed')) {
      this.toggleSidebar.emit();
    }
    this.menuService.openSidebar();
  }

  private sessionStorage(): Storage | null {
    try {
      return typeof sessionStorage === 'undefined' ? null : sessionStorage;
    } catch {
      return null;
    }
  }
}

type CampoFicha = 'nombre' | 'apellido' | 'dni' | 'telefono';

const CAMPOS_FICHA: CampoFicha[] = ['nombre', 'apellido', 'dni', 'telefono'];

function camposDeError(error: unknown): CampoFicha[] {
  if (!(error instanceof HttpErrorResponse) || error.status !== 400) {
    return [];
  }
  const campos = error.error?.data?.campos;
  if (!Array.isArray(campos)) {
    return [];
  }
  return campos.filter((campo): campo is CampoFicha =>
    CAMPOS_FICHA.includes(campo as CampoFicha)
  );
}

function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const texto = error.error?.status_text;
    if (typeof texto === 'string' && texto.trim()) {
      return texto;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor. Reintentá.';
    }
  }
  return 'No se pudo crear la ficha';
}
