import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TimeoutError, timeout } from 'rxjs';
import { HceService } from './hce.service';
import {
  GRUPOS_SANGUINEOS,
  PacienteResumenHce,
  PerfilClinico,
  TimelineItem
} from './hce.models';

@Component({
  selector: 'app-medico-hce',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './medico-hce.component.html',
  styleUrl: './hce.css'
})
export class MedicoHceComponent implements OnInit {
  pacientes: PacienteResumenHce[] = [];
  perfil: PerfilClinico | null = null;
  timeline: TimelineItem[] = [];
  pacienteId: number | null = null;
  tab: 'timeline' | 'perfil' = 'timeline';
  cargando = false;
  guardando = false;
  error = '';
  aviso = '';
  grupos = GRUPOS_SANGUINEOS;
  filtros = { tipo: '', centroId: null as number | null, medicoId: null as number | null, especialidadId: null as number | null };

  constructor(
    private hce: HceService,
    private route: ActivatedRoute,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const raw = params.get('pacienteId');
      this.pacienteId = raw ? Number(raw) : null;
      this.error = '';
      if (this.pacienteId) {
        this.cargarPaciente();
      } else {
        this.cargarLista();
      }
    });
  }

  cargarLista(): void {
    this.cargando = true;
    this.error = '';
    this.hce.misPacientes().pipe(timeout(20000)).subscribe({
      next: res => {
        this.pacientes = res.data || [];
        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: err => this.fallo(err)
    });
  }

  reintentar(): void {
    if (this.pacienteId) {
      this.cargarPaciente();
    } else {
      this.cargarLista();
    }
  }

  cargarPaciente(): void {
    if (!this.pacienteId) return;
    this.cargando = true;
    this.hce.perfil(this.pacienteId).subscribe({
      next: res => {
        this.perfil = this.normalizarPerfil(res.data);
        this.cargando = false;
        this.cdr.detectChanges();
        this.buscarTimeline();
      },
      error: err => this.fallo(err)
    });
  }

  buscarTimeline(): void {
    if (!this.pacienteId) return;
    this.hce.timeline(this.pacienteId, {
      tipo: this.filtros.tipo || undefined,
      centroId: this.filtros.centroId,
      medicoId: this.filtros.medicoId,
      especialidadId: this.filtros.especialidadId
    }).subscribe({
      next: res => { this.timeline = res.data || []; },
      error: err => this.fallo(err)
    });
  }

  guardarPerfil(): void {
    if (!this.pacienteId || !this.perfil) return;
    this.guardando = true;
    this.error = '';
    this.hce.guardarPerfil(this.pacienteId, this.perfil).subscribe({
      next: res => {
        this.perfil = this.normalizarPerfil(res.data);
        this.guardando = false;
        this.aviso = 'Perfil clínico guardado';
      },
      error: err => this.fallo(err)
    });
  }

  agregarAlergia(): void {
    this.perfil?.alergias.push({ sustancia: '', activa: true, criticidad: 'NO_EVALUADA' });
  }

  agregarMedicacion(): void {
    this.perfil?.medicaciones.push({ farmaco: '', activa: true });
  }

  agregarVacuna(): void {
    this.perfil?.vacunas.push({ nombre: '' });
  }

  desactivarAlergia(index: number): void {
    if (!this.perfil) return;
    const item = this.perfil.alergias[index];
    if (item.id) {
      item.activa = false;
    } else {
      this.perfil.alergias.splice(index, 1);
    }
  }

  desactivarMedicacion(index: number): void {
    if (!this.perfil) return;
    const item = this.perfil.medicaciones[index];
    if (item.id) {
      item.activa = false;
    } else {
      this.perfil.medicaciones.splice(index, 1);
    }
  }

  abrir(item: TimelineItem): void {
    if (item.consultaId) {
      this.router.navigate(['/medico-consulta', item.consultaId]);
    }
  }

  nuevaConsulta(): void {
    if (!this.pacienteId || !this.perfil) return;
    this.router.navigate(['/medico-consulta/nueva'], { queryParams: { pacienteId: this.pacienteId } });
  }

  private normalizarPerfil(perfil: PerfilClinico): PerfilClinico {
    perfil.alergias = perfil.alergias || [];
    perfil.medicaciones = perfil.medicaciones || [];
    perfil.vacunas = perfil.vacunas || [];
    perfil.grupoSanguineo = perfil.grupoSanguineo || 'DESCONOCIDO';
    return perfil;
  }

  private fallo(err: unknown): void {
    this.cargando = false;
    this.guardando = false;
    if (err instanceof TimeoutError) {
      this.error = 'La consulta tardó demasiado. Reintentá.';
      return;
    }
    const http = err as HttpErrorResponse;
    const texto = http?.error?.status_text;
    this.error = typeof texto === 'string' && texto.trim()
      ? texto
      : 'No se pudo completar la operación';
    this.cdr.detectChanges();
  }
}
