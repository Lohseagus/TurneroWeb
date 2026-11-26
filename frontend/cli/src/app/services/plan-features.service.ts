import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { DataPackage } from '../data.package';
import { Role } from '../inicio-sesion/auth.service';
import { FeaturesPlan } from '../saas/saas.models';
import { UserContextService } from './user-context.service';

/** Roles a los que el backend les aplica la compuerta de funciones por plan. */
const ROLES_CON_COMPUERTA: string[] = [Role.ADMINISTRADOR, Role.OPERADOR];

/**
 * Funciones del plan del centro de quien está logueado.
 * null = no se oculta nada (bandera apagada, SUPERADMIN, médico, paciente o error al leer).
 */
@Injectable({ providedIn: 'root' })
export class PlanFeaturesService {

  private readonly http = inject(HttpClient, { optional: true });
  private readonly userContext = inject(UserContextService);
  private readonly base = environment.production ? `${environment.apiUrl}/saas` : 'rest/saas';
  private readonly estadoSubject = new BehaviorSubject<FeaturesPlan | null>(null);

  readonly estado$: Observable<FeaturesPlan | null> = this.estadoSubject.asObservable();

  constructor() {
    this.userContext.userContext$.pipe(
      map(context => context?.isAuthenticated && ROLES_CON_COMPUERTA.includes(String(context.primaryRole).toUpperCase())
        ? `${context.email}|${context.primaryRole}|${context.centroAtencionId ?? ''}`
        : null),
      distinctUntilChanged(),
      switchMap(clave => clave ? this.leer() : of(null))
    ).subscribe(estado => this.estadoSubject.next(estado));
  }

  /** Volver a leer, por ejemplo después de cambiar de plan. */
  refrescar(): void {
    this.leer().subscribe(estado => this.estadoSubject.next(estado));
  }

  private leer(): Observable<FeaturesPlan | null> {
    if (!this.http) {
      return of(null);
    }
    return this.http.get<DataPackage<FeaturesPlan>>(`${this.base}/features`).pipe(
      map(respuesta => respuesta?.data ?? null),
      catchError(() => of(null))
    );
  }
}
