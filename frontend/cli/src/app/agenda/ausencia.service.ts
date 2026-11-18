import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DataPackage } from '../data.package';

export interface AusenciaSolicitud {
  staffMedicoId: number;
  centroAtencionId?: number | null;
  fechaInicio: string;
  fechaFin: string;
  horaInicio?: string | null;
  horaFin?: string | null;
  motivo?: string | null;
}

export interface TurnoAfectado {
  id: number;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  estado: string;
  pacienteNombre: string;
  pacienteApellido: string;
}

export interface AusenciaResultado {
  ausencia?: {
    id?: number;
    fecha?: string;
    fechaFin?: string;
    descripcion?: string;
  } | null;
  cancelados: TurnoAfectado[];
  requierenGestionManual: TurnoAfectado[];
  mensaje: string;
}

export interface AusenciaListada {
  id: number;
  fecha: string;
  fechaFin?: string | null;
  descripcion?: string;
  horaInicio?: string | null;
  horaFin?: string | null;
  staffMedicoId?: number;
  medicoNombre?: string;
  medicoApellido?: string;
  especialidadNombre?: string;
  centroAtencionNombre?: string;
}

@Injectable({ providedIn: 'root' })
export class AusenciaService {
  private url = environment.production
    ? `${environment.apiUrl}/agenda/ausencias`
    : 'rest/agenda/ausencias';

  constructor(private http: HttpClient) {}

  listar(staffMedicoId?: number | null, centroId?: number | null): Observable<DataPackage<AusenciaListada[]>> {
    let params = new HttpParams();
    if (staffMedicoId) {
      params = params.set('staffMedicoId', staffMedicoId);
    }
    if (centroId) {
      params = params.set('centroId', centroId);
    }
    return this.http.get<DataPackage<AusenciaListada[]>>(this.url, { params });
  }

  previsualizar(solicitud: AusenciaSolicitud): Observable<DataPackage<AusenciaResultado>> {
    let params = new HttpParams()
      .set('staffMedicoId', solicitud.staffMedicoId)
      .set('fechaInicio', solicitud.fechaInicio)
      .set('fechaFin', solicitud.fechaFin);
    if (solicitud.horaInicio) {
      params = params.set('horaInicio', solicitud.horaInicio);
    }
    if (solicitud.horaFin) {
      params = params.set('horaFin', solicitud.horaFin);
    }
    if (solicitud.motivo) {
      params = params.set('motivo', solicitud.motivo);
    }
    return this.http.get<DataPackage<AusenciaResultado>>(`${this.url}/preview`, { params });
  }

  confirmar(solicitud: AusenciaSolicitud): Observable<DataPackage<AusenciaResultado>> {
    return this.http.post<DataPackage<AusenciaResultado>>(this.url, solicitud);
  }

  /**
   * La baja viaja como POST con el profesional y el centro en el cuerpo,
   * para que el filtro de solo lectura pueda resolver la sede.
   */
  eliminar(id: number, staffMedicoId: number, centroAtencionId?: number | null): Observable<DataPackage<unknown>> {
    return this.http.post<DataPackage<unknown>>(`${this.url}/${id}/baja`, {
      staffMedicoId,
      centroAtencionId: centroAtencionId ?? null
    });
  }
}
