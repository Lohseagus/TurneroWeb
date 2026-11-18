import { Component, Input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, RouterLink } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it } from 'vitest';
import { OperadorTurnosComponent } from './operador-turnos.component';

@Component({ selector: 'app-operador-dashboard', standalone: true, template: 'por-confirmar-vista' })
class DashboardStub {
  @Input() soloTurnos = false;
}

@Component({ selector: 'app-admin-agenda', standalone: true, template: 'agenda-vista' })
class AgendaStub {}

@Component({ selector: 'app-turnos', standalone: true, template: 'todos-vista' })
class TurnosStub {}

@Component({ selector: 'app-turno-advanced-search', standalone: true, template: 'avanzada-vista' })
class AvanzadaStub {}

@Component({ selector: 'app-lista-espera', standalone: true, template: 'espera-vista' })
class EsperaStub {}

@Component({ selector: 'app-lista-espera-estadisticas', standalone: true, template: 'estadisticas-vista' })
class EstadisticasStub {}

describe('OperadorTurnosComponent', () => {
  async function abrir(url: string): Promise<{ texto: () => string; hrefs: () => string[] }> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [OperadorTurnosComponent],
      providers: [provideRouter([{ path: 'operador/turnos', component: OperadorTurnosComponent }])]
    });
    TestBed.overrideComponent(OperadorTurnosComponent, {
      set: {
        imports: [RouterLink, DashboardStub, AgendaStub, TurnosStub, AvanzadaStub, EsperaStub, EstadisticasStub]
      }
    });
    await TestBed.compileComponents();

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    const raiz = harness.routeNativeElement as HTMLElement;
    return {
      texto: () => raiz.textContent ?? '',
      hrefs: () => [...raiz.querySelectorAll('a')].map((enlace) => enlace.getAttribute('href') ?? '')
    };
  }

  it('abre Por confirmar y el turno extra apunta al formulario existente', async () => {
    const vista = await abrir('/operador/turnos');
    expect(vista.texto()).toContain('por-confirmar-vista');
    expect(vista.texto()).not.toContain('agenda-vista');
    expect(vista.hrefs().some((href) => href.includes('/turnos/new'))).toBe(true);
    expect(vista.hrefs().some((href) => href.includes('tab=agenda'))).toBe(true);
  });

  it('cambia de solapa según la URL', async () => {
    const agenda = await abrir('/operador/turnos?tab=agenda');
    expect(agenda.texto()).toContain('agenda-vista');
    expect(agenda.texto()).not.toContain('por-confirmar-vista');

    const todos = await abrir('/operador/turnos?tab=todos&vista=avanzada');
    expect(todos.texto()).toContain('avanzada-vista');
    expect(todos.hrefs().some((href) => href.includes('tab=todos') && !href.includes('vista='))).toBe(true);

    const espera = await abrir('/operador/turnos?tab=lista-espera&vista=estadisticas');
    expect(espera.texto()).toContain('estadisticas-vista');
    expect(espera.hrefs().some((href) => href.includes('tab=lista-espera') && !href.includes('vista='))).toBe(true);
  });
});
