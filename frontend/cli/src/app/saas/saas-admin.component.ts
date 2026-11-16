import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { SaasService } from './saas.service';
import { CatalogoSaas, PagoSaas, PlanSaas, ResumenSaas, SuscripcionSaas, etiquetaEstado, formatoArs, tope } from './saas.models';

@Component({
  selector: 'app-saas-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './saas-admin.component.html',
  styleUrl: './saas.css'
})
export class SaasAdminComponent implements OnInit, OnDestroy {
  vista = 'dashboard';
  resumen: ResumenSaas | null = null;
  suscripciones: SuscripcionSaas[] = [];
  detalle: SuscripcionSaas | null = null;
  catalogo: CatalogoSaas | null = null;
  pagos: PagoSaas[] = [];
  filtroEstado = '';
  filtroPlan = '';
  filtroTexto = '';
  suscripcionId: number | null = null;
  diasExtension = 7;
  motivo = '';
  planDestino = '';
  error = '';
  ok = '';
  formatoArs = formatoArs;
  etiquetaEstado = etiquetaEstado;
  tope = tope;
  private sub: Subscription | null = null;

  constructor(private saas: SaasService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.sub = combineLatest([this.route.data, this.route.paramMap]).subscribe(([data, params]) => {
      this.vista = (data['vista'] as string) || 'dashboard';
      const id = params.get('id');
      this.suscripcionId = id ? Number(id) : null;
      this.error = '';
      this.cargar();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  cargar(): void {
    if (this.vista === 'dashboard') {
      this.saas.resumen().subscribe({
        next: (resp) => this.resumen = resp.status_code === 200 ? resp.data : null,
        error: () => this.error = 'No se pudo cargar el tablero.'
      });
    }
    if (this.vista === 'suscripciones') {
      this.buscar();
    }
    if (this.vista === 'detalle' && this.suscripcionId) {
      this.saas.detalle(this.suscripcionId).subscribe({
        next: (resp) => {
          if (resp.status_code === 200) {
            this.detalle = resp.data;
            this.planDestino = resp.data.plan.codigo;
          } else {
            this.error = resp.status_text;
          }
        }
      });
      this.saas.planes().subscribe({
        next: (resp) => {
          if (resp.status_code === 200) this.catalogo = resp.data;
        }
      });
    }
    if (this.vista === 'planes') {
      this.saas.catalogoAdmin().subscribe({
        next: (resp) => this.catalogo = resp.status_code === 200 ? resp.data : null,
        error: () => this.error = 'No se pudieron cargar los planes.'
      });
    }
    if (this.vista === 'pagos') {
      this.saas.pagos().subscribe({
        next: (resp) => this.pagos = resp.status_code === 200 ? resp.data : []
      });
    }
  }

  buscar(): void {
    this.saas.suscripciones(this.filtroEstado, this.filtroPlan, this.filtroTexto).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) this.suscripciones = resp.data;
        else this.error = resp.status_text;
      }
    });
  }

  extender(): void {
    if (!this.detalle) return;
    this.saas.extenderTrial(this.detalle.id, Number(this.diasExtension)).subscribe({
      next: (resp) => this.despues(resp.status_code, resp.status_text, resp.data)
    });
  }

  suspender(): void {
    if (!this.detalle) return;
    this.saas.suspender(this.detalle.id, this.motivo).subscribe({
      next: (resp) => this.despues(resp.status_code, resp.status_text, resp.data)
    });
  }

  reactivar(): void {
    if (!this.detalle) return;
    this.saas.reactivar(this.detalle.id).subscribe({
      next: (resp) => this.despues(resp.status_code, resp.status_text, resp.data)
    });
  }

  cancelar(): void {
    if (!this.detalle) return;
    this.saas.cancelar(this.detalle.id, this.motivo).subscribe({
      next: (resp) => this.despues(resp.status_code, resp.status_text, resp.data)
    });
  }

  cambiarPlan(): void {
    if (!this.detalle) return;
    this.saas.cambiarPlanAdmin(this.detalle.id, { planCodigo: this.planDestino }).subscribe({
      next: (resp) => this.despues(resp.status_code, resp.status_text, resp.data)
    });
  }

  guardarConfig(): void {
    if (!this.catalogo) return;
    this.saas.guardarConfiguracion(this.catalogo.configuracion).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.catalogo!.configuracion = resp.data;
          this.ok = 'Configuración comercial actualizada.';
          this.error = '';
        } else {
          this.error = resp.status_text;
        }
      }
    });
  }

  guardarPlan(plan: PlanSaas): void {
    this.saas.guardarPlan(plan).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.ok = `Plan ${plan.nombre} actualizado.`;
          this.error = '';
        } else {
          this.error = resp.status_text;
        }
      }
    });
  }

  guardarAddon(addon: CatalogoSaas['addons'][number]): void {
    this.saas.guardarAddon(addon).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.ok = `Complemento ${addon.nombre} actualizado.`;
          this.error = '';
        } else {
          this.error = resp.status_text;
        }
      }
    });
  }

  private despues(status: number, texto: string, data: SuscripcionSaas): void {
    if (status === 200) {
      this.detalle = data;
      this.ok = texto;
      this.error = '';
    } else {
      this.error = texto;
    }
  }
}
