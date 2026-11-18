import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { DataPackage } from '../data.package';
import { MENSAJE_SIN_FICHA_PACIENTE } from '../config/menu.config';
import { PacienteHceComponent } from './paciente-hce.component';
import { HceService } from './hce.service';

class HceFalso {
  peticiones: Subject<DataPackage<unknown>>[] = [];

  miHistoria() {
    const actual = new Subject<DataPackage<unknown>>();
    this.peticiones.push(actual);
    return actual.asObservable();
  }

  get actual(): Subject<DataPackage<unknown>> {
    const ultima = this.peticiones[this.peticiones.length - 1];
    if (!ultima) {
      throw new Error('Todavía no se pidió la historia');
    }
    return ultima;
  }
}

describe('PacienteHceComponent', () => {
  let fixture: ComponentFixture<PacienteHceComponent>;
  let hce: HceFalso;

  beforeEach(async () => {
    hce = new HceFalso();
    await TestBed.configureTestingModule({
      imports: [PacienteHceComponent],
      providers: [provideRouter([]), { provide: HceService, useValue: hce }]
    }).compileComponents();
    fixture = TestBed.createComponent(PacienteHceComponent);
  });

  it('un 404 de ficha muestra el vacío y no se queda en Cargando', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance.cargando).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Cargando');

    hce.actual.error(new HttpErrorResponse({
      status: 404,
      error: { status_text: MENSAJE_SIN_FICHA_PACIENTE }
    }));
    fixture.detectChanges();

    expect(fixture.componentInstance.cargando).toBe(false);
    expect(fixture.componentInstance.sinFicha).toBe(true);
    expect(fixture.componentInstance.error).toBe('');
    expect(fixture.nativeElement.textContent).toContain(MENSAJE_SIN_FICHA_PACIENTE);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando');
    expect(fixture.nativeElement.textContent).not.toContain('Línea de tiempo');
  });
});
