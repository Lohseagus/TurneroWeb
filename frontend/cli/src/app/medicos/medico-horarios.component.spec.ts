import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { MedicoHorariosComponent } from './medico-horarios.component';
import { MedicoSesionService } from './medico-sesion.service';
import { MedicoService } from './medico.service';
import { StaffMedicoService } from '../staffMedicos/staffMedico.service';
import { DisponibilidadMedicoService } from '../disponibilidadMedicos/disponibilidadMedico.service';

const sesion = {
  id: 1, nombre: 'Juan', apellido: 'Pérez', dni: null, email: 'medico@turnero.com', telefono: null,
  matricula: 'MP-1', especialidades: [{ id: 1, nombre: 'Cardiología' }], staffMedicoId: 1,
  staff: [
    { id: 1, centroId: 10, centroNombre: 'Clínica Santa María', especialidadId: 1, especialidadNombre: 'Cardiología' },
    { id: 2, centroId: 20, centroNombre: 'Clínica del Sur', especialidadId: 1, especialidadNombre: 'Cardiología' },
    { id: 3, centroId: 30, centroNombre: 'Consultorios del Sol', especialidadId: 1, especialidadNombre: 'Cardiología' }
  ]
};
const disponibilidades = [
  { id: 61, staffMedicoId: 1, especialidadId: 1, horarios: [{ dia: 'LUNES', horaInicio: '08:00:00', horaFin: '11:00:00' }] },
  { id: 62, staffMedicoId: 2, especialidadId: 1, horarios: [{ dia: 'MARTES', horaInicio: '08:00:00', horaFin: '11:00:00' }] }
];

describe('MedicoHorariosComponent', () => {
  let fixture: ComponentFixture<MedicoHorariosComponent>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [MedicoHorariosComponent],
      providers: [
        provideNoopAnimations(),
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        { provide: MedicoSesionService, useValue: { resolver: () => of(sesion), invalidar: () => undefined } },
        { provide: MedicoService, useValue: {} },
        { provide: StaffMedicoService, useValue: {} },
        { provide: DisponibilidadMedicoService, useValue: { byMedico: () => of({ data: disponibilidades }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(MedicoHorariosComponent);
    fixture.detectChanges();
  });

  it('muestra por defecto los horarios de todos los centros del médico', () => {
    const texto = fixture.nativeElement.textContent;
    expect(fixture.componentInstance.centroFiltro).toBeNull();
    expect(texto).toContain('Todos los centros (3)');
    expect(fixture.nativeElement.querySelectorAll('article.especialidad')).toHaveLength(3);
    expect(texto).toContain('LUNES');
    expect(texto).toContain('MARTES');
    expect(fixture.componentInstance.disponibilidades).toHaveLength(2);
    expect(texto).toContain('Consultorios del Sol');
    expect(texto).toContain('Configurar Horarios');
  });

  it('con un centro elegido en el filtro muestra solo ese centro', () => {
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#filtroCentro');
    select.value = '20';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const tarjetas = fixture.nativeElement.querySelectorAll('article.especialidad');
    expect(tarjetas).toHaveLength(1);
    expect(tarjetas[0].textContent).toContain('Clínica del Sur');
    expect(tarjetas[0].textContent).toContain('MARTES');
    expect(fixture.componentInstance.disponibilidades.map(d => d.id)).toEqual([62]);

    select.value = '';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article.especialidad')).toHaveLength(3);
  });

  it('configurar un centro pendiente apunta el alta al staff de ese centro', () => {
    const comp = fixture.componentInstance;
    comp.nuevaDisponibilidadParaStaff(comp.staffMedicos[2]);
    expect(comp.mostrarFormulario).toBe(true);
    expect(comp.staffMedicoSeleccionado.id).toBe(3);
    expect(comp.especialidadSeleccionada).toBe(1);
  });
});
