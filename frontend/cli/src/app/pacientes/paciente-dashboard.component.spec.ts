import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PacienteDashboardComponent } from './paciente-dashboard.component';
import { PacienteSesionService } from './paciente-sesion.service';
import { TurnoService } from '../turnos/turno.service';
import { NotificacionService } from '../services/notificacion.service';
import { AuthService } from '../inicio-sesion/auth.service';
import { EncuestaService } from '../services/encuesta.service';
import { ModalService } from '../modal/modal.service';
import { RecetaService } from '../historia-clinica/receta.service';

class SesionFalsa {
  idPaciente: number | null = 42;
  falla: unknown = null;
  invalidar(): void {}
  id() {
    return this.falla ? throwError(() => this.falla) : of(this.idPaciente);
  }
}

class TurnosFalsos {
  pedidos: number[] = [];
  private respuesta = new Subject<any>();

  getByPacienteId(id: number) {
    this.pedidos.push(id);
    return this.respuesta.asObservable();
  }

  updateEstado() {
    return of({});
  }

  emitir(data: unknown) {
    this.respuesta.next({ data });
  }

  fallar(error: unknown) {
    this.respuesta.error(error);
  }
}

describe('PacienteDashboardComponent con la ficha de la sesión', () => {
  let fixture: ComponentFixture<PacienteDashboardComponent>;
  let sesion: SesionFalsa;
  let turnos: TurnosFalsos;

  async function montar(): Promise<void> {
    localStorage.clear();
    sesion = new SesionFalsa();
    turnos = new TurnosFalsos();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PacienteDashboardComponent],
      providers: [
        { provide: PacienteSesionService, useValue: sesion },
        { provide: TurnoService, useValue: turnos },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        {
          provide: NotificacionService,
          useValue: {
            contadorNoLeidas$: of(0),
            contarNotificacionesNoLeidas: () => of(0),
            obtenerNotificacionesNoLeidas: () => of([])
          }
        },
        {
          provide: AuthService,
          useValue: { getUserEmail: () => 'juan@turnero.com', getUserName: () => 'Juan' }
        },
        { provide: EncuestaService, useValue: { isEncuestaPendiente: () => of({ data: false }) } },
        { provide: ModalService, useValue: { alert() {}, confirm: () => Promise.resolve() } },
        { provide: RecetaService, useValue: { mias: () => of({ data: [] }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(PacienteDashboardComponent);
    fixture.componentRef.setInput('embebido', true);
    fixture.detectChanges();
  }

  beforeEach(() => {
    localStorage.clear();
  });

  it('un médico en vista de paciente carga los turnos sin pacienteId en el storage', async () => {
    await montar();

    expect(localStorage.getItem('pacienteId')).toBeNull();
    expect(turnos.pedidos).toEqual([42]);
    expect(fixture.nativeElement.textContent).toContain('Cargando turnos');

    turnos.emitir([]);
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('No tenés turnos próximos');
    expect(texto).not.toContain('Cargando turnos');
  });

  it('un error de turnos apaga el spinner y muestra el mensaje', async () => {
    await montar();
    turnos.fallar(new HttpErrorResponse({
      status: 500,
      error: { status_text: 'No se pudo leer la agenda.' }
    }));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(fixture.componentInstance.isLoadingTurnos).toBe(false);
    expect(texto).toContain('No se pudo leer la agenda.');
    expect(texto).not.toContain('Cargando turnos');
    expect(texto).toContain('Reintentar');
  });

  it('si no se puede leer la ficha no se queda cargando', async () => {
    await montar();
    fixture.destroy();
    sesion.falla = new HttpErrorResponse({ status: 0 });
    sesion.idPaciente = null;
    fixture = TestBed.createComponent(PacienteDashboardComponent);
    fixture.componentRef.setInput('embebido', true);
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('No hay conexión con el servidor');
    expect(texto).not.toContain('Cargando turnos');
  });
});
