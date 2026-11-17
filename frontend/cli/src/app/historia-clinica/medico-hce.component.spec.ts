import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { DataPackage } from '../data.package';
import { MedicoHceComponent } from './medico-hce.component';
import { HceService } from './hce.service';
import { PacienteResumenHce } from './hce.models';

class HceFalso {
  peticiones: Subject<DataPackage<PacienteResumenHce[]>>[] = [];

  misPacientes() {
    const actual = new Subject<DataPackage<PacienteResumenHce[]>>();
    this.peticiones.push(actual);
    return actual.asObservable();
  }

  get actual(): Subject<DataPackage<PacienteResumenHce[]>> {
    const ultima = this.peticiones[this.peticiones.length - 1];
    if (!ultima) {
      throw new Error('Todavía no se pidió la lista');
    }
    return ultima;
  }
}

function paquete(data: PacienteResumenHce[]): DataPackage<PacienteResumenHce[]> {
  return { data, message: '', status: 200, status_text: 'ok', status_code: 200 };
}

describe('MedicoHceComponent', () => {
  let fixture: ComponentFixture<MedicoHceComponent>;
  let hce: HceFalso;

  beforeEach(async () => {
    hce = new HceFalso();
    await TestBed.configureTestingModule({
      imports: [MedicoHceComponent],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({})) } },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        { provide: HceService, useValue: hce }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(MedicoHceComponent);
  });

  it('pasa de cargando a la lista vacía', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance.cargando).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Cargando');

    hce.actual.next(paquete([]));
    hce.actual.complete();

    expect(fixture.componentInstance.cargando).toBe(false);
    expect(fixture.componentInstance.pacientes).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Todavía no atendiste pacientes');
    expect(fixture.nativeElement.textContent).not.toContain('Cargando');
  });

  it('muestra el error de un médico sin ficha y el reintento trae pacientes', () => {
    fixture.detectChanges();
    hce.actual.error(new HttpErrorResponse({
      status: 404,
      error: { status_text: 'Tu usuario tiene rol de médico, pero no hay una ficha profesional vinculada.' }
    }));

    expect(fixture.componentInstance.cargando).toBe(false);
    expect(fixture.componentInstance.error).toContain('ficha profesional');
    expect(fixture.nativeElement.textContent).toContain('ficha profesional');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');

    fixture.componentInstance.reintentar();
    expect(fixture.componentInstance.cargando).toBe(true);
    expect(hce.peticiones.length).toBe(2);

    hce.actual.next(paquete([{ id: 4, nombre: 'Ana', apellido: 'López' }]));
    hce.actual.complete();

    expect(fixture.componentInstance.cargando).toBe(false);
    expect(fixture.componentInstance.error).toBe('');
    expect(fixture.componentInstance.pacientes.map(p => p.apellido)).toEqual(['López']);
  });
});
