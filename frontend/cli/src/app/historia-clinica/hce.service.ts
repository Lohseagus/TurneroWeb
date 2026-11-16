import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DataPackage } from '../data.package';
import {
  AdjuntoHce,
  Cie10,
  ConsultaHce,
  MetadataHce,
  PacienteResumenHce,
  PerfilClinico,
  TimelineItem,
  TurnoContextoHce
} from './hce.models';

@Injectable({ providedIn: 'root' })
export class HceService {
  private readonly base = environment.production ? `${environment.apiUrl}/hce` : 'rest/hce';

  constructor(private http: HttpClient) {}

  cie10(q: string): Observable<DataPackage<Cie10[]>> {
    return this.http.get<DataPackage<Cie10[]>>(`${this.base}/cie10`, { params: new HttpParams().set('q', q) });
  }

  misPacientes(): Observable<DataPackage<PacienteResumenHce[]>> {
    return this.http.get<DataPackage<PacienteResumenHce[]>>(`${this.base}/mis-pacientes`);
  }

  contextoTurno(turnoId: number): Observable<DataPackage<TurnoContextoHce>> {
    return this.http.get<DataPackage<TurnoContextoHce>>(`${this.base}/turnos/${turnoId}`);
  }

  miHistoria(): Observable<DataPackage<{ perfil: PerfilClinico; timeline: TimelineItem[]; adjuntos: AdjuntoHce[] }>> {
    return this.http.get<DataPackage<{ perfil: PerfilClinico; timeline: TimelineItem[]; adjuntos: AdjuntoHce[] }>>(`${this.base}/mi`);
  }

  consentimiento(comparteEntreCentros: boolean): Observable<DataPackage<PerfilClinico>> {
    return this.http.put<DataPackage<PerfilClinico>>(`${this.base}/mi/consentimiento`, { comparteEntreCentros });
  }

  perfil(pacienteId: number): Observable<DataPackage<PerfilClinico>> {
    return this.http.get<DataPackage<PerfilClinico>>(`${this.base}/pacientes/${pacienteId}/perfil`);
  }

  guardarPerfil(pacienteId: number, perfil: PerfilClinico): Observable<DataPackage<PerfilClinico>> {
    return this.http.put<DataPackage<PerfilClinico>>(`${this.base}/pacientes/${pacienteId}/perfil`, perfil);
  }

  timeline(pacienteId: number, filtros: {
    especialidadId?: number | null;
    centroId?: number | null;
    medicoId?: number | null;
    tipo?: string;
  }): Observable<DataPackage<TimelineItem[]>> {
    let params = new HttpParams();
    if (filtros.especialidadId) params = params.set('especialidadId', filtros.especialidadId);
    if (filtros.centroId) params = params.set('centroId', filtros.centroId);
    if (filtros.medicoId) params = params.set('medicoId', filtros.medicoId);
    if (filtros.tipo) params = params.set('tipo', filtros.tipo);
    return this.http.get<DataPackage<TimelineItem[]>>(`${this.base}/pacientes/${pacienteId}/timeline`, { params });
  }

  metadata(pacienteId: number): Observable<DataPackage<MetadataHce>> {
    return this.http.get<DataPackage<MetadataHce>>(`${this.base}/pacientes/${pacienteId}/metadata`);
  }

  crearConsulta(body: Partial<ConsultaHce>): Observable<DataPackage<ConsultaHce>> {
    return this.http.post<DataPackage<ConsultaHce>>(`${this.base}/consultas`, body);
  }

  obtenerConsulta(id: number): Observable<DataPackage<ConsultaHce>> {
    return this.http.get<DataPackage<ConsultaHce>>(`${this.base}/consultas/${id}`);
  }

  actualizarConsulta(id: number, body: Partial<ConsultaHce>): Observable<DataPackage<ConsultaHce>> {
    return this.http.put<DataPackage<ConsultaHce>>(`${this.base}/consultas/${id}`, body);
  }

  cerrarConsulta(id: number): Observable<DataPackage<ConsultaHce>> {
    return this.http.post<DataPackage<ConsultaHce>>(`${this.base}/consultas/${id}/cerrar`, {});
  }

  enmendar(id: number, texto: string, motivo: string): Observable<DataPackage<unknown>> {
    return this.http.post<DataPackage<unknown>>(`${this.base}/consultas/${id}/enmiendas`, { texto, motivo });
  }

  adjuntos(pacienteId: number): Observable<DataPackage<AdjuntoHce[]>> {
    return this.http.get<DataPackage<AdjuntoHce[]>>(`${this.base}/pacientes/${pacienteId}/adjuntos`);
  }

  subirAdjuntoConsulta(consultaId: number, archivo: File, tipo: string): Observable<DataPackage<AdjuntoHce>> {
    const form = new FormData();
    form.append('archivo', archivo);
    form.append('tipo', tipo);
    return this.http.post<DataPackage<AdjuntoHce>>(`${this.base}/consultas/${consultaId}/adjuntos`, form);
  }

  descargarAdjunto(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/adjuntos/${id}`, { responseType: 'blob' });
  }

  pdfConsulta(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/consultas/${id}/pdf`, { responseType: 'blob' });
  }

  pdfPaciente(pacienteId: number): Observable<Blob> {
    return this.http.get(`${this.base}/pacientes/${pacienteId}/pdf`, { responseType: 'blob' });
  }

  pdfPropio(): Observable<Blob> {
    return this.http.get(`${this.base}/mi/pdf`, { responseType: 'blob' });
  }
}
