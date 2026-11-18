import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { HceService } from './hce.service';
import { AdjuntoHce, ConsultaHce, PerfilClinico, TimelineItem } from './hce.models';
import { MENSAJE_SIN_FICHA_PACIENTE } from '../config/menu.config';

@Component({
  selector: 'app-paciente-hce',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './paciente-hce.component.html',
  styleUrls: ['./hce.css', '../pacientes/paciente-portal.css']
})
export class PacienteHceComponent implements OnInit {
  /** completa conserva la pantalla anterior. Mi salud pide una sección. */
  @Input() seccion: 'completa' | 'timeline' | 'perfil' | 'pdf' = 'completa';
  perfil: PerfilClinico | null = null;
  timeline: TimelineItem[] = [];
  adjuntos: AdjuntoHce[] = [];
  consulta: ConsultaHce | null = null;
  filtrosTipo = '';
  cargando = false;
  sinFicha = false;
  readonly mensajeSinFicha = MENSAJE_SIN_FICHA_PACIENTE;
  error = '';
  aviso = '';

  constructor(
    private hce: HceService,
    private cdr: ChangeDetectorRef,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.sinFicha = false;
    this.error = '';
    this.hce.miHistoria().pipe(finalize(() => {
      this.cargando = false;
      this.cdr.detectChanges();
    })).subscribe({
      next: res => {
        this.perfil = res.data.perfil;
        this.timeline = res.data.timeline || [];
        this.adjuntos = res.data.adjuntos || [];
      },
      error: err => this.fallo(err)
    });
  }

  filtrar(): void {
    if (!this.perfil) return;
    this.hce.timeline(this.perfil.pacienteId, { tipo: this.filtrosTipo || undefined }).subscribe({
      next: res => { this.timeline = res.data || []; },
      error: err => this.fallo(err)
    });
  }

  muestra(parte: 'timeline' | 'perfil'): boolean {
    return this.seccion === 'completa' || this.seccion === parte;
  }

  etiqueta(tipo: string): string {
    const etiquetas: Record<string, string> = {
      CONSULTA: 'Consulta',
      ENMIENDA: 'Enmienda',
      ADJUNTO: 'Adjunto',
      RECETA: 'Receta',
      ORDEN: 'Orden'
    };
    return etiquetas[tipo] || tipo;
  }

  /**
   * Cada consulta junta su evolución (el resumen) con la receta u orden
   * que salió de esa misma consulta.
   */
  get eventosTimeline(): { principal: TimelineItem; extra: TimelineItem[] }[] {
    const items = this.timeline || [];
    const consultas = items.filter(item => item.tipo === 'CONSULTA');
    if (!consultas.length) {
      return items.map(item => ({ principal: item, extra: [] }));
    }
    const ids = new Set(consultas.map(item => item.consultaId));
    const grupos = consultas.map(consulta => ({
      principal: consulta,
      extra: items.filter(item => item.tipo !== 'CONSULTA' && item.consultaId === consulta.consultaId)
    }));
    const sueltos = items
      .filter(item => item.tipo !== 'CONSULTA' && !ids.has(item.consultaId))
      .map(item => ({ principal: item, extra: [] as TimelineItem[] }));
    return [...grupos, ...sueltos];
  }

  get alergiasTexto(): string {
    return (this.perfil?.alergias || []).filter(item => item.activa).map(item => item.sustancia).join(', ') || '—';
  }

  get medicacionTexto(): string {
    return (this.perfil?.medicaciones || []).filter(item => item.activa).map(item => item.farmaco).join(', ') || '—';
  }

  irAlTurno(item: TimelineItem, event: Event): void {
    event.stopPropagation();
    if (!item.consultaId) return;
    this.hce.obtenerConsulta(item.consultaId).subscribe({
      next: res => {
        const turnoId = res.data?.turnoId;
        if (!turnoId) {
          this.aviso = 'Esta consulta no tiene un turno vinculado';
          return;
        }
        this.router.navigate(['/paciente-turnos'], { queryParams: { tab: 'pasados', turno: turnoId } });
      },
      error: err => this.fallo(err)
    });
  }

  ver(item: TimelineItem): void {
    if (!item.consultaId) return;
    this.hce.obtenerConsulta(item.consultaId).subscribe({
      next: res => {
        res.data.diagnosticos = res.data.diagnosticos || [];
        res.data.enmiendas = res.data.enmiendas || [];
        this.consulta = res.data;
      },
      error: err => this.fallo(err)
    });
  }

  guardarConsentimiento(): void {
    if (!this.perfil) return;
    this.hce.consentimiento(this.perfil.comparteEntreCentros).subscribe({
      next: res => {
        this.perfil = res.data;
        this.aviso = 'Preferencia de privacidad actualizada';
      },
      error: err => this.fallo(err)
    });
  }

  pdfResumen(): void {
    this.hce.pdfPropio().subscribe({
      next: blob => this.bajar(blob, 'mi-historia-clinica.pdf'),
      error: err => this.fallo(err)
    });
  }

  pdfConsulta(): void {
    if (!this.consulta?.id) return;
    this.hce.pdfConsulta(this.consulta.id).subscribe({
      next: blob => this.bajar(blob, `consulta-${this.consulta?.id}.pdf`),
      error: err => this.fallo(err)
    });
  }

  descargar(adjunto: AdjuntoHce): void {
    this.hce.descargarAdjunto(adjunto.id).subscribe({
      next: blob => this.bajar(blob, adjunto.nombreOriginal),
      error: err => this.fallo(err)
    });
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
    const texto = err.error?.status_text || '';
    if (err.status === 404 && /ficha de paciente/i.test(texto)) {
      this.sinFicha = true;
      this.error = '';
      return;
    }
    this.error = texto || 'No se pudo leer la historia clínica';
  }
}
