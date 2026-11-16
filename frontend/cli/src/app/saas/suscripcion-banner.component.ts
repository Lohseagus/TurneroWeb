import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { UserContextService } from '../services/user-context.service';
import { SaasService } from './saas.service';
import { etiquetaEstado } from './saas.models';

@Component({
  selector: 'app-suscripcion-banner',
  standalone: true,
  imports: [CommonModule, RouterModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (mensaje) {
      <div class="saas-banner" [class.saas-banner--bloqueo]="soloLectura" role="status">
        <span class="material-symbols-outlined" aria-hidden="true">{{ soloLectura ? 'lock' : 'hourglass_top' }}</span>
        <p>{{ mensaje }}</p>
        <a routerLink="/mi-plan">Ir a Mi plan</a>
      </div>
    }
  `,
  styleUrl: './saas.css'
})
export class SuscripcionBannerComponent implements OnInit, OnDestroy {
  mensaje = '';
  soloLectura = false;
  private sub: Subscription | null = null;

  constructor(private users: UserContextService, private saas: SaasService) {}

  ngOnInit(): void {
    this.sub = this.users.userContext$.subscribe((context) => {
      this.mensaje = '';
      this.soloLectura = false;
      if (!context || context.primaryRole !== 'ADMINISTRADOR') {
        return;
      }
      this.saas.miSuscripcion().subscribe({
        next: (resp) => {
          if (resp.status_code !== 200 || !resp.data) return;
          if (resp.data.soloLectura) {
            this.soloLectura = true;
            this.mensaje = `La suscripción está ${etiquetaEstado(resp.data.estado).toLowerCase()}. La cuenta es de solo lectura: no podés crear turnos ni recursos nuevos.`;
          } else if (resp.data.estado === 'TRIAL') {
            this.mensaje = `Prueba activa: te quedan ${resp.data.diasTrialRestantes ?? 0} días. Todavía no hace falta la tarjeta.`;
          }
        }
      });
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
