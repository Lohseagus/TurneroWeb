import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DataPackage } from '../../../data.package';
import { TurnoService } from '../../../turnos/turno.service';

export interface TurnoCompacto {
  id: number;
  hora: string;
  paciente: string;
  medico: string;
  especialidad: string;
  estado: string;
}

export interface OperadorResumen {
  turnosHoy: number;
  pendientesConfirmar: number;
  listaEspera: number;
  cancelacionesSemana: number;
  proximosHoy: TurnoCompacto[];
}

@Component({
  selector: 'app-centro-resumen-tab',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './centro-resumen-tab.component.html',
  styleUrl: './centro-resumen-tab.component.css'
})
export class CentroResumenTabComponent implements OnInit {
  resumen: OperadorResumen | null = null;
  cargando = true;
  error = '';

  constructor(private turnoService: TurnoService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.turnoService.resumenOperador().subscribe({
      next: (respuesta: DataPackage<OperadorResumen>) => {
        this.cargando = false;
        if (respuesta.status_code === 200 && respuesta.data) {
          this.resumen = {
            turnosHoy: respuesta.data.turnosHoy ?? 0,
            pendientesConfirmar: respuesta.data.pendientesConfirmar ?? 0,
            listaEspera: respuesta.data.listaEspera ?? 0,
            cancelacionesSemana: respuesta.data.cancelacionesSemana ?? 0,
            proximosHoy: respuesta.data.proximosHoy ?? []
          };
          return;
        }
        this.resumen = null;
        this.error = respuesta.status_text || 'No pudimos cargar el resumen del centro.';
      },
      error: () => {
        this.cargando = false;
        this.resumen = null;
        this.error = 'No pudimos cargar el resumen del centro.';
      }
    });
  }

  etiquetaEstado(estado: string): string {
    const etiquetas: Record<string, string> = {
      PROGRAMADO: 'Programado',
      CONFIRMADO: 'Confirmado',
      REAGENDADO: 'Reagendado',
      COMPLETO: 'Completado',
      CANCELADO: 'Cancelado',
      AUSENTE: 'Ausente'
    };
    return etiquetas[estado] || estado;
  }
}
