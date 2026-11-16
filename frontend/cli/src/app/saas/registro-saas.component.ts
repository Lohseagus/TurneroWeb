import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { SaasService } from './saas.service';
import { ToastService } from '../services/toast.service';
import { CatalogoSaas, PlanSaas, PrecioSaas, esPlanRecomendado, formatoArs, iconoPlan } from './saas.models';

@Component({
  selector: 'app-registro-saas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './registro-saas.component.html',
  styleUrl: './saas.css',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class RegistroSaasComponent implements OnInit, OnDestroy {
  @ViewChild('raiz') raiz?: ElementRef<HTMLElement>;

  catalogo: CatalogoSaas | null = null;
  cargando = true;
  error = '';
  ok = '';
  enviando = false;
  precio: PrecioSaas | null = null;
  paso = 1;
  maxAlcanzado = 1;
  errores: Record<string, string> = {};
  password2 = '';
  formatoArs = formatoArs;
  iconoPlan = iconoPlan;
  esPlanRecomendado = esPlanRecomendado;
  readonly pasos = [
    { id: 1, titulo: 'Plan' },
    { id: 2, titulo: 'Centro' },
    { id: 3, titulo: 'Admin' },
    { id: 4, titulo: 'Confirmar' }
  ];

  form = {
    planCodigo: 'PROFESIONAL',
    ciclo: 'MENSUAL',
    profesionalesContratados: 1,
    addons: [] as string[],
    organizacionNombre: '',
    cuit: '',
    emailContacto: '',
    centroNombre: '',
    direccion: '',
    localidad: '',
    provincia: '',
    telefonoCentro: '',
    latitud: -34.6037 as number | null,
    longitud: -58.3816 as number | null,
    adminNombre: '',
    adminApellido: '',
    adminEmail: '',
    adminPassword: '',
    adminDni: '',
    adminTelefono: ''
  };

  private querySub: Subscription | null = null;
  private cotizarTimer: ReturnType<typeof setTimeout> | null = null;
  private redir: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private saas: SaasService,
    private route: ActivatedRoute,
    private router: Router,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.querySub = this.route.queryParamMap.subscribe((params) => {
      const plan = params.get('plan');
      const ciclo = params.get('ciclo');
      const profesionales = params.get('profesionales');
      const addons = params.get('addons');
      if (plan) this.form.planCodigo = plan;
      if (ciclo === 'ANUAL' || ciclo === 'MENSUAL') this.form.ciclo = ciclo;
      if (profesionales) this.form.profesionalesContratados = Number(profesionales) || 1;
      if (addons !== null) this.form.addons = addons.split(',').map((item) => item.trim()).filter(Boolean);
      if (this.catalogo) this.ajustarCupo();
    });
    this.saas.planes().subscribe({
      next: (resp) => {
        this.cargando = false;
        if (resp.status_code === 200) {
          this.catalogo = resp.data;
          this.ajustarCupo();
        } else {
          this.error = resp.status_text || 'No se pudieron cargar los planes.';
          this.toast.error(this.error);
        }
      },
      error: () => {
        this.cargando = false;
        this.error = 'No se pudieron cargar los planes.';
        this.toast.error(this.error);
      }
    });
  }

  ngOnDestroy(): void {
    this.querySub?.unsubscribe();
    if (this.cotizarTimer) clearTimeout(this.cotizarTimer);
    if (this.redir) clearTimeout(this.redir);
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
    } else {
      this.form.profesionalesContratados = 1;
    }
    this.programarCotizacion();
  }

  toggleAddon(codigo: string, activo: boolean): void {
    if (activo && !this.form.addons.includes(codigo)) {
      this.form.addons = [...this.form.addons, codigo];
    }
    if (!activo) {
      this.form.addons = this.form.addons.filter((item) => item !== codigo);
    }
    this.programarCotizacion();
  }

  elegirPlan(codigo: string): void {
    this.form.planCodigo = codigo;
    this.ajustarCupo();
  }

  irA(destino: number): void {
    if (destino === this.paso) return;
    if (destino < this.paso) {
      this.paso = destino;
      this.error = '';
      return;
    }
    for (let actual = this.paso; actual < destino; actual++) {
      if (!this.validarPaso(actual)) {
        this.paso = actual;
        this.enfocarError();
        return;
      }
    }
    this.paso = destino;
    this.maxAlcanzado = Math.max(this.maxAlcanzado, destino);
  }

  siguiente(): void {
    this.error = '';
    if (this.paso < 4) {
      if (!this.validarPaso(this.paso)) {
        this.enfocarError();
        return;
      }
      this.paso += 1;
      this.maxAlcanzado = Math.max(this.maxAlcanzado, this.paso);
      return;
    }
    this.enviar();
  }

  anterior(): void {
    if (this.paso > 1) this.paso -= 1;
  }

  nombresAddons(): string {
    if (!this.form.addons.length || !this.catalogo) return 'Ninguno';
    return this.catalogo.addons
      .filter((addon) => this.form.addons.includes(addon.codigo))
      .map((addon) => addon.nombre)
      .join(', ') || 'Ninguno';
  }

  private validarPaso(paso: number): boolean {
    this.errores = {};
    if (paso === 1) {
      if (!this.form.planCodigo) this.errores['plan'] = 'Elegí un plan.';
      if (this.form.ciclo !== 'MENSUAL' && this.form.ciclo !== 'ANUAL') this.errores['ciclo'] = 'Elegí mensual o anual.';
      if (this.porAsiento && this.planActual) {
        const min = this.planActual.minProfesionales || 1;
        const max = this.planActual.maxProfesionales || min;
        const cupo = Number(this.form.profesionalesContratados);
        if (!cupo || cupo < min || cupo > max) {
          this.errores['cupo'] = `El cupo tiene que estar entre ${min} y ${max} profesionales.`;
        }
      }
    }
    if (paso === 2) {
      this.requerido('organizacionNombre', this.form.organizacionNombre, 'Ingresá el nombre de la organización.');
      this.requerido('centroNombre', this.form.centroNombre, 'Ingresá el nombre del centro.');
      this.requerido('direccion', this.form.direccion, 'Ingresá la dirección.');
      this.requerido('localidad', this.form.localidad, 'Ingresá la localidad.');
      this.requerido('provincia', this.form.provincia, 'Ingresá la provincia.');
      if (!/^\d+$/.test((this.form.telefonoCentro || '').trim())) {
        this.errores['telefonoCentro'] = 'El teléfono del centro tiene que tener solo números.';
      }
      const lat = this.form.latitud;
      const lng = this.form.longitud;
      if (lat == null || Number.isNaN(Number(lat)) || Number(lat) < -90 || Number(lat) > 90) {
        this.errores['latitud'] = 'La latitud tiene que estar entre -90 y 90.';
      }
      if (lng == null || Number.isNaN(Number(lng)) || Number(lng) < -180 || Number(lng) > 180) {
        this.errores['longitud'] = 'La longitud tiene que estar entre -180 y 180.';
      }
      if (this.form.cuit.trim() && !/^\d{11}$/.test(this.form.cuit.trim())) {
        this.errores['cuit'] = 'El CUIT, si lo cargás, tiene 11 números.';
      }
      if (this.form.emailContacto.trim() && !this.emailValido(this.form.emailContacto)) {
        this.errores['emailContacto'] = 'El email de contacto no es válido.';
      }
    }
    if (paso === 3) {
      this.requerido('adminNombre', this.form.adminNombre, 'Ingresá el nombre.');
      this.requerido('adminApellido', this.form.adminApellido, 'Ingresá el apellido.');
      if (!this.emailValido(this.form.adminEmail)) this.errores['adminEmail'] = 'Ingresá un email válido.';
      if (!this.form.adminPassword || this.form.adminPassword.length < 6) {
        this.errores['adminPassword'] = 'La contraseña tiene que tener al menos 6 caracteres.';
      }
      if (this.form.adminPassword !== this.password2) this.errores['password2'] = 'Las contraseñas no coinciden.';
      if (!/^\d{7,8}$/.test((this.form.adminDni || '').trim())) this.errores['adminDni'] = 'El DNI tiene 7 u 8 números.';
      this.requerido('adminTelefono', this.form.adminTelefono, 'Ingresá el teléfono.');
    }
    if (Object.keys(this.errores).length) {
      this.error = 'Revisá los campos marcados antes de seguir.';
      return false;
    }
    this.error = '';
    return true;
  }

  enviar(): void {
    if (this.enviando) return;
    for (let actual = 1; actual <= 3; actual++) {
      if (!this.validarPaso(actual)) {
        this.paso = actual;
        this.enfocarError();
        return;
      }
    }
    this.error = '';
    this.ok = '';
    this.enviando = true;
    const body = {
      ...this.form,
      cuit: this.form.cuit.trim() || null,
      emailContacto: this.form.emailContacto.trim() || null,
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
          this.toast.success(this.ok);
          this.redir = setTimeout(() => this.router.navigate(['/ingresar']), 2200);
        } else {
          this.error = resp.status_text || 'No se pudo crear la cuenta.';
          this.toast.error(this.error);
        }
      },
      error: () => {
        this.enviando = false;
        this.error = 'No se pudo crear la cuenta.';
        this.toast.error(this.error);
      }
    });
  }

  private programarCotizacion(): void {
    if (this.cotizarTimer) clearTimeout(this.cotizarTimer);
    this.cotizarTimer = setTimeout(() => this.cotizar(), 200);
  }

  private cotizar(): void {
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
      }
    });
  }

  private requerido(campo: string, valor: string, mensaje: string): void {
    if (!valor || !valor.trim()) this.errores[campo] = mensaje;
  }

  private emailValido(valor: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test((valor || '').trim());
  }

  private enfocarError(): void {
    setTimeout(() => {
      const nodo = this.raiz?.nativeElement.querySelector<HTMLElement>('.is-invalid input, .is-invalid select, .is-invalid textarea');
      nodo?.focus();
    });
  }
}
