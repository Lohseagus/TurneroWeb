import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { RecetaService } from './receta.service';
import { VerificacionPublica } from './receta.models';

@Component({
  selector: 'app-verificar-prescripcion',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './verificar-prescripcion.component.html',
  styleUrl: './hce.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VerificarPrescripcionComponent implements OnInit {
  datos: VerificacionPublica | null = null;
  error = '';
  cargando = true;
  orden = false;

  constructor(private route: ActivatedRoute, private api: RecetaService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.orden = this.route.snapshot.data['tipo'] === 'ORDEN';
    const codigo = this.route.snapshot.paramMap.get('codigo') || '';
    const pedido = this.orden ? this.api.verificarOrden(codigo) : this.api.verificar(codigo);
    pedido.subscribe({
      next: res => {
        this.datos = res.data;
        this.cargando = false;
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => {
        this.cargando = false;
        this.error = err.error?.status_text || 'No se pudo verificar el código';
        this.cdr.markForCheck();
      }
    });
  }
}
