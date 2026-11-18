import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Role } from '../inicio-sesion/auth.service';
import { UserContextService } from '../services/user-context.service';
import { MENSAJE_SIN_FICHA_PACIENTE } from '../config/menu.config';
import { PacienteSesionService } from './paciente-sesion.service';

type EstadoPortal = 'cargando' | 'portal' | 'sin-ficha' | 'error';

/**
 * Envoltorio de las rutas de paciente. El rol heredado alcanza para entrar,
 * pero el staff sin ficha no monta las pantallas: así no disparan 403
 * ni se quedan en "Cargando…". Un PACIENTE primario sigue directo.
 */
@Component({
  selector: 'app-paciente-portal',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (estado === 'portal') {
      <router-outlet />
    } @else if (estado === 'sin-ficha') {
      <div class="ct-page">
        <section class="ct-section">
          <div class="ct-empty">
            <span class="material-symbols-outlined ct-empty__icon" aria-hidden="true">personal_injury</span>
            <h1 class="ct-empty__title">{{ mensaje }}</h1>
            <p class="ct-empty__text">
              Usá «Cambiar a paciente», arriba de Cerrar sesión, para crear tu ficha con los datos de tu cuenta.
            </p>
          </div>
        </section>
      </div>
    } @else if (estado === 'error') {
      <div class="ct-page">
        <section class="ct-section">
          <div class="ct-empty">
            <span class="material-symbols-outlined ct-empty__icon ct-empty__icon--danger" aria-hidden="true">error</span>
            <h1 class="ct-empty__title">No se pudo verificar tu ficha</h1>
            <p class="ct-empty__text">{{ detalleError }}</p>
            <button type="button" class="btn btn-primary" (click)="reintentar()">
              <span class="material-symbols-outlined">refresh</span>
              Reintentar
            </button>
          </div>
        </section>
      </div>
    } @else {
      <div class="ct-loading" role="status" aria-live="polite">
        <span class="material-symbols-outlined ct-spin" aria-hidden="true">sync</span>
        <p>Cargando…</p>
      </div>
    }
  `
})
export class PacientePortalComponent implements OnInit, OnDestroy {
  estado: EstadoPortal = 'cargando';
  readonly mensaje = MENSAJE_SIN_FICHA_PACIENTE;
  detalleError = 'Reintentá en un momento.';
  private creadaSub?: Subscription;

  constructor(
    private usuarios: UserContextService,
    private sesion: PacienteSesionService
  ) {}

  ngOnInit(): void {
    this.creadaSub = this.sesion.creada$.subscribe(() => {
      this.estado = 'portal';
    });
    if (this.usuarios.getCurrentContext().primaryRole === Role.PACIENTE) {
      this.estado = 'portal';
      return;
    }
    this.consultar();
  }

  ngOnDestroy(): void {
    this.creadaSub?.unsubscribe();
  }

  reintentar(): void {
    this.sesion.invalidar();
    this.estado = 'cargando';
    this.consultar();
  }

  private consultar(): void {
    this.sesion.resolver().subscribe({
      next: ficha => {
        this.estado = ficha ? 'portal' : 'sin-ficha';
      },
      error: (error: HttpErrorResponse) => {
        this.estado = 'error';
        const texto = error?.error?.status_text;
        this.detalleError = typeof texto === 'string' && texto.trim()
          ? texto
          : 'No hay conexión con el servidor. Reintentá.';
      }
    });
  }
}
