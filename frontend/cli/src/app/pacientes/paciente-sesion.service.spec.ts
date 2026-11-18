import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { Role } from '../inicio-sesion/auth.service';
import { UserContext, UserContextService } from '../services/user-context.service';
import { PacienteSesionService } from './paciente-sesion.service';

class UsuariosFalsos {
  context: UserContext = {
    email: 'Medico@Turnero.com',
    nombre: 'Juan',
    primaryRole: Role.MEDICO,
    allRoles: [Role.MEDICO, Role.PACIENTE],
    isAuthenticated: true
  };

  userContext$ = { subscribe: () => ({ unsubscribe() {} }) };

  getCurrentContext(): UserContext {
    return this.context;
  }
}

describe('PacienteSesionService', () => {
  let service: PacienteSesionService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        PacienteSesionService,
        { provide: UserContextService, useClass: UsuariosFalsos }
      ]
    });
    service = TestBed.inject(PacienteSesionService);
    http = TestBed.inject(HttpTestingController);
  });

  it('comparte una sola lectura de la ficha y deja el id como caché', () => {
    const recibidas: Array<number | null> = [];
    service.resolver().subscribe(ficha => recibidas.push(ficha?.id ?? null));
    service.tieneFicha().subscribe(tiene => recibidas.push(tiene ? 1 : 0));

    const req = http.expectOne('rest/pacientes/me');
    expect(req.request.method).toBe('GET');
    req.flush({
      status_code: 200,
      status_text: 'Paciente de la sesión',
      data: {
        id: 9,
        nombre: 'Juan',
        apellido: 'Pérez',
        email: 'medico@turnero.com',
        telefono: '',
        dni: 1,
        fechaNacimiento: ''
      }
    });

    expect(recibidas).toEqual([9, 1]);
    expect(localStorage.getItem('pacienteId')).toBe('9');
    http.verify();
  });

  it('un 404 es ausencia de ficha, no un error', () => {
    let ficha: unknown = 'todavia-no';
    let fallo = false;
    service.resolver().subscribe({
      next: valor => ficha = valor,
      error: () => fallo = true
    });

    http.expectOne('rest/pacientes/me').flush(
      { status_code: 404, status_text: 'Tu cuenta no tiene ficha de paciente', data: null },
      { status: 404, statusText: 'Not Found' }
    );

    expect(fallo).toBe(false);
    expect(ficha).toBeNull();
    expect(localStorage.getItem('pacienteId')).toBeNull();
    http.verify();
  });

  it('crea la ficha propia sin mandar el email de otra persona', () => {
    let fichaId: number | null = null;
    service.crear({ telefono: '2804000000' }).subscribe(ficha => fichaId = ficha.id);

    const req = http.expectOne('rest/pacientes/me');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ telefono: '2804000000' });
    expect(req.request.body.email).toBeUndefined();
    req.flush({
      status_code: 201,
      status_text: 'Creaste tu ficha de paciente',
      data: {
        id: 15,
        nombre: 'Juan',
        apellido: 'Pérez',
        email: 'medico@turnero.com',
        telefono: '2804000000',
        dni: 33333333,
        fechaNacimiento: ''
      }
    });

    expect(fichaId).toBe(15);
    expect(localStorage.getItem('pacienteId')).toBe('15');
    http.verify();
  });
});
