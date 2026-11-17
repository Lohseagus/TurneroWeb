import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { MedicoSesion, MedicoSesionService, MedicoSinFichaError } from './medico-sesion.service';

describe('MedicoSesionService', () => {
  let service: MedicoSesionService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MedicoSesionService]
    });
    service = TestBed.inject(MedicoSesionService);
    http = TestBed.inject(HttpTestingController);
  });

  it('pide la ficha al backend y deja el id solo como caché', () => {
    const recibidas: MedicoSesion[] = [];
    service.resolver().subscribe(sesion => recibidas.push(sesion));
    service.resolver().subscribe(sesion => recibidas.push(sesion));

    const req = http.expectOne('rest/medicos/me');
    expect(req.request.method).toBe('GET');
    req.flush({
      status_code: 200,
      status_text: 'Médico de la sesión',
      data: {
        id: 7,
        nombre: 'Juan',
        apellido: 'Pérez',
        email: 'medico@turnero.com',
        matricula: 'MP-1',
        especialidades: [],
        staffMedicoId: 15,
        staff: [{ id: 15, centroId: 1, centroNombre: 'Clínica Santa María', especialidadId: 3, especialidadNombre: 'Clínica' }]
      }
    });

    expect(recibidas).toHaveLength(2);
    expect(recibidas[0].id).toBe(7);
    expect(recibidas[0].staffMedicoId).toBe(15);
    expect(localStorage.getItem('medicoId')).toBe('7');
    expect(localStorage.getItem('staffMedicoId')).toBe('15');
    http.verify();
  });

  it('traduce el 404 de un médico sin ficha', () => {
    let error: unknown;
    service.resolver().subscribe({ error: err => error = err });

    http.expectOne('rest/medicos/me').flush(
      {
        status_code: 404,
        status_text: 'Tu usuario tiene rol de médico, pero no hay una ficha profesional vinculada. Pedile a un administrador del centro que te asocie.',
        data: null
      },
      { status: 404, statusText: 'Not Found' }
    );

    expect(error).toBeInstanceOf(MedicoSinFichaError);
    expect((error as Error).message).toContain('ficha profesional');
    expect(localStorage.getItem('medicoId')).toBeNull();
    http.verify();
  });
});
