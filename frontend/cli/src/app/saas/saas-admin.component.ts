import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ChartData, ChartOptions } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { PaginationComponent } from '../pagination/pagination.component';
import { Observable, Subscription, combineLatest } from 'rxjs';
import { DataPackage } from '../data.package';
import { ModalService } from '../modal/modal.service';
import { ToastService } from '../services/toast.service';
import { AuthService } from '../inicio-sesion/auth.service';
import { UserContextService } from '../services/user-context.service';
import { SaasService } from './saas.service';
import {
  AddonSaas,
  CatalogoSaas,
  PagoSaas,
  PlanSaas,
  ResumenSaas,
  SuscripcionSaas,
  claseEstado,
  esFalloDeAcceso,
  etiquetaCiclo,
  etiquetaEstado,
  etiquetaPago,
  etiquetaTipoPago,
  formatoArs,
  formatoFecha,
  formatoPorcentaje,
  nivelUso,
  porcentajeUso,
  tope
} from './saas.models';

type DialogoAdmin = '' | 'suspender' | 'cancelar' | 'cambiar';

@Component({
  selector: 'app-saas-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BaseChartDirective, PaginationComponent],
  templateUrl: './saas-admin.component.html',
  styleUrl: './saas.css',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SaasAdminComponent implements OnInit, OnDestroy {
  @ViewChild('dialogoRef') dialogoRef?: ElementRef<HTMLElement>;

  vista = 'dashboard';
  resumen: ResumenSaas | null = null;
  suscripciones: SuscripcionSaas[] = [];
  detalle: SuscripcionSaas | null = null;
  catalogo: CatalogoSaas | null = null;
  pagos: PagoSaas[] = [];
  filtroEstado = '';
  filtroPlan = '';
  filtroTexto = '';
  filtroPago = '';
  suscripcionId: number | null = null;
  diasExtension = 7;
  motivo = '';
  planDestino = '';
  error = '';
  ok = '';
  cargando = true;
  operando = false;
  dialogo: DialogoAdmin = '';
  paginaLista = 1;
  paginaPagos = 1;
  readonly porPagina = 8;
  formatoArs = formatoArs;
  formatoFecha = formatoFecha;
  formatoPorcentaje = formatoPorcentaje;
  etiquetaEstado = etiquetaEstado;
  etiquetaPago = etiquetaPago;
  etiquetaTipoPago = etiquetaTipoPago;
  etiquetaCiclo = etiquetaCiclo;
  claseEstado = claseEstado;
  tope = tope;
  porcentajeUso = porcentajeUso;
  nivelUso = nivelUso;

  hayGraficoEstados = false;
  hayGraficoMrr = false;
  estadosChart: ChartData<'doughnut'> = { labels: [], datasets: [{ data: [] }] };
  mrrChart: ChartData<'bar'> = { labels: [], datasets: [{ data: [], label: 'MRR neto' }] };
  readonly opcionesDona: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { color: '#212529', boxWidth: 12, padding: 12 } }
    }
  };
  readonly opcionesBarras: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: '#6C757D' }, grid: { display: false } },
      y: { beginAtZero: true, ticks: { color: '#6C757D' } }
    }
  };

  private sub: Subscription | null = null;
  private ticket = 0;
  private listaToken = 0;
  private retornoFoco: HTMLElement | null = null;
  private readonly colores = ['#198754', '#0d6efd', '#dc3545', '#ffc107', '#6c757d', '#842029', '#6f42c1', '#fd7e14'];

  constructor(
    private saas: SaasService,
    private route: ActivatedRoute,
    private toast: ToastService,
    private auth: AuthService,
    private users: UserContextService,
    private modal: ModalService
  ) {}

  ngOnInit(): void {
    this.sub = combineLatest([this.route.data, this.route.paramMap]).subscribe(([data, params]) => {
      this.vista = (data['vista'] as string) || 'dashboard';
      const id = params.get('id');
      this.suscripcionId = id ? Number(id) : null;
      this.ok = '';
      this.error = '';
      this.dialogo = '';
      this.cargar();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get churn(): number {
    if (!this.resumen) return 0;
    const total = this.totalSuscripciones;
    if (!total) return 0;
    return (this.resumen.canceladas / total) * 100;
  }

  get totalSuscripciones(): number {
    if (!this.resumen) return 0;
    const r = this.resumen;
    return r.activas + r.trials + r.vencidas + r.morosas + r.suspendidas + r.canceladas;
  }

  get mezclaEstados(): { clave: string; etiqueta: string; valor: number; clase: string; color: string }[] {
    if (!this.resumen) return [];
    const r = this.resumen;
    return [
      { clave: 'activas', etiqueta: 'Activas', valor: r.activas, clase: 'activa', color: '#198754' },
      { clave: 'trials', etiqueta: 'En prueba', valor: r.trials, clase: 'trial', color: '#0d6efd' },
      { clave: 'morosas', etiqueta: 'Morosas', valor: r.morosas, clase: 'morosa', color: '#dc3545' },
      { clave: 'vencidas', etiqueta: 'Vencidas', valor: r.vencidas, clase: 'vencida', color: '#ffc107' },
      { clave: 'suspendidas', etiqueta: 'Suspendidas', valor: r.suspendidas, clase: 'suspendida', color: '#6c757d' },
      { clave: 'canceladas', etiqueta: 'Canceladas', valor: r.canceladas, clase: 'cancelada', color: '#842029' }
    ];
  }

  get listaPagina(): SuscripcionSaas[] {
    const inicio = (this.paginaLista - 1) * this.porPagina;
    return this.suscripciones.slice(inicio, inicio + this.porPagina);
  }

  get paginasLista(): number {
    return Math.max(1, Math.ceil(this.suscripciones.length / this.porPagina));
  }

  get pagosFiltrados(): PagoSaas[] {
    const q = this.filtroPago.trim().toLowerCase();
    if (!q) return this.pagos;
    return this.pagos.filter((pago) =>
      (pago.organizacion || '').toLowerCase().includes(q)
      || (pago.plan || '').toLowerCase().includes(q)
      || (pago.estado || '').toLowerCase().includes(q)
      || (pago.mpPaymentId || '').toLowerCase().includes(q)
    );
  }

  get pagosPagina(): PagoSaas[] {
    const inicio = (this.paginaPagos - 1) * this.porPagina;
    return this.pagosFiltrados.slice(inicio, inicio + this.porPagina);
  }

  get paginasPagos(): number {
    return Math.max(1, Math.ceil(this.pagosFiltrados.length / this.porPagina));
  }

  get hayFiltroSuscripciones(): boolean {
    return !!(this.filtroEstado || this.filtroPlan || this.filtroTexto.trim());
  }

  cargar(): void {
    if (!this.puedeOperar()) {
      this.cargando = false;
      return;
    }
    const actual = ++this.ticket;
    this.cargando = true;
    const sigue = () => actual === this.ticket;
    const listo = () => {
      if (sigue()) this.cargando = false;
    };

    if (this.vista === 'dashboard') {
      this.saas.resumen().subscribe({
        next: (resp) => {
          if (!sigue()) return;
          this.cargando = false;
          if (resp.status_code === 200) {
            this.resumen = resp.data;
            this.armarGraficos(resp.data);
          } else {
            this.error = resp.status_text || 'No se pudo cargar el tablero.';
            this.toast.error(this.error);
          }
        },
        error: () => {
          if (!sigue()) return;
          this.cargando = false;
          this.error = 'No se pudo cargar el tablero.';
        }
      });
    }
    if (this.vista === 'suscripciones') {
      this.buscar(listo);
    } else {
      this.listaToken++;
    }
    if (this.vista === 'detalle' && this.suscripcionId) {
      this.saas.detalle(this.suscripcionId).subscribe({
        next: (resp) => {
          if (!sigue()) return;
          this.cargando = false;
          if (resp.status_code === 200) {
            this.detalle = resp.data;
            this.planDestino = resp.data.plan.codigo;
          } else {
            this.error = resp.status_text || 'No se pudo abrir la organización.';
          }
        },
        error: () => {
          if (!sigue()) return;
          this.cargando = false;
          this.error = 'No se pudo abrir la organización.';
        }
      });
      this.saas.planes().subscribe({
        next: (resp) => {
          if (sigue() && resp.status_code === 200) this.catalogo = resp.data;
        }
      });
    }
    if (this.vista === 'planes') {
      this.saas.catalogoAdmin().subscribe({
        next: (resp) => {
          if (!sigue()) return;
          this.cargando = false;
          this.catalogo = resp.status_code === 200 ? resp.data : null;
          if (resp.status_code !== 200) this.error = resp.status_text || 'No se pudieron cargar los planes.';
        },
        error: () => {
          if (!sigue()) return;
          this.cargando = false;
          this.error = 'No se pudieron cargar los planes.';
        }
      });
    }
    if (this.vista === 'pagos') {
      this.saas.pagos().subscribe({
        next: (resp) => {
          if (!sigue()) return;
          this.cargando = false;
          this.pagos = resp.status_code === 200 ? resp.data : [];
          this.paginaPagos = 1;
        },
        error: () => {
          if (!sigue()) return;
          this.cargando = false;
          this.error = 'No se pudieron cargar los pagos.';
        }
      });
    }
  }

  buscar(alTerminar?: () => void): void {
    if (!this.puedeOperar()) {
      this.cargando = false;
      alTerminar?.();
      return;
    }
    const token = ++this.listaToken;
    this.saas.suscripciones(this.filtroEstado, this.filtroPlan, this.filtroTexto).subscribe({
      next: (resp) => {
        if (token !== this.listaToken || this.vista !== 'suscripciones') return;
        if (resp.status_code === 200) {
          this.suscripciones = resp.data || [];
          this.paginaLista = 1;
          this.error = '';
        } else {
          this.error = resp.status_text || 'No se pudo filtrar el listado.';
        }
        alTerminar?.();
        this.cargando = false;
      },
      error: (err) => {
        if (token !== this.listaToken || this.vista !== 'suscripciones') return;
        this.cargando = false;
        this.suscripciones = [];
        alTerminar?.();
        if (esFalloDeAcceso(err)) {
          this.error = 'No se pudo abrir el listado de suscripciones.';
          return;
        }
        this.error = 'No se pudo filtrar el listado.';
        this.toast.error(this.error);
      }
    });
  }

  alFiltrarPagos(): void {
    this.paginaPagos = 1;
  }

  abrir(tipo: DialogoAdmin, event?: Event): void {
    this.retornoFoco = (event?.currentTarget as HTMLElement) ?? null;
    this.dialogo = tipo;
    setTimeout(() => this.dialogoRef?.nativeElement.querySelector<HTMLElement>('[data-autofocus]')?.focus());
  }

  cerrarDialogo(): void {
    this.dialogo = '';
    setTimeout(() => this.retornoFoco?.focus());
  }

  onDialogKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.cerrarDialogo();
      return;
    }
    if (event.key !== 'Tab' || !this.dialogoRef) return;
    const items = Array.from(this.dialogoRef.nativeElement.querySelectorAll<HTMLElement>('button, [href], input, select, textarea'))
      .filter((el) => !el.hasAttribute('disabled'));
    if (!items.length) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  extender(): void {
    if (!this.detalle) return;
    this.actuar(this.saas.extenderTrial(this.detalle.id, Number(this.diasExtension)), false);
  }

  suspender(): void {
    if (!this.detalle) return;
    this.actuar(this.saas.suspender(this.detalle.id, this.motivo), true);
  }

  reactivar(): void {
    if (!this.detalle) return;
    this.actuar(this.saas.reactivar(this.detalle.id), false);
  }

  cancelar(): void {
    if (!this.detalle) return;
    this.actuar(this.saas.cancelar(this.detalle.id, this.motivo), true);
  }

  cambiarPlan(): void {
    if (!this.detalle) return;
    this.actuar(this.saas.cambiarPlanAdmin(this.detalle.id, { planCodigo: this.planDestino }), true);
  }

  guardarConfig(): void {
    if (!this.catalogo) return;
    this.saas.guardarConfiguracion(this.catalogo.configuracion).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.catalogo!.configuracion = resp.data;
          this.ok = 'Configuración comercial actualizada.';
          this.error = '';
          this.toast.success(this.ok);
        } else {
          this.error = resp.status_text;
          this.toast.error(this.error);
        }
      },
      error: () => {
        this.error = 'No se pudo guardar la configuración.';
        this.toast.error(this.error);
      }
    });
  }

  guardarPlan(plan: PlanSaas): void {
    this.saas.guardarPlan(plan).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.ok = `Plan ${plan.nombre} actualizado.`;
          this.error = '';
          this.toast.success(this.ok);
        } else {
          this.error = resp.status_text;
          this.toast.error(this.error);
        }
      },
      error: () => {
        this.error = 'No se pudo guardar el plan.';
        this.toast.error(this.error);
      }
    });
  }

  guardarAddon(addon: AddonSaas): void {
    this.saas.guardarAddon(addon).subscribe({
      next: (resp) => {
        if (resp.status_code === 200) {
          this.ok = `Complemento ${addon.nombre} actualizado.`;
          this.error = '';
          this.toast.success(this.ok);
        } else {
          this.error = resp.status_text;
          this.toast.error(this.error);
        }
      },
      error: () => {
        this.error = 'No se pudo guardar el complemento.';
        this.toast.error(this.error);
      }
    });
  }

  ancho(valor: number): number {
    const total = this.totalSuscripciones;
    if (!total) return 0;
    return (valor / total) * 100;
  }

  pedirSuspender(): void {
    if (!this.detalle) return;
    const motivo = this.motivo?.trim();
    this.modal.confirm(
      '¿Suspendés esta suscripción?',
      `${this.detalle.organizacion.nombre} pasa a solo lectura hasta que la reactives.`,
      motivo ? `Motivo: ${motivo}` : 'Podés escribir un motivo en el campo de arriba.'
    ).then(() => this.suspender()).catch(() => undefined);
  }

  pedirCancelar(): void {
    if (!this.detalle) return;
    const motivo = this.motivo?.trim();
    this.modal.confirm(
      '¿Cancelás esta suscripción?',
      `La cuenta de ${this.detalle.organizacion.nombre} queda en solo lectura. Los datos no se borran.`,
      motivo ? `Motivo: ${motivo}` : 'Podés escribir un motivo en el campo de arriba.'
    ).then(() => this.cancelar()).catch(() => undefined);
  }

  pedirCambio(): void {
    if (!this.detalle) return;
    this.modal.confirm(
      '¿Confirmás el cambio de plan?',
      `${this.detalle.organizacion.nombre} pasa de ${this.detalle.plan.nombre} a ${this.planDestino}.`,
      'El precio se recalcula con el plan nuevo.'
    ).then(() => this.cambiarPlan()).catch(() => undefined);
  }

  private puedeOperar(): boolean {
    return this.auth.isAuthenticated() && this.users.isSuperAdmin;
  }

  private actuar(llamada: Observable<DataPackage<SuscripcionSaas>>, cerrar: boolean): void {
    this.operando = true;
    this.error = '';
    llamada.subscribe({
      next: (resp) => {
        this.operando = false;
        this.despues(resp.status_code, resp.status_text, resp.data);
        if (cerrar && resp.status_code === 200) this.cerrarDialogo();
      },
      error: (err) => {
        this.operando = false;
        if (esFalloDeAcceso(err)) return;
        this.error = 'No se pudo completar la operación.';
        this.toast.error(this.error);
      }
    });
  }

  private despues(status: number, texto: string, data: SuscripcionSaas): void {
    if (status === 200) {
      this.detalle = data;
      this.planDestino = data?.plan?.codigo || this.planDestino;
      this.ok = texto || 'Listo.';
      this.error = '';
      this.toast.success(this.ok);
    } else {
      this.error = texto || 'No se pudo completar la operación.';
      this.toast.error(this.error);
    }
  }

  private armarGraficos(resumen: ResumenSaas): void {
    const estados = this.mezclaEstados;
    this.hayGraficoEstados = estados.some((item) => item.valor > 0);
    this.estadosChart = {
      labels: estados.map((item) => item.etiqueta),
      datasets: [{
        data: estados.map((item) => item.valor),
        backgroundColor: this.colores.slice(0, estados.length),
        borderWidth: 0
      }]
    };
    const planes = resumen.porPlan || [];
    this.hayGraficoMrr = planes.some((plan) => Number(plan.mrrNeto) > 0);
    this.mrrChart = {
      labels: planes.map((plan) => plan.nombre),
      datasets: [{
        label: 'MRR neto',
        data: planes.map((plan) => Number(plan.mrrNeto) || 0),
        backgroundColor: '#0d6efd',
        borderRadius: 8,
        maxBarThickness: 48
      }]
    };
  }
}
