import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { TurnoService } from '../../../turnos/turno.service';
import { CentroResumenTabComponent } from './centro-resumen-tab.component';

describe('CentroResumenTabComponent', () => {
  let fixture: ComponentFixture<CentroResumenTabComponent>;

  async function montar(respuesta: unknown): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CentroResumenTabComponent],
      providers: [
        provideRouter([]),
        { provide: TurnoService, useValue: { resumenOperador: () => respuesta } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(CentroResumenTabComponent);
    fixture.detectChanges();
  }

  it('muestra los KPIs del centro y el enlace a Por confirmar', async () => {
    await montar(of({
      status_code: 200,
      data: {
        turnosHoy: 6,
        pendientesConfirmar: 2,
        listaEspera: 4,
        cancelacionesSemana: 1,
        proximosHoy: [{ id: 9, hora: '16:00', paciente: 'Ana Pérez', medico: 'Luis Gómez', especialidad: 'Clínica', estado: 'PROGRAMADO' }]
      }
    }));

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Turnos de hoy');
    expect(texto).toContain('6');
    expect(texto).toContain('Pendientes de confirmar');
    expect(texto).toContain('2');
    expect(texto).toContain('Personas en lista de espera');
    expect(texto).toContain('4');
    expect(texto).toContain('Cancelaciones de la semana');
    expect(texto).toContain('Ana Pérez');
    const pendientes = [...fixture.nativeElement.querySelectorAll('a')].find((enlace: HTMLAnchorElement) =>
      enlace.textContent?.includes('Pendientes de confirmar')
    );
    expect(pendientes?.getAttribute('href')).toContain('tab=por-confirmar');
  });

  it('avisa si el resumen no carga', async () => {
    await montar(throwError(() => new Error('falló')));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar el resumen del centro.');
  });
});
