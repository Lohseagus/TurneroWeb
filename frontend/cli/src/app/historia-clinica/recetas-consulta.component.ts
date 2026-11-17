import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ConsultaHce } from './hce.models';
import { RecetaService } from './receta.service';
import { CatalogoMedicamento, OrdenEstudio, Receta, RecetaItem, itemVacio } from './receta.models';

@Component({
  selector: 'app-recetas-consulta',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recetas-consulta.component.html',
  styleUrl: './hce.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RecetasConsultaComponent implements OnInit {
  @Input({ required: true }) consulta!: ConsultaHce;

  tab: 'receta' | 'orden' = 'receta';
  recetas: Receta[] = [];
  ordenes: OrdenEstudio[] = [];
  sugerencias: CatalogoMedicamento[] = [];
  sugerenciaPara = 0;
  form: Receta = { tipo: 'COMUN', items: [itemVacio()] };
  orden: OrdenEstudio = { urgencia: 'NORMAL', items: [{ nombrePractica: '' }] };
  motivo = '';
  error = '';
  aviso = '';
  guardando = false;

  constructor(private api: RecetaService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.nueva();
    this.nuevaOrden();
    this.cargar();
  }

  get bloqueada(): boolean {
    return !!this.form.id && this.form.estado !== 'BORRADOR';
  }

  get ordenBloqueada(): boolean {
    return !!this.orden.id && this.orden.estado !== 'BORRADOR';
  }

  cargar(): void {
    if (!this.consulta?.id) return;
    this.api.porConsulta(this.consulta.id).subscribe({
      next: res => {
        this.recetas = res.data || [];
        this.cdr.markForCheck();
      },
      error: err => this.fallo(err)
    });
    this.api.ordenesPorConsulta(this.consulta.id).subscribe({
      next: res => {
        this.ordenes = res.data || [];
        this.cdr.markForCheck();
      },
      error: err => this.fallo(err)
    });
  }

  nueva(): void {
    const diagnostico = this.consulta?.diagnosticos?.[0];
    this.form = {
      consultaId: this.consulta?.id,
      tipo: 'COMUN',
      estado: 'BORRADOR',
      diagnosticoCode: diagnostico?.code,
      pacienteNombre: this.consulta?.pacienteNombre,
      pacienteApellido: this.consulta?.pacienteApellido,
      pacienteDni: this.consulta?.dni,
      items: [itemVacio()]
    };
    this.motivo = '';
    this.cdr.markForCheck();
  }

  nuevaOrden(): void {
    const diagnostico = this.consulta?.diagnosticos?.[0];
    this.orden = {
      consultaId: this.consulta?.id,
      urgencia: 'NORMAL',
      estado: 'BORRADOR',
      diagnosticoCode: diagnostico?.code,
      items: [{ nombrePractica: '' }]
    };
    this.cdr.markForCheck();
  }

  abrir(receta: Receta): void {
    this.tab = 'receta';
    this.form = { ...receta, items: (receta.items || []).map(item => ({ ...item })) };
    this.cdr.markForCheck();
  }

  abrirOrden(orden: OrdenEstudio): void {
    this.tab = 'orden';
    this.orden = { ...orden, items: (orden.items || []).map(item => ({ ...item })) };
    this.cdr.markForCheck();
  }

  buscar(termino: string, index: number): void {
    this.sugerenciaPara = index;
    if (!termino || termino.trim().length < 2) {
      this.sugerencias = [];
      this.cdr.markForCheck();
      return;
    }
    this.api.catalogo(termino.trim()).subscribe({
      next: res => {
        this.sugerencias = res.data || [];
        this.cdr.markForCheck();
      },
      error: () => {
        this.sugerencias = [];
        this.cdr.markForCheck();
      }
    });
  }

  elegir(med: CatalogoMedicamento): void {
    const item = this.form.items[this.sugerenciaPara];
    if (!item) return;
    item.nombreGenerico = med.nombreGenerico;
    item.concentracion = med.concentracion;
    item.formaFarmaceutica = med.formaFarmaceutica;
    item.via = med.via || 'oral';
    item.lista = med.lista;
    item.catalogoId = med.id;
    this.sugerencias = [];
    this.error = this.controlada(item)
      ? 'Esa sustancia requiere receta oficial archivada y no se emite por acá.'
      : '';
    this.cdr.markForCheck();
  }

  agregarItem(): void {
    if (this.form.items.length >= 3) {
      this.error = 'Una receta admite hasta 3 medicamentos.';
      this.cdr.markForCheck();
      return;
    }
    this.form.items.push(itemVacio());
    this.cdr.markForCheck();
  }

  quitarItem(index: number): void {
    this.form.items.splice(index, 1);
    if (!this.form.items.length) this.form.items.push(itemVacio());
    this.cdr.markForCheck();
  }

  agregarPractica(): void {
    this.orden.items.push({ nombrePractica: '' });
    this.cdr.markForCheck();
  }

  quitarPractica(index: number): void {
    this.orden.items.splice(index, 1);
    if (!this.orden.items.length) this.orden.items.push({ nombrePractica: '' });
    this.cdr.markForCheck();
  }

  guardar(): void {
    this.enviarReceta(false);
  }

  emitir(): void {
    this.enviarReceta(true);
  }

  anular(): void {
    if (!this.form.id || !this.motivo.trim()) {
      this.error = 'Para anular hace falta un motivo';
      this.cdr.markForCheck();
      return;
    }
    this.guardando = true;
    this.api.anular(this.form.id, this.motivo.trim()).subscribe({
      next: res => {
        this.form = this.normalizar(res.data);
        this.aviso = 'Receta anulada';
        this.motivo = '';
        this.guardando = false;
        this.cargar();
      },
      error: err => this.fallo(err)
    });
  }

  pdf(): void {
    if (!this.form.id) return;
    this.api.pdf(this.form.id).subscribe(blob => this.bajar(blob, `receta-${this.form.id}.pdf`));
  }

  guardarOrden(): void {
    this.enviarOrden(false);
  }

  emitirOrden(): void {
    this.enviarOrden(true);
  }

  anularOrden(): void {
    if (!this.orden.id || !this.motivo.trim()) {
      this.error = 'Para anular hace falta un motivo';
      this.cdr.markForCheck();
      return;
    }
    this.guardando = true;
    this.api.anularOrden(this.orden.id, this.motivo.trim()).subscribe({
      next: res => {
        this.orden = res.data;
        this.orden.items = this.orden.items || [];
        this.aviso = 'Orden anulada';
        this.motivo = '';
        this.guardando = false;
        this.cargar();
      },
      error: err => this.fallo(err)
    });
  }

  pdfOrden(): void {
    if (!this.orden.id) return;
    this.api.pdfOrden(this.orden.id).subscribe(blob => this.bajar(blob, `orden-${this.orden.id}.pdf`));
  }

  controlada(item: RecetaItem): boolean {
    const nombre = (item.nombreGenerico || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
    const lista = ['clonazepam', 'alprazolam', 'diazepam', 'lorazepam', 'bromazepam', 'codeina', 'morfina', 'tramadol', 'fentanilo'];
    return (!!item.lista && item.lista !== 'NINGUNA') || lista.some(controlada => nombre.includes(controlada));
  }

  private enviarReceta(emitir: boolean): void {
    this.guardando = true;
    this.error = '';
    const body = this.cuerpoReceta();
    const pedido = this.form.id ? this.api.actualizar(this.form.id, body) : this.api.crear(body);
    pedido.subscribe({
      next: res => {
        this.form = this.normalizar(res.data);
        if (!emitir) {
          this.aviso = 'Borrador guardado';
          this.guardando = false;
          this.cargar();
          return;
        }
        this.api.emitir(this.form.id as number).subscribe({
          next: emitida => {
            this.form = this.normalizar(emitida.data);
            this.aviso = 'Receta emitida';
            this.guardando = false;
            this.cargar();
          },
          error: err => this.fallo(err)
        });
      },
      error: err => this.fallo(err)
    });
  }

  private enviarOrden(emitir: boolean): void {
    this.guardando = true;
    this.error = '';
    const body = this.cuerpoOrden();
    const pedido = this.orden.id ? this.api.actualizarOrden(this.orden.id, body) : this.api.crearOrden(body);
    pedido.subscribe({
      next: res => {
        this.orden = res.data;
        this.orden.items = this.orden.items || [];
        if (!emitir) {
          this.aviso = 'Orden guardada';
          this.guardando = false;
          this.cargar();
          return;
        }
        this.api.emitirOrden(this.orden.id as number).subscribe({
          next: emitida => {
            this.orden = emitida.data;
            this.aviso = 'Orden emitida';
            this.guardando = false;
            this.cargar();
          },
          error: err => this.fallo(err)
        });
      },
      error: err => this.fallo(err)
    });
  }

  private cuerpoReceta(): object {
    return {
      consultaId: this.consulta.id,
      tipo: this.form.tipo || 'COMUN',
      diagnosticoCode: this.form.diagnosticoCode,
      sexo: this.form.pacienteSexo || null,
      fechaNacimiento: this.form.pacienteNacimiento || null,
      numeroAfiliado: this.form.numeroAfiliado,
      obraSocialNombre: this.form.obraSocialNombre,
      items: this.form.items
    };
  }

  private cuerpoOrden(): object {
    return {
      consultaId: this.consulta.id,
      diagnosticoCode: this.orden.diagnosticoCode,
      urgencia: this.orden.urgencia || 'NORMAL',
      indicacionClinica: this.orden.indicacionClinica,
      items: this.orden.items
    };
  }

  private normalizar(receta: Receta): Receta {
    receta.items = receta.items || [];
    return receta;
  }

  private bajar(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = nombre;
    link.click();
    URL.revokeObjectURL(url);
  }

  private fallo(err: HttpErrorResponse): void {
    this.guardando = false;
    this.error = err.error?.status_text || 'No se pudo completar la operación';
    this.cdr.markForCheck();
  }
}
