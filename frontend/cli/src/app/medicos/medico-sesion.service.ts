import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, TimeoutError, throwError } from 'rxjs';
import { catchError, map, shareReplay, tap, timeout } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { DataPackage } from '../data.package';
import { Especialidad } from '../especialidades/especialidad';
import { Medico } from './medico';

export interface StaffMedicoSesion {
  id: number;
  centroId: number | null;
  centroNombre: string | null;
  especialidadId: number | null;
  especialidadNombre: string | null;
}

/** Ficha que devuelve GET /api/medicos/me. El id lo decide el backend. */
export interface MedicoSesion {
  id: number;
  nombre: string;
  apellido: string;
  dni: string | null;
  email: string;
  telefono: string | null;
  matricula: string;
  refepsId?: string | null;
  especialidades: Especialidad[];
  staffMedicoId: number | null;
  staff: StaffMedicoSesion[];
}

export class MedicoSinFichaError extends Error {
  static readonly porDefecto =
    'Tu usuario tiene rol de médico, pero no hay una ficha profesional vinculada. Pedile a un administrador del centro que te asocie.';

  constructor(message = MedicoSinFichaError.porDefecto) {
    super(message);
    this.name = 'MedicoSinFichaError';
  }
}

export function mensajeMedico(err: unknown): string {
  if (err instanceof MedicoSinFichaError) {
    return err.message;
  }
  if (err instanceof TimeoutError) {
    return 'La consulta tardó demasiado. Reintentá.';
  }
  if (err instanceof HttpErrorResponse) {
    const texto = err.error?.status_text;
    if (typeof texto === 'string' && texto.trim()) {
      return texto;
    }
    if (err.status === 0) {
      return 'No hay conexión con el servidor. Reintentá.';
    }
  }
  return 'No se pudo identificar al médico. Reintentá.';
}

export function comoMedico(sesion: MedicoSesion): Medico {
  return {
    id: sesion.id,
    nombre: sesion.nombre,
    apellido: sesion.apellido,
    dni: sesion.dni || '',
    email: sesion.email,
    telefono: sesion.telefono || '',
    matricula: sesion.matricula,
    refepsId: sesion.refepsId || undefined,
    especialidades: sesion.especialidades || []
  };
}

/** Forma que ya consumen horarios y el turno manual. */
export function staffComoLista(sesion: MedicoSesion): Array<{
  id: number;
  centroAtencion: { id: number; nombre: string } | null;
  centro: { id: number; nombre: string } | null;
  especialidad: { id: number; nombre: string } | null;
  medico: { id: number; nombre: string; apellido: string; matricula: string };
}> {
  return (sesion.staff || []).map(staff => {
    const centro = staff.centroId
      ? { id: staff.centroId, nombre: staff.centroNombre || 'Centro' }
      : null;
    return {
      id: staff.id,
      centroAtencion: centro,
      centro,
      especialidad: staff.especialidadId
        ? { id: staff.especialidadId, nombre: staff.especialidadNombre || '' }
        : null,
      medico: {
        id: sesion.id,
        nombre: sesion.nombre,
        apellido: sesion.apellido,
        matricula: sesion.matricula
      }
    };
  });
}

/**
 * Resuelve al médico logueado contra el backend. localStorage queda solo como caché
 * para el resto de la pantalla, nunca como fuente del id.
 */
@Injectable({ providedIn: 'root' })
export class MedicoSesionService {
  private readonly url = environment.production
    ? `${environment.apiUrl}/medicos/me`
    : 'rest/medicos/me';

  private enCurso: Observable<MedicoSesion> | null = null;

  constructor(private http: HttpClient) {}

  resolver(forzar = false): Observable<MedicoSesion> {
    if (forzar) {
      this.enCurso = null;
    }
    if (!this.enCurso) {
      this.enCurso = this.pedir();
    }
    return this.enCurso;
  }

  invalidar(): void {
    this.enCurso = null;
  }

  private pedir(): Observable<MedicoSesion> {
    return this.http.get<DataPackage<MedicoSesion>>(this.url).pipe(
      timeout(15000),
      map(respuesta => this.normalizar(respuesta?.data)),
      tap(sesion => this.cachear(sesion)),
      catchError(error => {
        this.enCurso = null;
        if (error instanceof MedicoSinFichaError) {
          return throwError(() => error);
        }
        if (error instanceof HttpErrorResponse && error.status === 404) {
          const texto = error.error?.status_text;
          const mensaje = typeof texto === 'string' && texto.trim()
            ? texto
            : MedicoSinFichaError.porDefecto;
          return throwError(() => new MedicoSinFichaError(mensaje));
        }
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
  }

  private normalizar(data: MedicoSesion | null | undefined): MedicoSesion {
    if (!data?.id) {
      throw new MedicoSinFichaError();
    }
    const staff = data.staff || [];
    return {
      ...data,
      especialidades: data.especialidades || [],
      staff,
      staffMedicoId: data.staffMedicoId || staff[0]?.id || null
    };
  }

  private cachear(sesion: MedicoSesion): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.setItem('medicoId', String(sesion.id));
    localStorage.setItem('medicoData', JSON.stringify(comoMedico(sesion)));
    if (sesion.staffMedicoId) {
      localStorage.setItem('staffMedicoId', String(sesion.staffMedicoId));
    } else {
      localStorage.removeItem('staffMedicoId');
    }
  }
}
