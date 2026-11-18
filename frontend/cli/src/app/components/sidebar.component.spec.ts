import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SidebarComponent } from './sidebar.component';
import { MenuService } from '../services/menu.service';
import { VistaPanelService } from '../services/vista-panel.service';
import { UserContext, UserContextService } from '../services/user-context.service';
import { AuthService, Role } from '../inicio-sesion/auth.service';
import { PacienteSesionService } from '../pacientes/paciente-sesion.service';
import { clearAllVistaPanel, readVistaPanel, writeVistaPanel } from '../config/vista-panel-storage';

function contexto(role: Role, email = 'ana@turnero.com'): UserContext {
  return {
    email,
    nombre: 'Ana Martínez',
    primaryRole: role,
    allRoles: role === Role.PACIENTE ? [Role.PACIENTE] : [role, Role.PACIENTE],
    isAuthenticated: true
  };
}

class Usuarios {
  subject = new BehaviorSubject<UserContext>(contexto(Role.MEDICO));
  userContext$ = this.subject.asObservable();
  getCurrentContext(): UserContext {
    return this.subject.value;
  }
}

class SesionFalsa {
  tiene = true;
  error: HttpErrorResponse | null = null;
  creadas: unknown[] = [];
  invalidadas = 0;

  tieneFicha() {
    return of(this.tiene);
  }

  invalidar(): void {
    this.invalidadas += 1;
  }

  crear(datos: unknown) {
    this.creadas.push(datos);
    if (this.error) {
      const error = this.error;
      this.error = null;
      return throwError(() => error);
    }
    return of({
      id: 9,
      nombre: 'Ana',
      apellido: 'Martínez',
      email: 'ana@turnero.com',
      telefono: '299',
      dni: 1,
      fechaNacimiento: ''
    });
  }
}

