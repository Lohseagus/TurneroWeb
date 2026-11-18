import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HistorialService, HistorialTurnoDTO, HistorialFilter } from './historial.service';
import { HistorialTurnoDetalleComponent } from './historial-turno-detalle.component';
import { timeout } from 'rxjs/operators';
import { PacienteSesionService } from '../pacientes/paciente-sesion.service';
import { mensajeDeCarga } from '../pacientes/mensaje-carga';

@Component({
  selector: 'app-historial-turnos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './historial-turnos.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./historial-turnos.component.css']
})
export class HistorialTurnosComponent implements OnInit {
  @Input() embebido = false;
  turnos: HistorialTurnoDTO[] = [];
  isLoading = true;
  errorCarga = '';
  private pacienteId: number | null = null;

  // Paginación
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;

  // Filtros
  filtro: HistorialFilter = {
    fechaDesde: undefined,
    fechaHasta: undefined,
    estado: undefined  // ⚠️ Cambiá de '' a undefined
  };
  constructor(
    private historialService: HistorialService,
    private modalService: NgbModal,
    private sesion: PacienteSesionService
  ) { }

  abrirDetalle(turno: HistorialTurnoDTO) {
    const modalRef = this.modalService.open(HistorialTurnoDetalleComponent, {
      size: 'lg',
      centered: true,
      backdrop: true, // Permite cerrar clickeando fuera
      keyboard: true  // Permite cerrar con ESC
    });
    modalRef.componentInstance.turno = turno;
  }

  ngOnInit() {
    this.resolverYcargar();
  }

  reintentar(): void {
    this.sesion.invalidar();
    this.resolverYcargar();
  }

  private resolverYcargar(): void {
    this.isLoading = true;
    this.errorCarga = '';
    this.sesion.id().subscribe({
      next: (id) => {
        this.pacienteId = id;
        if (!id) {
          this.isLoading = false;
          this.errorCarga = 'No encontramos tu ficha de paciente.';
          return;
        }
        this.cargarHistorial();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorCarga = mensajeDeCarga(error, 'No se pudo cargar el historial. Reintentá.');
      }
    });
  }

  cargarHistorial() {
    const pacienteId = this.pacienteId;
    if (!pacienteId) {
      this.isLoading = false;
      this.errorCarga = 'No encontramos tu ficha de paciente.';
      return;
    }

    console.log('📡 Enviando request con filtros:', {
      pacienteId,
      page: this.currentPage,
      size: this.pageSize,
      filtro: this.filtro
    });

    this.isLoading = true;
    this.errorCarga = '';
    this.historialService.getHistorialTurnosPaginado(
      pacienteId,
      this.currentPage,
      this.pageSize,
      this.filtro
    ).pipe(timeout(20000)).subscribe({
      next: (response) => {
        console.log('✅ Response recibida:', response);
        this.turnos = response.data.content;
        this.totalPages = response.data.totalPages;
        this.totalElements = response.data.totalElements;
        this.currentPage = response.data.currentPage;
        this.isLoading = false;
      },
      error: (error) => {
        console.error('❌ Error al cargar historial:', error);
        this.isLoading = false;
        this.errorCarga = mensajeDeCarga(error, 'No se pudo cargar el historial. Reintentá.');
      }
    });
  }

  aplicarFiltros() {
    console.log('🔍 Filtros aplicados:', this.filtro);
    console.log('   - Estado:', this.filtro.estado, '(tipo:', typeof this.filtro.estado, ')');

    // Limpiar valores vacíos
    if (this.filtro.estado === '' || this.filtro.estado === 'TODOS') {
      this.filtro.estado = undefined;
    }

    this.currentPage = 0;
    this.cargarHistorial();
  }

  cambiarPagina(pagina: number) {
    this.currentPage = pagina;
    this.cargarHistorial();
  }

  getPaginasArray(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  getEstadoClass(estado: string): string {
    return `estado-${estado.toLowerCase()}`;
  }
}