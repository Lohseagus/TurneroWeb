import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { PacienteNotificacionesComponent } from './paciente-notificaciones.component';
import { PacienteSesionService } from './paciente-sesion.service';
import { NotificacionService } from '../services/notificacion.service';

class SesionFalsa {
  idPaciente: number | null = 7;
  falla: unknown = null;
  invalidar(): void {}
  id() {
    return this.falla ? throwError(() => this.falla) : of(this.idPaciente);
  }
}

class NotificacionesFalsas {
  pedidos: number[] = [];
  contadorNoLeidas$ = of(0);
  private pagina = new Subject<any>();

  obtenerNotificacionesPorPaciente(id: number) {
    this.pedidos.push(id);
    return this.pagina.asObservable();
  }

  actualizarContador(): void {}

  emitirVacias(): void {
    this.pagina.next({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true });
  }

  fallar(error: unknown): void {
    this.pagina.error(error);
  }
}

describe('PacienteNotificacionesComponent', () => {
  let fixture: ComponentFixture<PacienteNotificacionesComponent>;
  let sesion: SesionFalsa;
  let notificaciones: NotificacionesFalsas;

  async function montar(): Promise<void> {
    localStorage.clear();
    sesion = new SesionFalsa();
    notificaciones = new NotificacionesFalsas();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PacienteNotificacionesComponent],
      providers: [
        { provide: PacienteSesionService, useValue: sesion },
        { provide: NotificacionService, useValue: notificaciones },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { data: { embebido: true }, queryParamMap: convertToParamMap({}) }
          }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(PacienteNotificacionesComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    localStorage.clear();
  });

  it('carga las notificaciones del médico en vista de paciente sin id guardado', async () => {
    await montar();

    expect(localStorage.getItem('pacienteId')).toBeNull();
    expect(notificaciones.pedidos).toEqual([7]);

    notificaciones.emitirVacias();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('No hay notificaciones');
    expect(texto).not.toContain('Cargando notificaciones');
  });

  it('un error deja un mensaje y apaga el spinner', async () => {
    await montar();
    notificaciones.fallar(new HttpErrorResponse({
      status: 500,
      error: { status_text: 'No se pudo leer la bandeja.' }
    }));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(fixture.componentInstance.loading).toBe(false);
    expect(texto).toContain('No se pudo leer la bandeja.');
    expect(texto).not.toContain('Cargando notificaciones');
    expect(texto).toContain('Reintentar');
  });

  it('sin ficha no se queda en Cargando', async () => {
    await montar();
    fixture.destroy();
    sesion.idPaciente = null;
    fixture = TestBed.createComponent(PacienteNotificacionesComponent);
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('No encontramos tu ficha de paciente.');
    expect(texto).not.toContain('Cargando notificaciones');
  });
});
