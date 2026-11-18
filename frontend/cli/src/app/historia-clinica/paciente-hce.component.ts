import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { HceService } from './hce.service';
import { AdjuntoHce, ConsultaHce, PerfilClinico, TimelineItem } from './hce.models';
import { MENSAJE_SIN_FICHA_PACIENTE } from '../config/menu.config';

@Component({
  selector: 'app-paciente-hce',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './paciente-hce.component.html',
  styleUrl: './hce.css'
})
export class PacienteHceComponent implements OnInit {
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

  constructor(private hce: HceService, private cdr: ChangeDetectorRef) {}

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

  get alergiasTexto(): string {
    return (this.perfil?.alergias || []).filter(item => item.activa).map(item => item.sustancia).join(', ') || '—';
  }

  get medicacionTexto(): string {
    return (this.perfil?.medicaciones || []).filter(item => item.activa).map(item => item.farmaco).join(', ') || '—';
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
