import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SaasService } from './saas.service';
import { CatalogoSaas, SuscripcionSaas, etiquetaEstado, formatoArs, tope } from './saas.models';

@Component({
  selector: 'app-mi-plan',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './mi-plan.component.html',
  styleUrl: './saas.css'
})
export class MiPlanComponent implements OnInit {
  suscripcion: SuscripcionSaas | null = null;
  catalogo: CatalogoSaas | null = null;
  error = '';
  ok = '';
  planCodigo = '';
  ciclo = 'MENSUAL';
  cupo = 1;
  addons: string[] = [];
  sede = { nombre: '', direccion: '', localidad: '', provincia: '', telefono: '', latitud: -34.6, longitud: -58.38 };
  formatoArs = formatoArs;
  etiquetaEstado = etiquetaEstado;
  tope = tope;

  constructor(private saas: SaasService) {}

  ngOnInit(): void {
    this.cargar();
    this.saas.planes().subscribe({
      next: (resp) => {
        if (resp.status_code === 200) this.catalogo = resp.data;
      }
    });
  }

  cargar(): void {
    this.saas.miSuscripcion().subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.suscripcion = resp.data;
          this.planCodigo = resp.data.plan.codigo;
          this.ciclo = resp.data.ciclo;
          this.cupo = resp.data.profesionalesContratados;
          this.addons = [...resp.data.addons];
        } else {
          this.error = resp.status_text;
        }
      },
      error: () => this.error = 'No se pudo leer la suscripción.'
    });
  }

  get puedeAgregarSede(): boolean {
    const uso = this.suscripcion?.uso;
    if (!uso || this.suscripcion?.soloLectura) return false;
    return uso.maxCentros === null || uso.centros < uso.maxCentros;
  }

  toggleAddon(codigo: string, activo: boolean): void {
    if (activo && !this.addons.includes(codigo)) this.addons = [...this.addons, codigo];
    if (!activo) this.addons = this.addons.filter((item) => item !== codigo);
  }

  pagar(): void {
    this.error = '';
    this.ok = '';
    this.saas.pagar(this.ciclo).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.ok = `Pago simulado registrado: ${formatoArs(resp.data.total)} con IVA.`;
          this.cargar();
        } else {
          this.error = resp.status_text;
        }
      },
      error: () => this.error = 'No se pudo registrar el pago.'
    });
  }

  guardar(): void {
    this.error = '';
    this.ok = '';
    this.saas.cambiarPlan({
      planCodigo: this.planCodigo,
      ciclo: this.ciclo,
      profesionalesContratados: Number(this.cupo),
      addons: this.addons
    }).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.ok = 'Los cambios del plan quedaron guardados.';
          this.suscripcion = resp.data;
        } else {
          this.error = resp.status_text;
        }
      },
      error: () => this.error = 'No se pudo cambiar el plan.'
    });
  }

  crearSede(): void {
    this.error = '';
    this.ok = '';
    this.saas.crearSede({
      ...this.sede,
      latitud: Number(this.sede.latitud),
      longitud: Number(this.sede.longitud)
    }).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.ok = `Sede ${resp.data.nombre} creada.`;
          this.cargar();
        } else {
          this.error = resp.status_text;
        }
      },
      error: () => this.error = 'No se pudo crear la sede.'
    });
  }
}
