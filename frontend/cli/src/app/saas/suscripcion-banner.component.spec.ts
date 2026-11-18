import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { Role } from '../inicio-sesion/auth.service';
import { UserContext, UserContextService } from '../services/user-context.service';
import { SuscripcionBannerComponent } from './suscripcion-banner.component';

describe('SuscripcionBannerComponent', () => {
  let fixture: ComponentFixture<SuscripcionBannerComponent>;
  let http: HttpTestingController;
  let contexto: BehaviorSubject<UserContext>;

  beforeEach(() => {
    contexto = new BehaviorSubject<UserContext>(usuario(Role.PACIENTE, false));
    TestBed.configureTestingModule({
      imports: [SuscripcionBannerComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: UserContextService, useValue: { userContext$: contexto.asObservable() } }
      ]
    });
    fixture = TestBed.createComponent(SuscripcionBannerComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('no consulta el aviso si quien entra es un paciente', () => {
    http.expectNone('rest/saas/aviso');
    expect(fixture.nativeElement.textContent).not.toContain('solo lectura');
  });

  it('muestra el banner de solo lectura al operador, sin el enlace a Mi plan', () => {
    contexto.next(usuario(Role.OPERADOR, true));
    fixture.detectChanges();
    const req = http.expectOne('rest/saas/aviso');
    req.flush({
      status_code: 200,
      status_text: 'ok',
      data: {
        soloLectura: true,
        sinSuscripcion: false,
        estado: 'MOROSA',
        mensaje: 'La suscripción está morosa. La cuenta es de solo lectura.',
        diasTrialRestantes: null
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('La suscripción está morosa');
    expect(fixture.nativeElement.querySelector('a[href="/mi-plan"]')).toBeNull();
  });

  it('al administrador le deja ir a Mi plan', () => {
    contexto.next(usuario(Role.ADMINISTRADOR, true));
    fixture.detectChanges();
    http.expectOne('rest/saas/aviso').flush({
      status_code: 200,
      status_text: 'ok',
      data: {
        soloLectura: true,
        sinSuscripcion: true,
        estado: null,
        mensaje: 'Este centro no tiene una suscripción activa.',
        diasTrialRestantes: null
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('a[href="/mi-plan"]')?.textContent).toContain('Ir a Mi plan');
  });

  it('avisa los días de prueba al médico', () => {
    contexto.next(usuario(Role.MEDICO, true));
    fixture.detectChanges();
    http.expectOne('rest/saas/aviso').flush({
      status_code: 200,
      status_text: 'ok',
      data: {
        soloLectura: false,
        sinSuscripcion: false,
        estado: 'TRIAL',
        mensaje: '',
        diasTrialRestantes: 12
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('te quedan 12 días');
  });

  function usuario(rol: Role, autenticado: boolean): UserContext {
    return {
      email: `${rol}@cheturno.test`,
      nombre: 'Prueba',
      primaryRole: rol,
      allRoles: [rol],
      isAuthenticated: autenticado
    };
  }
});
