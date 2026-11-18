import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { Role } from '../inicio-sesion/auth.service';
import { UserContextService } from '../services/user-context.service';
import { SaasService } from './saas.service';
import { etiquetaEstado } from './saas.models';

const ROLES_CON_BANNER = new Set<string>([Role.ADMINISTRADOR, Role.OPERADOR, Role.MEDICO]);

@Component({
  selector: 'app-suscripcion-banner',
  standalone: true,
  imports: [CommonModule, RouterModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (mensaje) {
      <div class="alert d-flex flex-wrap align-items-center gap-2 saas-banner" [class.alert-danger]="soloLectura" [class.alert-warning]="!soloLectura" role="status">
        <span class="material-symbols-outlined" aria-hidden="true">{{ soloLectura ? 'lock' : 'hourglass_top' }}</span>
        <p class="mb-0 flex-grow-1">{{ mensaje }}</p>
        @if (puedeIrAMiPlan) {
          <a class="btn btn-sm btn-outline-dark" routerLink="/mi-plan">Ir a Mi plan</a>
        }
      </div>
    }
  `,
  styleUrl: './saas.css'
})
export class SuscripcionBannerComponent implements OnInit, OnDestroy {
  mensaje = '';
  soloLectura = false;
  puedeIrAMiPlan = false;
  private sub: Subscription | null = null;
  private avisoSub: Subscription | null = null;

  constructor(private users: UserContextService, private saas: SaasService) {}

  ngOnInit(): void {
    this.sub = this.users.userContext$.subscribe((context) => {
      this.mensaje = '';
      this.soloLectura = false;
      this.puedeIrAMiPlan = false;
      if (!context?.isAuthenticated || !ROLES_CON_BANNER.has(context.primaryRole)) {
        return;
      }
      this.puedeIrAMiPlan = context.primaryRole === Role.ADMINISTRADOR;
      this.avisoSub?.unsubscribe();
      this.avisoSub = this.saas.aviso().subscribe({
        next: (resp) => {
          if (resp.status_code !== 200 || !resp.data) return;
          if (resp.data.enforcementEnabled && resp.data.soloLectura) {
            this.soloLectura = true;
            this.mensaje = resp.data.mensaje
              || `La suscripción está ${etiquetaEstado(resp.data.estado || '').toLowerCase()}. La cuenta es de solo lectura: no podés crear turnos ni recursos nuevos.`;
          } else if (resp.data.estado === 'TRIAL') {
            this.mensaje = `Prueba activa: te quedan ${resp.data.diasTrialRestantes ?? 0} días. Todavía no hace falta la tarjeta.`;
          }
        }
      });
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.avisoSub?.unsubscribe();
  }
}
