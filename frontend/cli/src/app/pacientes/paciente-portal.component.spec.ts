import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { Role } from '../inicio-sesion/auth.service';
import { UserContext, UserContextService } from '../services/user-context.service';
import { MENSAJE_SIN_FICHA_PACIENTE } from '../config/menu.config';
import { Paciente } from './paciente';
import { PacientePortalComponent } from './paciente-portal.component';
import { PacienteSesionService } from './paciente-sesion.service';

class UsuariosFalsos {
  context: UserContext = {
    email: 'medico@turnero.com',
    nombre: 'Juan Pérez',
    primaryRole: Role.MEDICO,
    allRoles: [Role.MEDICO, Role.PACIENTE],
    isAuthenticated: true
  };

  getCurrentContext(): UserContext {
    return this.context;
  }
}

class SesionFalsa {
  ficha: Paciente | null = null;
  falla: HttpErrorResponse | null = null;
  llamadas = 0;
  invalidadas = 0;

  resolver() {
    this.llamadas += 1;
    if (this.falla) {
      return throwError(() => this.falla);
    }
    return of(this.ficha);
  }

  invalidar(): void {
    this.invalidadas += 1;
  }
}

describe('PacientePortalComponent', () => {
  let fixture: ComponentFixture<PacientePortalComponent>;
  let usuarios: UsuariosFalsos;
  let sesion: SesionFalsa;

  beforeEach(async () => {
    usuarios = new UsuariosFalsos();
    sesion = new SesionFalsa();
    await TestBed.configureTestingModule({
      imports: [PacientePortalComponent],
      providers: [
        provideRouter([]),
        { provide: UserContextService, useValue: usuarios },
        { provide: PacienteSesionService, useValue: sesion }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(PacientePortalComponent);
  });

  it('un médico sin ficha ve el vacío y no queda en Cargando', () => {
    fixture.detectChanges();

    expect(sesion.llamadas).toBe(1);
    expect(fixture.componentInstance.estado).toBe('sin-ficha');
    expect(fixture.nativeElement.textContent).toContain(MENSAJE_SIN_FICHA_PACIENTE);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando');
  });

  it('un médico con ficha entra al portal', () => {
    sesion.ficha = {
      id: 9,
      nombre: 'Juan',
      apellido: 'Pérez',
      email: 'medico@turnero.com',
      telefono: '',
      dni: 1,
      fechaNacimiento: ''
    };

    fixture.detectChanges();

    expect(fixture.componentInstance.estado).toBe('portal');
    expect(fixture.nativeElement.textContent).not.toContain(MENSAJE_SIN_FICHA_PACIENTE);
  });

  it('un PACIENTE primario no consulta la ficha y entra directo', () => {
    usuarios.context = {
      ...usuarios.context,
      primaryRole: Role.PACIENTE,
      allRoles: [Role.PACIENTE]
    };

    fixture.detectChanges();

    expect(sesion.llamadas).toBe(0);
    expect(fixture.componentInstance.estado).toBe('portal');
  });

  it('un error de red corta el cargando y el reintento vuelve a pedir la ficha', () => {
    sesion.falla = new HttpErrorResponse({ status: 0 });
    fixture.detectChanges();

    expect(fixture.componentInstance.estado).toBe('error');
    expect(fixture.nativeElement.textContent).not.toContain('Cargando');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');

    sesion.falla = null;
    fixture.componentInstance.reintentar();

    expect(sesion.invalidadas).toBe(1);
    expect(sesion.llamadas).toBe(2);
    expect(fixture.componentInstance.estado).toBe('sin-ficha');
  });
});
