import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SaasService } from './saas.service';
import {
  AddonSaas,
  CatalogoSaas,
  PlanSaas,
  PrecioSaas,
  esPlanRecomendado,
  esFalloDeAcceso,
  formatoArs,
  iconoPlan
} from './saas.models';

interface CeldaComparacion {
  texto: string;
  ok: boolean | null;
}

interface FilaComparacion {
  etiqueta: string;
  valores: CeldaComparacion[];
}

@Component({
  selector: 'app-planes-publicos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './planes-publicos.component.html',
  styleUrls: ['./saas.css', './planes-publicos.component.css'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PlanesPublicosComponent implements OnInit, OnDestroy {
  catalogo: CatalogoSaas | null = null;
  error = '';
  cargando = true;
  ciclo: 'MENSUAL' | 'ANUAL' = 'MENSUAL';
  cotizacion = {
    planCodigo: 'CENTRO',
    profesionales: 1,
    sedes: 1,
    addons: [] as string[]
  };
  precio: PrecioSaas | null = null;
  cotizando = false;
  cotizacionError = '';
  formatoArs = formatoArs;
  iconoPlan = iconoPlan;
  esPlanRecomendado = esPlanRecomendado;
  private cotizarTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private saas: SaasService) {}

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    if (this.cotizarTimer) {
      clearTimeout(this.cotizarTimer);
    }
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.saas.planes().subscribe({
      next: (resp) => {
        this.cargando = false;
        if (resp.status_code === 200 && resp.data) {
          this.catalogo = resp.data;
          const inicial = resp.data.planes.find((plan) => plan.codigo === 'CENTRO') || resp.data.planes[0];
          this.cotizacion.planCodigo = inicial?.codigo || '';
          this.alCambiarPlanCotizacion();
        } else {
          this.error = resp.status_text || 'No se pudieron cargar los planes.';
        }
      },
      error: (err) => {
        this.cargando = false;
        if (esFalloDeAcceso(err)) return;
        this.error = 'No se pudieron cargar los planes.';
      }
    });
  }

  get planCotizado(): PlanSaas | undefined {
    return this.catalogo?.planes.find((plan) => plan.codigo === this.cotizacion.planCodigo);
  }

  get codigoRecomendado(): string {
    const planes = this.catalogo?.planes || [];
    return planes.find((plan) => plan.codigo === 'CENTRO')?.codigo || planes[0]?.codigo || 'CENTRO';
  }

  /** Plan que muestra la comparación en celular, donde entra uno por vez. */
  planMovil = '';

  get indiceMovil(): number {
    const planes = this.catalogo?.planes || [];
    const indice = planes.findIndex((plan) => plan.codigo === (this.planMovil || this.codigoRecomendado));
    return indice < 0 ? 0 : indice;
  }

  get porAsiento(): boolean {
    return (this.planCotizado?.precioPorProfesional || 0) > 0;
  }

  get puedeElegirSedes(): boolean {
    const plan = this.planCotizado;
    if (!plan) return false;
    return plan.precioPorSedeExtra != null || (plan.maxCentros != null && plan.maxCentros > 1) || (plan.sedesIncluidas || 1) > 1;
  }

  ejemplo(plan: PlanSaas): PrecioSaas {
    return this.ciclo === 'ANUAL' ? plan.ejemploAnual : plan.ejemploMensual;
  }

  precioUnitario(plan: PlanSaas): number {
    const actual = this.ejemplo(plan);
    const asientos = (plan.precioPorProfesional || 0) > 0
      ? (actual.profesionalesFacturados || plan.minProfesionales || 1)
      : 1;
    if (this.ciclo === 'ANUAL') {
      return actual.neto / 12 / asientos;
    }
    return (plan.precioPorProfesional || 0) > 0 ? plan.precioPorProfesional : actual.neto;
  }

  precioLista(plan: PlanSaas): number {
    return (plan.precioPorProfesional || 0) > 0 ? plan.precioPorProfesional : plan.ejemploMensual.neto;
  }

  sufijoPrecio(plan: PlanSaas): string {
    return (plan.precioPorProfesional || 0) > 0 ? 'por profesional / mes' : 'por mes';
  }

  get comparacion(): FilaComparacion[] {
    const planes = this.catalogo?.planes || [];
    const celda = (texto: string, ok: boolean | null = null): CeldaComparacion => ({ texto, ok });
    const siNo = (valor: boolean): CeldaComparacion => celda(valor ? 'Sí' : 'No', valor);
    return [
      {
        etiqueta: 'Precio de referencia',
        valores: planes.map((plan) => celda(`${formatoArs(this.precioUnitario(plan))} ${this.sufijoPrecio(plan)}`))
      },
      {
        etiqueta: 'Médicos',
        valores: planes.map((plan) => celda(plan.maxMedicos == null ? 'Sin tope' : `Hasta ${plan.maxMedicos}`))
      },
      {
        etiqueta: 'Operadores',
        valores: planes.map((plan) => celda(plan.maxOperadores == null ? 'Sin tope' : `Hasta ${plan.maxOperadores}`))
      },
      {
        etiqueta: 'Consultorios',
        valores: planes.map((plan) => celda(plan.maxConsultorios == null ? 'Sin tope' : `Hasta ${plan.maxConsultorios}`))
      },
      {
        etiqueta: 'Sedes',
        valores: planes.map((plan) => {
          if ((plan.sedesIncluidas || 0) > 1) {
            return celda(`${plan.sedesIncluidas} incluidas`);
          }
          return celda(plan.maxCentros == null ? 'Sin tope' : `${plan.maxCentros}`);
        })
      },
      {
        etiqueta: 'Sede extra',
        valores: planes.map((plan) => celda(plan.precioPorSedeExtra ? `${formatoArs(plan.precioPorSedeExtra)} + IVA` : 'No aplica', plan.precioPorSedeExtra ? true : null))
      },
      { etiqueta: 'Reportes', valores: planes.map((plan) => siNo(plan.reportesBasicos)) },
      { etiqueta: 'Reportes de la red', valores: planes.map((plan) => siNo(plan.reportesConsolidados)) },
      { etiqueta: 'Soporte prioritario', valores: planes.map((plan) => siNo(plan.soportePrioritario)) },
      { etiqueta: 'Prueba sin tarjeta', valores: planes.map(() => siNo(true)) }
    ];
  }

  get preguntas(): { q: string; a: string }[] {
    const config = this.catalogo?.configuracion;
    const dias = config?.diasTrial ?? 30;
    const iva = config?.ivaPorcentaje ?? 21;
    const descuento = config?.descuentoAnualPorcentaje ?? 15;
    const consultorio = this.catalogo?.planes.find((plan) => plan.codigo === 'CONSULTORIO');
    const red = this.catalogo?.planes.find((plan) => plan.codigo === 'RED');
    const cupo = consultorio
      ? `de ${consultorio.minProfesionales} a ${consultorio.maxProfesionales} profesionales`
      : 'según el cupo del plan';
    const sedesRed = red?.sedesIncluidas ?? 3;
    const extraRed = red?.precioPorSedeExtra ? formatoArs(red.precioPorSedeExtra) : 'el precio publicado';
    return [
      {
        q: '¿La prueba pide tarjeta?',
        a: `No. Son ${dias} días y no te pedimos tarjeta. Si no pagás al terminar, la cuenta queda en solo lectura: no se borra nada y tus pacientes siguen viendo lo que ya tenían.`
      },
      {
        q: '¿Los precios incluyen IVA?',
        a: `No. Los importes del catálogo son netos, en pesos. Se suma el IVA del ${iva}%. En cada plan y en el cotizador ves el neto y el total con IVA.`
      },
      {
        q: '¿Cómo se aplica el descuento anual?',
        a: `El anual descuenta el ${descuento}% sobre el neto de 12 meses y después suma el IVA. El interruptor de arriba te muestra el mensual y el anual con ese descuento.`
      },
      {
        q: '¿Cómo se cobra Consultorio?',
        a: `Es un precio por profesional, ${cupo}. El ejemplo de la tarjeta usa el mínimo. Si necesitás otro cupo, armá la cotización más abajo.`
      },
      {
        q: '¿Qué incluye el plan Red?',
        a: `Incluye ${sedesRed} sedes. Cada sede extra sale ${extraRed} + IVA. También trae reportes consolidados y soporte prioritario.`
      },
      {
        q: '¿WhatsApp y SMS ya envían mensajes?',
        a: 'Todavía no. Son complementos con el precio reservado para cuando se conecte el proveedor. Podés sumarlos en la cotización para ver cómo queda el total.'
      },
      {
        q: '¿Puedo cambiar de plan después?',
        a: 'Sí. Si sos administrador del centro, desde Mi plan cambiás el plan, el ciclo o el cupo. El cambio te pide confirmación antes de guardarse.'
      },
      {
        q: '¿Qué pasa si la suscripción queda vencida o morosa?',
        a: 'La cuenta pasa a solo lectura. No se crean turnos ni recursos nuevos. Un pago la vuelve a dejar activa.'
      }
    ];
  }

  elegirCiclo(ciclo: 'MENSUAL' | 'ANUAL'): void {
    this.ciclo = ciclo;
    this.programarCotizacion();
  }

  alCambiarPlanCotizacion(): void {
    const plan = this.planCotizado;
    if (!plan) return;
    if ((plan.precioPorProfesional || 0) > 0) {
      const min = plan.minProfesionales || 1;
      const max = plan.maxProfesionales || min;
      const actual = Number(this.cotizacion.profesionales) || min;
      this.cotizacion.profesionales = Math.min(max, Math.max(min, actual));
    } else {
      this.cotizacion.profesionales = 1;
    }
    let sedes = Math.max(1, plan.sedesIncluidas || 1);
    if (plan.maxCentros != null) {
      sedes = Math.min(sedes, Math.max(1, plan.maxCentros));
    }
    this.cotizacion.sedes = sedes;
    this.programarCotizacion();
  }

  cambiarCupo(): void {
    const plan = this.planCotizado;
    if (plan && (plan.precioPorProfesional || 0) > 0) {
      const min = plan.minProfesionales || 1;
      const max = plan.maxProfesionales || min;
      this.cotizacion.profesionales = Math.min(max, Math.max(min, Number(this.cotizacion.profesionales) || min));
    }
    if (plan?.maxCentros != null) {
      this.cotizacion.sedes = Math.min(plan.maxCentros, Math.max(1, Number(this.cotizacion.sedes) || 1));
    } else {
      this.cotizacion.sedes = Math.max(1, Number(this.cotizacion.sedes) || 1);
    }
    this.programarCotizacion();
  }

  toggleAddon(codigo: string, activo: boolean): void {
    if (activo && !this.cotizacion.addons.includes(codigo)) {
      this.cotizacion.addons = [...this.cotizacion.addons, codigo];
    }
    if (!activo) {
      this.cotizacion.addons = this.cotizacion.addons.filter((item) => item !== codigo);
    }
    this.programarCotizacion();
  }

  usarEnCotizador(codigo: string): void {
    this.cotizacion.planCodigo = codigo;
    this.alCambiarPlanCotizacion();
    const destino = document.getElementById('cotizador');
    const reducir = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    destino?.scrollIntoView({ behavior: reducir ? 'auto' : 'smooth', block: 'start' });
  }

  irA(id: string): void {
    const destino = document.getElementById(id);
    const reducir = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    destino?.scrollIntoView({ behavior: reducir ? 'auto' : 'smooth', block: 'start' });
  }

  paramsRegistro(plan?: string): Record<string, string | number> {
    const params: Record<string, string | number> = {
      plan: plan || this.cotizacion.planCodigo,
      ciclo: this.ciclo
    };
    if (!plan) {
      params['profesionales'] = this.cotizacion.profesionales;
      if (this.cotizacion.addons.length) {
        params['addons'] = this.cotizacion.addons.join(',');
      }
    }
    return params;
  }

  addonPrecio(addon: AddonSaas): string {
    return `${formatoArs(addon.precioMensualNeto)} + IVA`;
  }

  private programarCotizacion(): void {
    if (this.cotizarTimer) {
      clearTimeout(this.cotizarTimer);
    }
    this.cotizarTimer = setTimeout(() => this.cotizar(), 250);
  }

  private cotizar(): void {
    if (!this.cotizacion.planCodigo) return;
    this.cotizando = true;
    this.cotizacionError = '';
    this.saas.cotizar({
      planCodigo: this.cotizacion.planCodigo,
      ciclo: this.ciclo,
      profesionalesContratados: Number(this.cotizacion.profesionales) || 1,
      sedes: Number(this.cotizacion.sedes) || 1,
      addons: this.cotizacion.addons
    }).subscribe({
      next: (resp) => {
        this.cotizando = false;
        if (resp.status_code === 200) {
          this.precio = resp.data;
        } else {
          this.precio = null;
          this.cotizacionError = resp.status_text || 'No se pudo calcular el precio.';
        }
      },
      error: () => {
        this.cotizando = false;
        this.precio = null;
        this.cotizacionError = 'No se pudo calcular el precio.';
      }
    });
  }
}
