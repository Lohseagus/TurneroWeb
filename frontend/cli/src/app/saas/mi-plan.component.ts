import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SaasService } from './saas.service';
import { ToastService } from '../services/toast.service';
import { ModalService } from '../modal/modal.service';
import { UserContextService } from '../services/user-context.service';
import {
  CatalogoSaas,
  PrecioSaas,
  SuscripcionSaas,
  claseEstado,
  esFalloDeAcceso,
  etiquetaCiclo,
  etiquetaEstado,
  etiquetaPago,
  etiquetaTipoPago,
  formatoArs,
  formatoFecha,
  nivelUso,
  porcentajeUso,
  tope
} from './saas.models';

@Component({
  selector: 'app-mi-plan',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './mi-plan.component.html',
  styleUrl: './saas.css',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MiPlanComponent implements OnInit {

  suscripcion: SuscripcionSaas | null = null;
  catalogo: CatalogoSaas | null = null;
  cargando = true;
  operando = false;
  error = '';
  ok = '';
  planCodigo = '';
  ciclo = 'MENSUAL';
  cupo = 1;
  addons: string[] = [];
  precioCambio: PrecioSaas | null = null;
  sede = { nombre: '', direccion: '', localidad: '', provincia: '', telefono: '', latitud: -34.6 as number | null, longitud: -58.38 as number | null };
  erroresSede: Record<string, string> = {};
  formatoArs = formatoArs;
  formatoFecha = formatoFecha;
  etiquetaEstado = etiquetaEstado;
  etiquetaPago = etiquetaPago;
  etiquetaTipoPago = etiquetaTipoPago;
  etiquetaCiclo = etiquetaCiclo;
  claseEstado = claseEstado;
  tope = tope;
  porcentajeUso = porcentajeUso;
  nivelUso = nivelUso;

  constructor(
    private saas: SaasService,
    private toast: ToastService,
    private users: UserContextService,
    private modal: ModalService
  ) {}

  sinPermiso = '';

  ngOnInit(): void {
    const rol = this.users.getCurrentContext()?.primaryRole;
    if (rol !== 'ADMINISTRADOR') {
      this.cargando = false;
      this.sinPermiso = rol === 'SUPERADMIN'
        ? 'Mi plan es del administrador de cada centro. El tablero comercial está en SaaS.'
        : 'No tenés un plan de centro para ver.';
      return;
    }
    this.cargar();
    this.saas.planes().subscribe({
      next: (resp) => {
        if (resp.status_code === 200) this.catalogo = resp.data;
      }
    });
  }

  cargar(): void {
    this.cargando = true;
    this.saas.miSuscripcion().subscribe({
      next: (resp) => {
        this.cargando = false;
        if (resp.status_code === 200) {
          this.aplicar(resp.data);
          this.error = '';
        } else {
          this.error = resp.status_text || 'No se pudo leer la suscripción.';
          this.toast.error(this.error);
        }
      },
      error: (err) => {
        this.cargando = false;
        if (esFalloDeAcceso(err)) return;
        this.error = 'No se pudo leer la suscripción.';
        this.toast.error(this.error);
      }
    });
  }

  get puedeAgregarSede(): boolean {
    const uso = this.suscripcion?.uso;
    if (!uso || this.suscripcion?.soloLectura) return false;
    return uso.maxCentros === null || uso.centros < uso.maxCentros;
  }

  get formularioSucio(): boolean {
    const actual = this.suscripcion;
    if (!actual) return false;
    return this.planCodigo !== actual.plan.codigo
      || this.ciclo !== actual.ciclo
      || Number(this.cupo) !== actual.profesionalesContratados
      || !this.mismosAddons(this.addons, actual.addons);
  }

  get trialTotal(): number {
    return this.catalogo?.configuracion.diasTrial || 30;
  }

  get trialRestante(): number {
    return Math.max(0, Math.min(100, Math.round(((this.suscripcion?.diasTrialRestantes ?? 0) / this.trialTotal) * 100)));
  }

  get planElegido() {
    return this.catalogo?.planes.find((plan) => plan.codigo === this.planCodigo);
  }

  get porAsiento(): boolean {
    return (this.planElegido?.precioPorProfesional || 0) > 0;
  }

  get precioPago(): PrecioSaas | null {
    if (!this.suscripcion) return null;
    return this.ciclo === 'ANUAL' ? this.suscripcion.precioAnual : this.suscripcion.precioMensual;
  }

  toggleAddon(codigo: string, activo: boolean): void {
    if (activo && !this.addons.includes(codigo)) this.addons = [...this.addons, codigo];
    if (!activo) this.addons = this.addons.filter((item) => item !== codigo);
  }

  abrirCambio(): void {
    if (this.suscripcion?.estado === 'CANCELADA') {
      this.error = 'La suscripción está cancelada.';
      this.toast.error(this.error);
      return;
    }
    this.operando = true;
    this.saas.cotizar({
      planCodigo: this.planCodigo,
      ciclo: this.ciclo,
      profesionalesContratados: Number(this.cupo) || 1,
      sedes: Math.max(1, this.suscripcion?.uso.centros || 1),
      addons: this.addons
    }).subscribe({
      next: (resp) => {
        this.operando = false;
        if (resp.status_code === 200) {
          this.precioCambio = resp.data;
          const cupo = this.porAsiento ? ` Cupo: ${this.cupo} profesionales.` : '';
          this.modal.confirm(
            '¿Confirmás el cambio?',
            `Vas a pasar de ${this.suscripcion?.plan?.nombre} (${etiquetaCiclo(this.suscripcion?.ciclo)}) a ${this.planElegido?.nombre || this.planCodigo} (${etiquetaCiclo(this.ciclo)}).${cupo}`,
            `Nuevo importe: ${formatoArs(resp.data.neto)} + IVA (${formatoArs(resp.data.total)} con IVA).`
          ).then(() => this.confirmarCambio()).catch(() => undefined);
        } else {
          this.error = resp.status_text || 'No se pudo calcular el cambio.';
          this.toast.error(this.error);
        }
      },
      error: (err) => {
        this.operando = false;
        if (esFalloDeAcceso(err)) return;
        this.error = 'No se pudo calcular el cambio.';
        this.toast.error(this.error);
      }
    });
  }

  pedirPago(): void {
    const aviso = this.formularioSucio
      ? ' Hay cambios sin guardar: este pago usa el plan que ya está vigente.'
      : '';
    const total = this.precioPago ? ` Total con IVA: ${formatoArs(this.precioPago.total)}.` : '';
    this.modal.confirm(
      '¿Confirmás el pago simulado?',
      `Se registra un pago del plan vigente ${this.suscripcion?.plan?.nombre}, ciclo ${etiquetaCiclo(this.ciclo)}.${aviso}`,
      total
    ).then(() => this.confirmarPago()).catch(() => undefined);
  }

  confirmarCambio(): void {
    this.operando = true;
    this.error = '';
    this.ok = '';
    this.saas.cambiarPlan({
      planCodigo: this.planCodigo,
      ciclo: this.ciclo,
      profesionalesContratados: Number(this.cupo),
      addons: this.addons
    }).subscribe({
      next: (resp) => {
        this.operando = false;
        if (resp.status_code === 200) {
          this.ok = 'Los cambios del plan quedaron guardados.';
          this.toast.success(this.ok);
          this.aplicar(resp.data);
        } else {
          this.error = resp.status_text || 'No se pudo cambiar el plan.';
          this.toast.error(this.error);
        }
      },
      error: (err) => {
        this.operando = false;
        if (esFalloDeAcceso(err)) return;
        this.error = 'No se pudo cambiar el plan.';
        this.toast.error(this.error);
      }
    });
  }

  confirmarPago(): void {
    this.operando = true;
    this.error = '';
    this.ok = '';
    this.saas.pagar(this.ciclo).subscribe({
      next: (resp) => {
        this.operando = false;
        if (resp.status_code === 200) {
          this.ok = `Pago simulado registrado: ${formatoArs(resp.data.total)} con IVA.`;
          this.toast.success(this.ok);
          this.cargar();
        } else {
          this.error = resp.status_text || 'No se pudo registrar el pago.';
          this.toast.error(this.error);
        }
      },
      error: (err) => {
        this.operando = false;
        if (esFalloDeAcceso(err)) return;
        this.error = 'No se pudo registrar el pago.';
        this.toast.error(this.error);
      }
    });
  }

  crearSede(): void {
    this.erroresSede = {};
    if (!this.sede.nombre.trim()) this.erroresSede['nombre'] = 'Ingresá el nombre de la sede.';
    if (!this.sede.direccion.trim()) this.erroresSede['direccion'] = 'Ingresá la dirección.';
    if (!this.sede.localidad.trim()) this.erroresSede['localidad'] = 'Ingresá la localidad.';
    if (!this.sede.provincia.trim()) this.erroresSede['provincia'] = 'Ingresá la provincia.';
    if (!/^\d+$/.test(this.sede.telefono.trim())) this.erroresSede['telefono'] = 'El teléfono tiene que tener solo números.';
    if (this.sede.latitud == null || Number(this.sede.latitud) < -90 || Number(this.sede.latitud) > 90) {
      this.erroresSede['latitud'] = 'La latitud tiene que estar entre -90 y 90.';
    }
    if (this.sede.longitud == null || Number(this.sede.longitud) < -180 || Number(this.sede.longitud) > 180) {
      this.erroresSede['longitud'] = 'La longitud tiene que estar entre -180 y 180.';
    }
    if (Object.keys(this.erroresSede).length) return;

    this.operando = true;
    this.error = '';
    this.ok = '';
    this.saas.crearSede({
      ...this.sede,
      latitud: Number(this.sede.latitud),
      longitud: Number(this.sede.longitud)
    }).subscribe({
      next: (resp) => {
        this.operando = false;
        if (resp.status_code === 200) {
          this.ok = `Sede ${resp.data.nombre} creada.`;
          this.toast.success(this.ok);
          this.sede = { nombre: '', direccion: '', localidad: '', provincia: '', telefono: '', latitud: -34.6, longitud: -58.38 };
          this.cargar();
        } else {
          this.error = resp.status_text || 'No se pudo crear la sede.';
          this.toast.error(this.error);
        }
      },
      error: (err) => {
        this.operando = false;
        if (esFalloDeAcceso(err)) return;
        this.error = 'No se pudo crear la sede.';
        this.toast.error(this.error);
      }
    });
  }

  private aplicar(data: SuscripcionSaas): void {
    this.suscripcion = data;
    this.planCodigo = data.plan.codigo;
    this.ciclo = data.ciclo;
    this.cupo = data.profesionalesContratados;
    this.addons = [...(data.addons || [])];
  }

  private mismosAddons(a: string[], b: string[]): boolean {
    const izquierda = [...a].sort().join('|');
    const derecha = [...(b || [])].sort().join('|');
    return izquierda === derecha;
  }
}
