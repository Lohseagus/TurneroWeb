import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CentroAtencionService } from '../centrosAtencion/centroAtencion.service';
import { StaffMedicoService } from '../staffMedicos/staffMedico.service';
import { MedicoSesionService } from '../medicos/medico-sesion.service';
import {
  AusenciaListada,
  AusenciaResultado,
  AusenciaService,
  AusenciaSolicitud,
  TurnoAfectado
} from './ausencia.service';

interface OpcionStaff {
  id: number;
  centroId: number | null;
  etiqueta: string;
}

@Component({
  selector: 'app-ausencias-medico',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ausencias-medico.component.html',
  styleUrl: './ausencias-medico.component.css',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AusenciasMedicoComponent implements OnInit {
  @Input() modo: 'medico' | 'admin' = 'medico';

  centros: { id: number; nombre: string }[] = [];
  centroId: number | null = null;
  staff: OpcionStaff[] = [];
  staffMedicoId: number | null = null;
  fechaInicio = '';
  fechaFin = '';
  horaInicio = '';
  horaFin = '';
  motivo = '';
  urgente = false;
  diasAnticipacion = 7;
  fechaMinima = '';

  ausencias: AusenciaListada[] = [];
  vistaPrevia: AusenciaResultado | null = null;
  cargando = false;
  guardando = false;
  mensaje = '';
  error = '';

  constructor(
    private ausenciaService: AusenciaService,
    private medicoSesion: MedicoSesionService,
    private staffService: StaffMedicoService,
    private centroService: CentroAtencionService
  ) {}

  ngOnInit() {
    if (this.modo === 'admin') {
      this.centroService.all().subscribe({
        next: (respuesta) => {
          this.centros = respuesta.data || [];
          if (this.centros.length === 1) {
            this.centroId = this.centros[0].id;
            this.alCambiarCentro();
          } else {
            this.cargarLista();
            this.cargarAnticipacion(null);
          }
        },
        error: () => this.error = 'No se pudieron cargar los centros.'
      });
      return;
    }
    this.medicoSesion.resolver().subscribe({
      next: (sesion) => {
        this.staff = (sesion.staff || []).map(item => ({
          id: item.id,
          centroId: item.centroId,
          etiqueta: `${item.especialidadNombre || 'Especialidad'} · ${item.centroNombre || 'Centro'}`
        }));
        if (this.staff.length === 1) {
          this.staffMedicoId = this.staff[0].id;
        }
        this.cargarLista();
        this.cargarAnticipacion(this.centroSeleccionado());
      },
      error: () => this.error = 'No se pudo identificar tu agenda.'
    });
  }

  get requiereUrgencia(): boolean {
    return this.modo === 'admin' && !!this.fechaInicio && !!this.fechaMinima && this.fechaInicio < this.fechaMinima;
  }

  get minimoDelMedico(): string | null {
    return this.modo === 'medico' ? this.fechaMinima || null : null;
  }

  alCambiarCentro() {
    this.staff = [];
    this.staffMedicoId = null;
    this.vistaPrevia = null;
    if (!this.centroId) {
      this.cargarLista();
      this.cargarAnticipacion(null);
      return;
    }
    this.staffService.getByCentroAtencion(this.centroId).subscribe({
      next: (respuesta) => {
        this.staff = (respuesta.data || []).map(item => ({
          id: item.id,
          centroId: this.centroId,
          etiqueta: this.etiquetaStaff(item)
        }));
        this.cargarLista();
        this.cargarAnticipacion(this.centroId);
      },
      error: () => this.error = 'No se pudieron cargar los profesionales del centro.'
    });
  }

  alEditar() {
    this.vistaPrevia = null;
    this.mensaje = '';
    this.error = '';
  }

  alElegirStaff() {
    this.alEditar();
    this.cargarLista();
    this.cargarAnticipacion(this.centroSeleccionado());
  }

  previsualizar() {
    const solicitud = this.solicitud();
    if (!solicitud) {
      return;
    }
    this.guardando = true;
    this.error = '';
    this.mensaje = '';
    this.ausenciaService.previsualizar(solicitud).subscribe({
      next: (respuesta) => {
        this.guardando = false;
        if (respuesta.status_code !== 200) {
          this.error = respuesta.status_text || 'No se pudo ver los turnos afectados.';
          return;
        }
        this.vistaPrevia = respuesta.data;
      },
      error: (err) => {
        this.guardando = false;
        this.error = this.textoError(err, 'No se pudo ver los turnos afectados.');
      }
    });
  }

  confirmar() {
    const solicitud = this.solicitud();
    if (!solicitud || !this.vistaPrevia) {
      this.error = 'Primero revisá los turnos afectados.';
      return;
    }
    this.guardando = true;
    this.error = '';
    this.ausenciaService.confirmar(solicitud).subscribe({
      next: (respuesta) => {
        this.guardando = false;
        if (respuesta.status_code !== 200) {
          this.error = respuesta.status_text || 'No se pudo guardar la ausencia.';
          return;
        }
        this.mensaje = respuesta.data?.mensaje || respuesta.status_text || 'Ausencia guardada.';
        this.vistaPrevia = respuesta.data;
        this.motivo = '';
        this.urgente = false;
        this.cargarLista();
      },
      error: (err) => {
        this.guardando = false;
        this.error = this.textoError(err, 'No se pudo guardar la ausencia.');
      }
    });
  }

  eliminar(ausencia: AusenciaListada) {
    if (!confirm('¿Eliminás esta ausencia? Los turnos que ya se cancelaron no se vuelven a abrir.')) {
      return;
    }
    const staffId = ausencia.staffMedicoId || this.staffMedicoId;
    if (!staffId) {
      this.error = 'No se pudo identificar al profesional de la ausencia.';
      return;
    }
    this.ausenciaService.eliminar(ausencia.id, staffId, this.centroSeleccionado()).subscribe({
      next: (respuesta) => {
        if (respuesta.status_code !== 200) {
          this.error = respuesta.status_text || 'No se pudo eliminar la ausencia.';
          return;
        }
        this.mensaje = 'Ausencia eliminada.';
        this.cargarLista();
      },
      error: (err) => this.error = this.textoError(err, 'No se pudo eliminar la ausencia.')
    });
  }

  nombrePaciente(turno: TurnoAfectado): string {
    return `${turno.pacienteNombre || ''} ${turno.pacienteApellido || ''}`.trim() || 'Paciente';
  }

  horario(ausencia: AusenciaListada): string {
    if (!ausencia.horaInicio || !ausencia.horaFin) {
      return 'Día completo';
    }
    return `${ausencia.horaInicio.slice(0, 5)} a ${ausencia.horaFin.slice(0, 5)}`;
  }

  rango(ausencia: AusenciaListada): string {
    return ausencia.fechaFin && ausencia.fechaFin !== ausencia.fecha
      ? `${ausencia.fecha} al ${ausencia.fechaFin}`
      : ausencia.fecha;
  }

  private cargarLista() {
    this.cargando = true;
    const staffId = this.staffMedicoId;
    const centroId = this.modo === 'admin' && !staffId ? this.centroId : null;
    this.ausenciaService.listar(staffId, centroId).subscribe({
      next: (respuesta) => {
        this.cargando = false;
        this.ausencias = respuesta.status_code === 200 ? (respuesta.data || []) : [];
        if (respuesta.status_code !== 200) {
          this.error = respuesta.status_text || 'No se pudieron cargar las ausencias.';
        }
      },
      error: (err) => {
        this.cargando = false;
        this.error = this.textoError(err, 'No se pudieron cargar las ausencias.');
      }
    });
  }

  private solicitud(): AusenciaSolicitud | null {
    if (!this.staffMedicoId) {
      this.error = 'Elegí el profesional.';
      return null;
    }
    if (!this.fechaInicio || !this.fechaFin) {
      this.error = 'Completá desde y hasta.';
      return null;
    }
    if (this.fechaFin < this.fechaInicio) {
      this.error = 'La fecha de fin no puede ser anterior a la de inicio.';
      return null;
    }
    if (this.modo === 'medico' && this.fechaMinima && this.fechaInicio < this.fechaMinima) {
      this.error = `Las ausencias se cargan con al menos ${this.diasAnticipacion} días de anticipación. Para una urgencia, pedile a la administración del centro que la cargue.`;
      return null;
    }
    if (this.requiereUrgencia && !this.urgente) {
      this.error = `Las ausencias se cargan con al menos ${this.diasAnticipacion} días de anticipación. Marcá Urgencia y escribí el motivo.`;
      return null;
    }
    if (this.requiereUrgencia && this.motivo.trim().length < 5) {
      this.error = 'La urgencia necesita un motivo de al menos 5 caracteres.';
      return null;
    }
    if ((this.horaInicio && !this.horaFin) || (!this.horaInicio && this.horaFin)) {
      this.error = 'Si indicás un horario, completá la hora de inicio y la de fin.';
      return null;
    }
    this.error = '';
    return {
      staffMedicoId: this.staffMedicoId,
      centroAtencionId: this.centroSeleccionado(),
      fechaInicio: this.fechaInicio,
      fechaFin: this.fechaFin,
      horaInicio: this.horaInicio || null,
      horaFin: this.horaFin || null,
      motivo: this.motivo.trim() || null,
      urgente: this.requiereUrgencia ? this.urgente : false
    };
  }

  private cargarAnticipacion(centroId: number | null) {
    this.ausenciaService.anticipacion(centroId).subscribe({
      next: (respuesta) => {
        if (respuesta.status_code === 200 && respuesta.data) {
          this.diasAnticipacion = respuesta.data.dias;
          this.fechaMinima = respuesta.data.fechaMinima;
        }
      },
      error: () => {
        this.error = 'No se pudo consultar la anticipación mínima.';
      }
    });
  }

  private centroSeleccionado(): number | null {
    if (this.modo === 'admin') {
      return this.centroId;
    }
    return this.staff.find(item => item.id === this.staffMedicoId)?.centroId ?? null;
  }

  private etiquetaStaff(item: {
    medico?: { nombre?: string; apellido?: string };
    especialidad?: { nombre?: string };
  }): string {
    const medico = `${item.medico?.apellido || ''} ${item.medico?.nombre || ''}`.trim();
    const especialidad = item.especialidad?.nombre || '';
    return [medico, especialidad].filter(Boolean).join(' · ') || 'Profesional';
  }

  private textoError(err: { error?: { status_text?: string } }, porDefecto: string): string {
    return err?.error?.status_text || porDefecto;
  }
}
