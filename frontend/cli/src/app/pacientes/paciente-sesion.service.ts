import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map, shareReplay, tap, timeout } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { DataPackage } from '../data.package';
import { Paciente } from './paciente';
import { UserContextService } from '../services/user-context.service';

/**
 * Ficha de la cuenta autenticada (GET /api/pacientes/me).
 * Un 404 es "no hay ficha", no un error de permiso.
 */
@Injectable({ providedIn: 'root' })
export class PacienteSesionService {
  private readonly url = environment.production
    ? `${environment.apiUrl}/pacientes/me`
    : 'rest/pacientes/me';

  private cache: Observable<Paciente | null> | null = null;
  private cacheEmail: string | null = null;

  constructor(private http: HttpClient, private usuarios: UserContextService) {
    this.usuarios.userContext$.subscribe(context => {
      if (!context.isAuthenticated) {
        this.invalidar();
      }
    });
  }

  /** null cuando la cuenta no tiene ficha. Otros errores se propagan. */
  resolver(): Observable<Paciente | null> {
    const email = (this.usuarios.getCurrentContext().email || '').trim().toLowerCase();
    if (!this.cache || this.cacheEmail !== email) {
      this.cacheEmail = email;
      this.cache = this.pedir();
    }
    return this.cache;
  }

  tieneFicha(): Observable<boolean> {
    return this.resolver().pipe(map(ficha => ficha != null));
  }

  invalidar(): void {
    this.cache = null;
    this.cacheEmail = null;
  }

  private pedir(): Observable<Paciente | null> {
    return this.http.get<DataPackage<Paciente>>(this.url).pipe(
      timeout(15000),
      map(respuesta => respuesta?.data ?? null),
      tap(ficha => this.recordar(ficha)),
      catchError(error => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.recordar(null);
          return of(null);
        }
        this.cache = null;
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
  }

  private recordar(ficha: Paciente | null): void {
    if (typeof localStorage === 'undefined' || !this.usuarios.getCurrentContext().isAuthenticated) {
      return;
    }
    if (ficha?.id) {
      localStorage.setItem('pacienteId', String(ficha.id));
      return;
    }
    localStorage.removeItem('pacienteId');
  }
}
