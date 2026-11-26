import { Component, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AdminAgendaComponent } from '../agenda/admin-agenda.component';
import { CalendarioMesComponent } from '../calendario/calendario-mes.component';
import { ListaEsperaComponent } from '../lista-espera/lista-espera.component';
import { ListaEsperaEstadisticasComponent } from '../lista-espera/lista-espera-estadisticas.component';
import { TurnoAdvancedSearchComponent } from '../turnos/turno-advanced-search.component';
import { TurnosComponent } from '../turnos/turnos.component';
import { OperadorDashboardComponent } from './operador-dashboard.component';
import { SolapaTurnos, VistaTurnos, solapaDesdeQuery } from './operador-panel';

@Component({
  selector: 'app-operador-turnos',
  standalone: true,
  imports: [
    RouterLink,
    OperadorDashboardComponent,
    AdminAgendaComponent,
    TurnosComponent,
    TurnoAdvancedSearchComponent,
    ListaEsperaComponent,
    ListaEsperaEstadisticasComponent,
    CalendarioMesComponent
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './operador-turnos.component.html',
  styleUrls: ['../pacientes/paciente-portal.css', './operador-turnos.component.css']
})
export class OperadorTurnosComponent implements OnInit, OnDestroy {
  solapa: SolapaTurnos = 'por-confirmar';
  vista: VistaTurnos = 'lista';
  private sub?: Subscription;

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.sub = this.route.queryParamMap.subscribe((params) => {
      const parsed = solapaDesdeQuery(params.get('tab'), params.get('vista'));
      this.solapa = parsed.solapa;
      this.vista = parsed.vista;
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
