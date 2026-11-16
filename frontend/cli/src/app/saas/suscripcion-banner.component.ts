import { Component, OnDestroy, OnInit } from '@angular/core';
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
  template: `
    <div class="saas-banner" *ngIf="mensaje">
      {{ mensaje }}
      <a routerLink="/mi-plan">Ir a Mi plan</a>
    </div>
  `,
  styleUrl: './saas.css'
})
export class SuscripcionBannerComponent implements OnInit, OnDestroy {
  mensaje = '';
  private sub: Subscription | null = null;

  constructor(private users: UserContextService, private saas: SaasService) {}

  ngOnInit(): void {
    this.sub = this.users.userContext$.subscribe((context) => {
      this.mensaje = '';
      if (!context || context.primaryRole !== 'ADMINISTRADOR') {
        return;
      }
      this.saas.miSuscripcion().subscribe({
        next: (resp) => {
          if (resp.status_code !== 200 || !resp.data) return;
          if (resp.data.soloLectura) {
            this.mensaje = `La suscripción está ${etiquetaEstado(resp.data.estado).toLowerCase()}. La cuenta es de solo lectura.`;
          } else if (resp.data.estado === 'TRIAL') {
            this.mensaje = `Prueba activa: quedan ${resp.data.diasTrialRestantes} días.`;
          }
        }
      });
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
