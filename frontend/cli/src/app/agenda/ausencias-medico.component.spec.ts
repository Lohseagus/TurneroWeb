import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { AusenciasMedicoComponent } from './ausencias-medico.component';
import { AusenciaService } from './ausencia.service';
import { MedicoSesionService } from '../medicos/medico-sesion.service';
import { StaffMedicoService } from '../staffMedicos/staffMedico.service';
import { CentroAtencionService } from '../centrosAtencion/centroAtencion.service';

const anticipacion = {
  status_code: 200,
  status_text: 'ok',
  data: { dias: 7, fechaMinima: '2026-12-01' }
};

describe('AusenciasMedicoComponent', () => {
  let fixture: ComponentFixture<AusenciasMedicoComponent>;

  const ausenciaService = {
    anticipacion: () => of(anticipacion),
    listar: () => of({ status_code: 200, status_text: 'ok', data: [] }),
    previsualizar: () => of({ status_code: 200, status_text: 'ok', data: { cancelados: [], requierenGestionManual: [], mensaje: '' } }),
    confirmar: () => of({ status_code: 200, status_text: 'ok', data: { cancelados: [], requierenGestionManual: [], mensaje: 'Lista' } })
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AusenciasMedicoComponent],
      providers: [
        { provide: AusenciaService, useValue: ausenciaService },
        {
          provide: MedicoSesionService,
          useValue: {
            resolver: () => of({
              staff: [{ id: 1, centroId: 10, centroNombre: 'Santa María', especialidadNombre: 'Cardiología' }]
            })
          }
        },
        { provide: StaffMedicoService, useValue: { getByCentroAtencion: () => of({ data: [] }) } },
        { provide: CentroAtencionService, useValue: { all: () => of({ data: [{ id: 10, nombre: 'Santa María' }] }) } }
      ]
    }).compileComponents();
  });

  function crear(modo: 'medico' | 'admin') {
    fixture = TestBed.createComponent(AusenciasMedicoComponent);
    fixture.componentInstance.modo = modo;
    fixture.detectChanges();
  }

  it('al médico le muestra el mínimo y no deja elegir una fecha anterior', () => {
    crear('medico');
    const ayuda: HTMLElement = fixture.nativeElement.querySelector('[data-testid="ayuda-anticipacion"]');
    const desde: HTMLInputElement = fixture.nativeElement.querySelector('input[name="desde"]');
    expect(ayuda.textContent).toContain('al menos 7 días');
    expect(desde.min).toBe('2026-12-01');
    expect(fixture.nativeElement.querySelector('input[name="urgente"]')).toBeNull();
  });

  it('en administración, una fecha anterior al mínimo pide la urgencia y el motivo', () => {
    crear('admin');
    fixture.componentInstance.fechaInicio = '2026-11-20';
    fixture.componentInstance.fechaFin = '2026-11-21';
    fixture.detectChanges();

    const urgencia: HTMLInputElement = fixture.nativeElement.querySelector('input[name="urgente"]');
    const motivo: HTMLInputElement = fixture.nativeElement.querySelector('input[name="motivo"]');
    expect(urgencia).not.toBeNull();
    expect(motivo.required).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Solo la administración puede cargarla');
  });
});
