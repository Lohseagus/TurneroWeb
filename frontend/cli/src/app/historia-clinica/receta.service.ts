import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DataPackage } from '../data.package';
import { CatalogoMedicamento, OrdenEstudio, Receta, VerificacionPublica } from './receta.models';

@Injectable({ providedIn: 'root' })
export class RecetaService {
  private readonly recetas = environment.production ? `${environment.apiUrl}/recetas` : 'rest/recetas';
  private readonly ordenes = environment.production ? `${environment.apiUrl}/ordenes-estudio` : 'rest/ordenes-estudio';

  constructor(private http: HttpClient) {}

  catalogo(q: string): Observable<DataPackage<CatalogoMedicamento[]>> {
    return this.http.get<DataPackage<CatalogoMedicamento[]>>(`${this.recetas}/catalogo`, {
      params: new HttpParams().set('q', q)
    });
  }

  porConsulta(consultaId: number): Observable<DataPackage<Receta[]>> {
    return this.http.get<DataPackage<Receta[]>>(`${this.recetas}`, {
      params: new HttpParams().set('consultaId', consultaId)
    });
  }

  crear(body: object): Observable<DataPackage<Receta>> {
    return this.http.post<DataPackage<Receta>>(this.recetas, body);
  }

  actualizar(id: number, body: object): Observable<DataPackage<Receta>> {
    return this.http.put<DataPackage<Receta>>(`${this.recetas}/${id}`, body);
  }

  emitir(id: number): Observable<DataPackage<Receta>> {
    return this.http.post<DataPackage<Receta>>(`${this.recetas}/${id}/emitir`, {});
  }

  anular(id: number, motivo: string): Observable<DataPackage<Receta>> {
    return this.http.post<DataPackage<Receta>>(`${this.recetas}/${id}/anular`, { motivo });
  }

  pdf(id: number): Observable<Blob> {
    return this.http.get(`${this.recetas}/${id}/pdf`, { responseType: 'blob' });
  }

  mias(): Observable<DataPackage<Receta[]>> {
    return this.http.get<DataPackage<Receta[]>>(`${this.recetas}/mias`);
  }

  verificar(codigo: string): Observable<DataPackage<VerificacionPublica>> {
    return this.http.get<DataPackage<VerificacionPublica>>(`${this.recetas}/verificar/${codigo}`);
  }

  ordenesPorConsulta(consultaId: number): Observable<DataPackage<OrdenEstudio[]>> {
    return this.http.get<DataPackage<OrdenEstudio[]>>(this.ordenes, {
      params: new HttpParams().set('consultaId', consultaId)
    });
  }

  crearOrden(body: object): Observable<DataPackage<OrdenEstudio>> {
    return this.http.post<DataPackage<OrdenEstudio>>(this.ordenes, body);
  }

  actualizarOrden(id: number, body: object): Observable<DataPackage<OrdenEstudio>> {
    return this.http.put<DataPackage<OrdenEstudio>>(`${this.ordenes}/${id}`, body);
  }

  emitirOrden(id: number): Observable<DataPackage<OrdenEstudio>> {
    return this.http.post<DataPackage<OrdenEstudio>>(`${this.ordenes}/${id}/emitir`, {});
  }

  anularOrden(id: number, motivo: string): Observable<DataPackage<OrdenEstudio>> {
    return this.http.post<DataPackage<OrdenEstudio>>(`${this.ordenes}/${id}/anular`, { motivo });
  }

  pdfOrden(id: number): Observable<Blob> {
    return this.http.get(`${this.ordenes}/${id}/pdf`, { responseType: 'blob' });
  }

  misOrdenes(): Observable<DataPackage<OrdenEstudio[]>> {
    return this.http.get<DataPackage<OrdenEstudio[]>>(`${this.ordenes}/mias`);
  }

  verificarOrden(codigo: string): Observable<DataPackage<VerificacionPublica>> {
    return this.http.get<DataPackage<VerificacionPublica>>(`${this.ordenes}/verificar/${codigo}`);
  }
}
