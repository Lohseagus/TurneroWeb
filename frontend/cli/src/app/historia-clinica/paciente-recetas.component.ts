import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { RecetaService } from './receta.service';
import { OrdenEstudio, Receta } from './receta.models';
import { MENSAJE_SIN_FICHA_PACIENTE } from '../config/menu.config';

@Component({
  selector: 'app-paciente-recetas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './paciente-recetas.component.html',
  styleUrl: './hce.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PacienteRecetasComponent implements OnInit {
  recetas: Receta[] = [];
  ordenes: OrdenEstudio[] = [];
  error = '';
  sinFicha = false;
  readonly mensajeSinFicha = MENSAJE_SIN_FICHA_PACIENTE;
  cargando = true;

  constructor(private api: RecetaService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    let pendientes = 2;
    const listo = () => {
      pendientes -= 1;
      if (pendientes <= 0) {
        this.cargando = false;
        this.cdr.detectChanges();
      }
    };
    this.api.mias().pipe(finalize(listo)).subscribe({
      next: res => {
        this.recetas = res.data || [];
        this.cdr.markForCheck();
      },
      error: err => this.fallo(err)
    });
    this.api.misOrdenes().pipe(finalize(listo)).subscribe({
      next: res => {
        this.ordenes = res.data || [];
        this.cdr.markForCheck();
      },
      error: err => this.fallo(err)
    });
  }

  pdfReceta(receta: Receta): void {
    if (!receta.id) return;
    this.api.pdf(receta.id).subscribe(blob => this.bajar(blob, `receta-${receta.id}.pdf`));
  }

  pdfOrden(orden: OrdenEstudio): void {
    if (!orden.id) return;
    this.api.pdfOrden(orden.id).subscribe(blob => this.bajar(blob, `orden-${orden.id}.pdf`));
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
    } else if (!this.sinFicha) {
      this.error = texto || 'No se pudieron cargar las recetas';
    }
    this.cdr.markForCheck();
  }
}
