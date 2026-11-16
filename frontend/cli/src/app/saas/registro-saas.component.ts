import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { SaasService } from './saas.service';
import { CatalogoSaas, PlanSaas, PrecioSaas, formatoArs } from './saas.models';

@Component({
  selector: 'app-registro-saas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './registro-saas.component.html',
  styleUrl: './saas.css'
})
export class RegistroSaasComponent implements OnInit {
  catalogo: CatalogoSaas | null = null;
  error = '';
  ok = '';
  enviando = false;
  precio: PrecioSaas | null = null;
  formatoArs = formatoArs;

  form = {
    planCodigo: 'PROFESIONAL',
    ciclo: 'MENSUAL',
    profesionalesContratados: 1,
    addons: [] as string[],
    organizacionNombre: '',
    centroNombre: '',
    direccion: '',
    localidad: '',
    provincia: '',
    telefonoCentro: '',
    latitud: -34.6037,
    longitud: -58.3816,
    adminNombre: '',
    adminApellido: '',
    adminEmail: '',
    adminPassword: '',
    adminDni: '',
    adminTelefono: ''
  };

  constructor(private saas: SaasService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      const plan = params.get('plan');
      const ciclo = params.get('ciclo');
      if (plan) this.form.planCodigo = plan;
      if (ciclo === 'ANUAL' || ciclo === 'MENSUAL') this.form.ciclo = ciclo;
    });
    this.saas.planes().subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.catalogo = resp.data;
          this.ajustarCupo();
          this.cotizar();
        } else {
          this.error = resp.status_text;
        }
      },
      error: () => this.error = 'No se pudieron cargar los planes.'
    });
  }

  get planActual(): PlanSaas | undefined {
    return this.catalogo?.planes.find((plan) => plan.codigo === this.form.planCodigo);
  }

  get porAsiento(): boolean {
    return (this.planActual?.precioPorProfesional || 0) > 0;
  }

  ajustarCupo(): void {
    const plan = this.planActual;
    if (!plan) return;
    if (this.porAsiento) {
      const min = plan.minProfesionales || 1;
      const max = plan.maxProfesionales || min;
      if (this.form.profesionalesContratados < min) this.form.profesionalesContratados = min;
      if (this.form.profesionalesContratados > max) this.form.profesionalesContratados = max;
    }
    this.cotizar();
  }

  toggleAddon(codigo: string, activo: boolean): void {
    if (activo && !this.form.addons.includes(codigo)) {
      this.form.addons = [...this.form.addons, codigo];
    }
    if (!activo) {
      this.form.addons = this.form.addons.filter((item) => item !== codigo);
    }
    this.cotizar();
  }

  cotizar(): void {
    if (!this.form.planCodigo) return;
    this.saas.cotizar({
      planCodigo: this.form.planCodigo,
      ciclo: this.form.ciclo,
      profesionalesContratados: Number(this.form.profesionalesContratados) || 1,
      sedes: 1,
      addons: this.form.addons
    }).subscribe({
      next: (resp) => {
        this.precio = resp.status_code === 200 ? resp.data : null;
        if (resp.status_code !== 200) this.error = resp.status_text;
      }
    });
  }

  enviar(): void {
    this.error = '';
    this.ok = '';
    this.enviando = true;
    const body = {
      ...this.form,
      profesionalesContratados: Number(this.form.profesionalesContratados),
      adminDni: Number(this.form.adminDni),
      latitud: Number(this.form.latitud),
      longitud: Number(this.form.longitud)
    };
    this.saas.registrar(body).subscribe({
      next: (resp) => {
        this.enviando = false;
        if (resp.status_code === 200) {
          this.ok = `Listo. La prueba de ${resp.data.diasTrial} días quedó activa para ${resp.data.email}. Ya podés ingresar.`;
          setTimeout(() => this.router.navigate(['/ingresar']), 1800);
        } else {
          this.error = resp.status_text;
        }
      },
      error: () => {
        this.enviando = false;
        this.error = 'No se pudo crear la cuenta.';
      }
    });
  }
}
