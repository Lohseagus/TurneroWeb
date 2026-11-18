import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { MedicoConsultaComponent } from './medico-consulta.component';
import { RecetasConsultaComponent } from './recetas-consulta.component';
import { HceService } from './hce.service';

@Component({ selector: 'app-recetas-consulta', standalone: true, template: '' })
class RecetasFalso {
  @Input() consulta: unknown;
}

describe('MedicoConsultaComponent', () => {
  let fixture: ComponentFixture<MedicoConsultaComponent>;
  let llamadas: string[];

  beforeEach(async () => {
    llamadas = [];
    const hce = {
      contextoTurno: () => of({ data: {
        turnoId: 9, pacienteId: 5, pacienteNombre: 'Joaquín', pacienteApellido: 'Acuña', dni: 19481640,
        centroId: 1, centroNombre: 'Clínica Santa María', especialidadId: 1, especialidadNombre: 'Cardiología'
      } }),
      crearConsulta: () => { llamadas.push('crear'); return of({ data: { id: 77, estado: 'BORRADOR', pacienteId: 5 } }); },
      actualizarConsulta: () => { llamadas.push('actualizar'); return of({ data: { id: 77, estado: 'BORRADOR' } }); },
      cerrarConsulta: () => { llamadas.push('cerrar'); return of({ data: { id: 77, estado: 'CERRADA', pacienteId: 5 } }); }
    };
    await TestBed.configureTestingModule({
      imports: [MedicoConsultaComponent],
      providers: [
        { provide: HceService, useValue: hce },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        { provide: ActivatedRoute, useValue: { snapshot: {
          paramMap: convertToParamMap({}), queryParamMap: convertToParamMap({ turnoId: '9' })
        } } }
      ]
    })
      .overrideComponent(MedicoConsultaComponent, {
        remove: { imports: [RecetasConsultaComponent] },
        add: { imports: [RecetasFalso] }
      })
      .compileComponents();
    fixture = TestBed.createComponent(MedicoConsultaComponent);
    fixture.detectChanges();
  });

  it('muestra al paciente del turno y el estado borrador', () => {
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Nueva consulta');
    expect(texto).toContain('Acuña, Joaquín');
    expect(texto).toContain('DNI 19481640');
    expect(texto).toContain('Borrador');
    expect(texto).toContain('Guardá el borrador para poder agregar recetas');
  });

  it('cerrar una consulta nueva la guarda primero y después la cierra', () => {
    fixture.componentInstance.cerrar();
    fixture.detectChanges();
    expect(llamadas).toEqual(['crear', 'cerrar']);
    expect(fixture.componentInstance.cerrada).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Cerrada');
  });
});
