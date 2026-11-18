import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DataPackage } from '../data.package';
import { AddonSaas, AvisoSuscripcion, CatalogoSaas, ConfiguracionSaas, PlanSaas, PrecioSaas, RegistroSaasResultado, ResumenSaas, SuscripcionSaas, PagoSaas } from './saas.models';

@Injectable({ providedIn: 'root' })
export class SaasService {

  private readonly base = environment.production ? `${environment.apiUrl}/saas` : 'rest/saas';

  constructor(private http: HttpClient) {}

  planes(): Observable<DataPackage<CatalogoSaas>> {
    return this.http.get<DataPackage<CatalogoSaas>>(`${this.base}/planes`);
  }

  cotizar(body: {
    planCodigo: string;
    ciclo: string;
    profesionalesContratados: number;
    sedes: number;
    addons: string[];
  }): Observable<DataPackage<PrecioSaas>> {
    return this.http.post<DataPackage<PrecioSaas>>(`${this.base}/cotizar`, body);
  }

  registrar(body: Record<string, unknown>): Observable<DataPackage<RegistroSaasResultado>> {
    return this.http.post<DataPackage<RegistroSaasResultado>>(`${this.base}/registro`, body);
  }

  miSuscripcion(): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.get<DataPackage<SuscripcionSaas>>(`${this.base}/mi-suscripcion`);
  }

  aviso(centroId?: number): Observable<DataPackage<AvisoSuscripcion>> {
    const query = centroId == null ? '' : `?centroId=${centroId}`;
    return this.http.get<DataPackage<AvisoSuscripcion>>(`${this.base}/aviso${query}`);
  }

  pagar(ciclo: string): Observable<DataPackage<PrecioSaas>> {
    return this.http.post<DataPackage<PrecioSaas>>(`${this.base}/mi-suscripcion/pagar`, { ciclo });
  }

  cambiarPlan(body: Record<string, unknown>): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.post<DataPackage<SuscripcionSaas>>(`${this.base}/mi-suscripcion/cambiar-plan`, body);
  }

  crearSede(body: Record<string, unknown>): Observable<DataPackage<{ id: number; nombre: string }>> {
    return this.http.post<DataPackage<{ id: number; nombre: string }>>(`${this.base}/mi-suscripcion/sedes`, body);
  }

  resumen(): Observable<DataPackage<ResumenSaas>> {
    return this.http.get<DataPackage<ResumenSaas>>(`${this.base}/admin/resumen`);
  }

  suscripciones(estado: string, plan: string, q: string): Observable<DataPackage<SuscripcionSaas[]>> {
    const params = new URLSearchParams();
    if (estado) params.set('estado', estado);
    if (plan) params.set('plan', plan);
    if (q) params.set('q', q);
    const query = params.toString();
    return this.http.get<DataPackage<SuscripcionSaas[]>>(`${this.base}/admin/suscripciones${query ? '?' + query : ''}`);
  }

  detalle(id: number): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.get<DataPackage<SuscripcionSaas>>(`${this.base}/admin/suscripciones/${id}`);
  }

  extenderTrial(id: number, dias: number): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.post<DataPackage<SuscripcionSaas>>(`${this.base}/admin/suscripciones/${id}/extender-trial`, { dias });
  }

  suspender(id: number, motivo: string): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.post<DataPackage<SuscripcionSaas>>(`${this.base}/admin/suscripciones/${id}/suspender`, { motivo });
  }

  reactivar(id: number): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.post<DataPackage<SuscripcionSaas>>(`${this.base}/admin/suscripciones/${id}/reactivar`, {});
  }

  cancelar(id: number, motivo: string): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.post<DataPackage<SuscripcionSaas>>(`${this.base}/admin/suscripciones/${id}/cancelar`, { motivo });
  }

  cambiarPlanAdmin(id: number, body: Record<string, unknown>): Observable<DataPackage<SuscripcionSaas>> {
    return this.http.post<DataPackage<SuscripcionSaas>>(`${this.base}/admin/suscripciones/${id}/cambiar-plan`, body);
  }

  catalogoAdmin(): Observable<DataPackage<CatalogoSaas>> {
    return this.http.get<DataPackage<CatalogoSaas>>(`${this.base}/admin/planes`);
  }

  guardarPlan(plan: PlanSaas): Observable<DataPackage<PlanSaas>> {
    return this.http.put<DataPackage<PlanSaas>>(`${this.base}/admin/planes/${plan.id}`, plan);
  }

  guardarConfiguracion(config: ConfiguracionSaas): Observable<DataPackage<ConfiguracionSaas>> {
    return this.http.put<DataPackage<ConfiguracionSaas>>(`${this.base}/admin/configuracion`, config);
  }

  guardarAddon(addon: AddonSaas): Observable<DataPackage<AddonSaas>> {
    return this.http.put<DataPackage<AddonSaas>>(`${this.base}/admin/addons/${addon.id}`, addon);
  }

  pagos(): Observable<DataPackage<PagoSaas[]>> {
    return this.http.get<DataPackage<PagoSaas[]>>(`${this.base}/admin/pagos`);
  }
}
