import { describe, expect, it } from 'vitest';
import { destinoDeRutaOperador, solapaDesdeQuery } from './operador-panel';

describe('rutas viejas del operador', () => {
  it('manda el dashboard a Mi Centro y el resto a la solapa correspondiente', () => {
    expect(destinoDeRutaOperador('operador-dashboard')).toBe('mi-centro');
    expect(destinoDeRutaOperador('/turnos')).toEqual({ tab: 'todos' });
    expect(destinoDeRutaOperador('turnos/advanced-search')).toEqual({ tab: 'todos', vista: 'avanzada' });
    expect(destinoDeRutaOperador('agenda')).toEqual({ tab: 'agenda' });
    expect(destinoDeRutaOperador('lista-espera')).toEqual({ tab: 'lista-espera' });
    expect(destinoDeRutaOperador('lista-espera-estadisticas')).toEqual({
      tab: 'lista-espera',
      vista: 'estadisticas'
    });
  });

  it('no reescribe el turno extra, el detalle ni las ausencias', () => {
    expect(destinoDeRutaOperador('turnos/new')).toBeNull();
    expect(destinoDeRutaOperador('turnos/15')).toBeNull();
    expect(destinoDeRutaOperador('turnos/15/edit')).toBeNull();
    expect(destinoDeRutaOperador('agenda/dias-excepcionales')).toBeNull();
    expect(destinoDeRutaOperador('pacientes')).toBeNull();
  });
});

describe('solapa activa de Turnos', () => {
  it('usa Por confirmar cuando la URL no trae solapa', () => {
    expect(solapaDesdeQuery(null, null)).toEqual({ solapa: 'por-confirmar', vista: 'lista' });
    expect(solapaDesdeQuery('desconocida', 'estadisticas')).toEqual({ solapa: 'por-confirmar', vista: 'lista' });
  });

  it('respeta la solapa y la vista que se puede linkear', () => {
    expect(solapaDesdeQuery('agenda', null)).toEqual({ solapa: 'agenda', vista: 'lista' });
    expect(solapaDesdeQuery('todos', 'avanzada')).toEqual({ solapa: 'todos', vista: 'avanzada' });
    expect(solapaDesdeQuery('lista-espera', 'estadisticas')).toEqual({
      solapa: 'lista-espera',
      vista: 'estadisticas'
    });
    expect(solapaDesdeQuery('agenda', 'estadisticas')).toEqual({ solapa: 'agenda', vista: 'lista' });
    expect(solapaDesdeQuery('todos', 'estadisticas')).toEqual({ solapa: 'todos', vista: 'lista' });
  });
});
