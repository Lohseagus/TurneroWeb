import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { HceService } from './hce.service';
import { AdjuntoHce, Cie10, ConsultaHce, DiagnosticoHce } from './hce.models';

@Component({
  selector: 'app-medico-consulta',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './medico-consulta.component.html',
  styleUrl: './hce.css'
})
export class MedicoConsultaComponent implements OnInit {
  consulta: ConsultaHce = this.vacia();
  sugerencias: Cie10[] = [];
  adjuntos: AdjuntoHce[] = [];
  tipoAdjunto = 'ESTUDIO';
  enmiendaTexto = '';
  enmiendaMotivo = '';
  busqueda = '';
  error = '';
  aviso = '';
  cargando = false;
  guardando = false;

  constructor(private hce: HceService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    const turnoId = this.route.snapshot.queryParamMap.get('turnoId');
    const pacienteId = this.route.snapshot.queryParamMap.get('pacienteId');
    if (id) {
      this.cargar(Number(id));
    } else if (turnoId) {
      this.desdeTurno(Number(turnoId));
    } else if (pacienteId) {
      this.consulta.pacienteId = Number(pacienteId);
    }
  }

  get cerrada(): boolean {
    return this.consulta.estado === 'CERRADA';
  }

  get imc(): string {
    const peso = Number(this.consulta.pesoKg);
    const talla = Number(this.consulta.tallaCm);
    if (!peso || !talla || talla <= 0) return '—';
    const metros = talla / 100;
    return (peso / (metros * metros)).toFixed(2);
  }

  desdeTurno(turnoId: number): void {
    this.cargando = true;
    this.hce.contextoTurno(turnoId).subscribe({
      next: res => {
        const ctx = res.data;
        if (ctx.consultaId) {
          this.router.navigate(['/medico-consulta', ctx.consultaId]);
          return;
        }
        this.consulta.turnoId = ctx.turnoId;
        this.consulta.pacienteId = ctx.pacienteId;
        this.consulta.pacienteNombre = ctx.pacienteNombre;
        this.consulta.pacienteApellido = ctx.pacienteApellido;
        this.consulta.dni = ctx.dni;
        this.consulta.centroId = ctx.centroId;
        this.consulta.centroNombre = ctx.centroNombre;
        this.consulta.especialidadId = ctx.especialidadId;
        this.consulta.especialidadNombre = ctx.especialidadNombre;
        this.cargando = false;
      },
      error: err => this.fallo(err)
    });
  }

  cargar(id: number): void {
    this.cargando = true;
    this.hce.obtenerConsulta(id).subscribe({
      next: res => {
        this.consulta = this.normalizar(res.data);
        this.cargando = false;
        if (this.consulta.pacienteId) {
          this.hce.adjuntos(this.consulta.pacienteId).subscribe(adj => {
            this.adjuntos = (adj.data || []).filter(item => item.consultaId === this.consulta.id);
          });
        }
      },
      error: err => this.fallo(err)
    });
  }

  buscarCie(): void {
    if (!this.busqueda || this.busqueda.trim().length < 2) {
      this.sugerencias = [];
      return;
    }
    this.hce.cie10(this.busqueda.trim()).subscribe({
      next: res => { this.sugerencias = res.data || []; },
      error: () => { this.sugerencias = []; }
    });
  }

  agregarDiagnostico(codigo: Cie10): void {
    const ya = this.consulta.diagnosticos.some(item => item.code === codigo.code);
    if (!ya) {
      const diagnostico: DiagnosticoHce = {
        systemUri: codigo.systemUri,
        code: codigo.code,
        display: codigo.display,
        tipo: 'PRESUNTIVO'
      };
      this.consulta.diagnosticos.push(diagnostico);
    }
    this.sugerencias = [];
    this.busqueda = '';
  }

  quitarDiagnostico(index: number): void {
    if (this.cerrada) return;
    this.consulta.diagnosticos.splice(index, 1);
  }

  guardar(): void {
    this.guardando = true;
    this.error = '';
    const request = this.consulta.id
      ? this.hce.actualizarConsulta(this.consulta.id, this.consulta)
      : this.hce.crearConsulta(this.consulta);
    request.subscribe({
      next: res => {
        this.consulta = this.normalizar(res.data);
        this.guardando = false;
        this.aviso = this.consulta.estado === 'CERRADA' ? 'Consulta cerrada' : 'Borrador guardado';
        if (res.data.id) {
          this.router.navigate(['/medico-consulta', res.data.id], { replaceUrl: true });
        }
      },
      error: err => this.fallo(err)
    });
  }

  cerrar(): void {
    if (!this.consulta.id) {
      this.error = 'Guardá el borrador antes de cerrarlo';
      return;
    }
    this.guardando = true;
    this.hce.actualizarConsulta(this.consulta.id, this.consulta).subscribe({
      next: () => {
        this.hce.cerrarConsulta(this.consulta.id as number).subscribe({
          next: res => {
            this.consulta = this.normalizar(res.data);
            this.guardando = false;
            this.aviso = 'Consulta cerrada. A partir de ahora las correcciones son enmiendas.';
          },
          error: err => this.fallo(err)
        });
      },
      error: err => this.fallo(err)
    });
  }

  agregarEnmienda(): void {
    if (!this.consulta.id) return;
    this.guardando = true;
    this.hce.enmendar(this.consulta.id, this.enmiendaTexto, this.enmiendaMotivo).subscribe({
      next: () => {
        this.enmiendaTexto = '';
        this.enmiendaMotivo = '';
        this.cargar(this.consulta.id as number);
        this.aviso = 'Enmienda registrada';
        this.guardando = false;
      },
      error: err => this.fallo(err)
    });
  }

  onArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files && input.files.length ? input.files[0] : null;
    if (!archivo || !this.consulta.id) return;
    this.hce.subirAdjuntoConsulta(this.consulta.id, archivo, this.tipoAdjunto).subscribe({
      next: () => {
        this.aviso = 'Archivo guardado';
        this.cargar(this.consulta.id as number);
      },
      error: err => this.fallo(err)
    });
  }

  pdf(): void {
    if (!this.consulta.id) return;
    this.hce.pdfConsulta(this.consulta.id).subscribe(blob => this.bajar(blob, `consulta-${this.consulta.id}.pdf`));
  }

  private bajar(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = nombre;
    link.click();
    URL.revokeObjectURL(url);
  }

  private normalizar(consulta: ConsultaHce): ConsultaHce {
    consulta.diagnosticos = consulta.diagnosticos || [];
    consulta.enmiendas = consulta.enmiendas || [];
    return consulta;
  }

  private vacia(): ConsultaHce {
    return { diagnosticos: [], enmiendas: [], estado: 'BORRADOR' };
  }

  private fallo(err: HttpErrorResponse): void {
    this.cargando = false;
    this.guardando = false;
    this.error = err.error?.status_text || 'No se pudo completar la operación';
  }
}