class AuthFalsa {
  logout = vi.fn();
}

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let usuarios: Usuarios;
  let sesion: SesionFalsa;
  let auth: AuthFalsa;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();
    localStorage.clear();
    usuarios = new Usuarios();
    sesion = new SesionFalsa();
    auth = new AuthFalsa();

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        MenuService,
        VistaPanelService,
        { provide: UserContextService, useValue: usuarios },
        { provide: PacienteSesionService, useValue: sesion },
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
  });

  function texto(): string {
    return fixture.nativeElement.textContent;
  }

  function botonVista(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('.sidebar__footer-btn--vista');
  }

  it('un médico, un operador, un admin y un superadmin ven Cambiar a paciente; un paciente no', () => {
    expect(botonVista().textContent).toContain('Cambiar a paciente');
    expect(texto()).toContain('Panel de Médico');
    expect(texto()).not.toContain('Mis turnos');

    for (const role of [Role.OPERADOR, Role.ADMINISTRADOR, Role.SUPERADMIN]) {
      usuarios.subject.next(contexto(role));
      fixture.detectChanges();
      expect(botonVista().textContent).toContain('Cambiar a paciente');
    }

    usuarios.subject.next(contexto(Role.PACIENTE, 'paciente@turnero.com'));
    fixture.detectChanges();
    expect(botonVista()).toBeNull();
    expect(texto()).not.toContain('Cambiar a paciente');
    expect(texto()).not.toContain('Cambiar a médico');
  });

  it('cambiar a paciente deja solo el portal y volver restaura el panel del rol', () => {
    botonVista().click();
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/paciente-dashboard']);
    expect(sesion.invalidadas).toBe(1);
    expect(botonVista().textContent).toContain('Cambiar a médico');
    expect(texto()).toContain('Inicio');
    expect(texto()).toContain('Mis turnos');
    expect(texto()).toContain('Mi salud');
    expect(texto()).toContain('Mi cuenta');
    expect(texto()).not.toContain('Panel de Médico');

    botonVista().click();
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/medico-dashboard']);
    expect(botonVista().textContent).toContain('Cambiar a paciente');
    expect(texto()).toContain('Panel de Médico');
    expect(texto()).not.toContain('Mis turnos');
  });

  it('el texto de vuelta depende del rol', () => {
    usuarios.subject.next(contexto(Role.OPERADOR));
    fixture.detectChanges();
    botonVista().click();
    fixture.detectChanges();
    expect(botonVista().textContent).toContain('Cambiar a operador');

    usuarios.subject.next(contexto(Role.ADMINISTRADOR));
    fixture.detectChanges();
    expect(botonVista().textContent).toContain('Cambiar a administración');

    usuarios.subject.next(contexto(Role.SUPERADMIN));
    fixture.detectChanges();
    expect(botonVista().textContent).toContain('Cambiar a superadmin');
  });

  it('cerrar sesión pide el logout, que es el que borra la vista', () => {
    botonVista().click();
    fixture.detectChanges();
    expect(readVistaPanel(sessionStorage, 'ana@turnero.com')).toBe('paciente');

    const cerrar = [...fixture.nativeElement.querySelectorAll('.sidebar__footer-btn')]
      .find((boton: HTMLButtonElement) => boton.textContent?.includes('Cerrar Sesión'));
    cerrar.click();

    expect(auth.logout).toHaveBeenCalledTimes(1);
    clearAllVistaPanel(sessionStorage);
    clearAllVistaPanel(localStorage);
    usuarios.subject.next({
      email: '',
      nombre: '',
      primaryRole: Role.PACIENTE,
      allRoles: [],
      isAuthenticated: false
    });
    fixture.detectChanges();

    expect(readVistaPanel(sessionStorage, 'ana@turnero.com')).toBe('staff');
    expect(TestBed.inject(VistaPanelService).actual()).toBe('staff');
    expect(fixture.nativeElement.querySelector('.sidebar__footer-btn--vista')).toBeNull();
  });

  it('sin ficha pide confirmación y recién después crea la propia', () => {
    sesion.tiene = false;
    botonVista().click();
    fixture.detectChanges();

    expect(router.navigate).not.toHaveBeenCalled();
    expect(texto()).toContain('¿La creamos con tus datos?');
    expect(sesion.creadas).toEqual([]);

    const crear = [...fixture.nativeElement.querySelectorAll('.sidebar__footer-btn')]
      .find((boton: HTMLButtonElement) => boton.textContent?.includes('Crear mi ficha'));
    crear.click();
    fixture.detectChanges();

    expect(sesion.creadas).toEqual([{}]);
    expect(botonVista().textContent).toContain('Cambiar a médico');
    expect(router.navigate).toHaveBeenCalledWith(['/paciente-dashboard']);
  });

  it('completa el dato que falta y después pasa a la vista de paciente', () => {
    sesion.tiene = false;
    sesion.error = new HttpErrorResponse({
      status: 400,
      error: { status_text: 'Completá los datos que faltan', data: { campos: ['telefono'] } }
    });
    botonVista().click();
    fixture.detectChanges();
    const crear = () => [...fixture.nativeElement.querySelectorAll('button')]
      .find((boton: HTMLButtonElement) => boton.textContent?.includes('Crear mi ficha'));
    crear().click();
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('#alta-telefono') as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(texto()).toContain('Teléfono');
    input.value = '2804111222';
    input.dispatchEvent(new Event('input'));
    crear().click();
    fixture.detectChanges();

    expect(sesion.creadas[1]).toEqual({ telefono: '2804111222' });
    expect(botonVista().textContent).toContain('Cambiar a médico');
  });

  it('si la cuenta está en solo lectura no cambia a la vista de paciente', () => {
    sesion.tiene = false;
    sesion.error = new HttpErrorResponse({
      status: 403,
      error: { status_text: 'La cuenta es de solo lectura.', code: 'SUSCRIPCION_SOLO_LECTURA' }
    });
    botonVista().click();
    fixture.detectChanges();
    const crear = [...fixture.nativeElement.querySelectorAll('button')]
      .find((boton: HTMLButtonElement) => boton.textContent?.includes('Crear mi ficha'));
    crear.click();
    fixture.detectChanges();

    expect(texto()).toContain('solo lectura');
    expect(botonVista().textContent).toContain('Cambiar a paciente');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});

describe('vista recordada al entrar', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    localStorage.clear();
    writeVistaPanel(sessionStorage, 'ana@turnero.com', 'paciente');
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        MenuService,
        VistaPanelService,
        { provide: UserContextService, useValue: new Usuarios() },
        { provide: PacienteSesionService, useValue: new SesionFalsa() },
        { provide: AuthService, useValue: new AuthFalsa() }
      ]
    }).compileComponents();
  });

  it('abre directo en el portal y el botón dice Cambiar a médico', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
    const boton = fixture.nativeElement.querySelector('.sidebar__footer-btn--vista');
    expect(boton.textContent).toContain('Cambiar a médico');
    expect(fixture.nativeElement.textContent).toContain('Mis turnos');
    expect(fixture.nativeElement.textContent).not.toContain('Panel de Médico');
    expect(readVistaPanel(sessionStorage, 'ana@turnero.com')).toBe('paciente');
  });
});
