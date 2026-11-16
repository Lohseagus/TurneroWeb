import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SaasService } from './saas.service';
import { AddonSaas, CatalogoSaas, formatoArs } from './saas.models';

@Component({
  selector: 'app-planes-publicos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './planes-publicos.component.html',
  styleUrl: './saas.css'
})
export class PlanesPublicosComponent implements OnInit {
  catalogo: CatalogoSaas | null = null;
  error = '';
  ciclo: 'MENSUAL' | 'ANUAL' = 'MENSUAL';
  formatoArs = formatoArs;

  constructor(private saas: SaasService) {}

  ngOnInit(): void {
    this.saas.planes().subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.catalogo = resp.data;
        } else {
          this.error = resp.status_text;
        }
      },
      error: () => this.error = 'No se pudieron cargar los planes.'
    });
  }

  precio(plan: CatalogoSaas['planes'][number]): string {
    const ejemplo = this.ciclo === 'ANUAL' ? plan.ejemploAnual : plan.ejemploMensual;
    return formatoArs(ejemplo.neto);
  }

  total(plan: CatalogoSaas['planes'][number]): string {
    const ejemplo = this.ciclo === 'ANUAL' ? plan.ejemploAnual : plan.ejemploMensual;
    return formatoArs(ejemplo.total);
  }

  addonPrecio(addon: AddonSaas): string {
    return `${formatoArs(addon.precioMensualNeto)} + IVA`;
  }
}
