import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Observable, of, Subject, throwError } from 'rxjs';
import { catchError, map, mergeMap, shareReplay, tap, timeout } from 'rxjs/operators';
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
  /** Sube al invalidar o al crear la ficha, para ignorar un GET que llegó tarde. */
  private generacion = 0;
  private readonly creadaSubject = new Subject<Paciente>();
  /** Avisa al portal si la ficha se creó con la pantalla de vacío ya abierta. */
  readonly creada$ = this.creadaSubject.asObservable();

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

  /**
   * Id de la ficha de la cuenta. No usa el pacienteId guardado en el login:
   * un médico que acaba de crear su ficha, o que entró a la vista de paciente,
   * no lo tiene en el storage. null si no hay ficha. Otros errores se propagan.
   */
  id(): Observable<number | null> {
    return this.resolver().pipe(map(ficha => {
      const id = ficha?.id;
      return typeof id === 'number' && id > 0 ? id : null;
    }));
  }

  /**
   * Crea la ficha de la cuenta autenticada (POST /api/pacientes/me).
   * El email lo pone el backend. No manda el de otra persona.
   * Reemplaza la caché: un 404 anterior no puede seguir diciendo que no hay ficha.
   */
  crear(datos: Partial<Paciente> = {}): Observable<Paciente> {
    return this.http.post<DataPackage<Paciente>>(this.url, datos).pipe(
      map(respuesta => {
        const ficha = respuesta?.data;
        if (!ficha?.id) {
          throw new Error('La ficha no llegó completa');
        }
        return ficha;
      }),
      tap(ficha => {
        this.generacion++;
        const email = (this.usuarios.getCurrentContext().email || '').trim().toLowerCase();
        this.cacheEmail = email;
        this.cache = of(ficha);
        this.recordar(ficha);
        this.creadaSubject.next(ficha);
      })
    );
  }

  invalidar(): void {
    this.generacion++;
    this.cache = null;
    this.cacheEmail = null;
  }

  private pedir(): Observable<Paciente | null> {
    const generacion = this.generacion;
    return this.http.get<DataPackage<Paciente>>(this.url).pipe(
      timeout(15000),
      map(respuesta => respuesta?.data ?? null),
      mergeMap(ficha => this.vigente(generacion, ficha)),
      catchError(error => {
        if (generacion !== this.generacion) {
          return EMPTY;
        }
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

  /** Un GET empezado antes de crear la ficha o de invalidar no pisa el resultado nuevo. */
  private vigente(generacion: number, ficha: Paciente | null): Observable<Paciente | null> {
    if (generacion !== this.generacion) {
      return EMPTY;
    }
    this.recordar(ficha);
    return of(ficha);
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
