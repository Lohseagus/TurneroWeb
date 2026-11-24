import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../environments/environment";
import { DataPackage } from "../data.package";
import { FeriadoGrilla, TurnoGrilla } from "./calendario-mes";

export interface CalendarioMes {
  desde: string;
  hasta: string;
  turnos: TurnoGrilla[];
  feriados: FeriadoGrilla[];
}

export interface FiltroCalendario {
  desde: string;
  hasta: string;
  centroId?: number | null;
  medicoId?: number | null;
  especialidadId?: number | null;
  consultorioId?: number | null;
}

@Injectable({
  providedIn: "root",
})
export class CalendarioService {
  private url = environment.production ? `${environment.apiUrl}/turno/calendario` : "rest/turno/calendario";

  constructor(private http: HttpClient) {}

  mes(filtro: FiltroCalendario): Observable<DataPackage<CalendarioMes>> {
    let params = new HttpParams().set("desde", filtro.desde).set("hasta", filtro.hasta);
    if (filtro.centroId) {
      params = params.set("centroId", String(filtro.centroId));
    }
    if (filtro.medicoId) {
      params = params.set("medicoId", String(filtro.medicoId));
    }
    if (filtro.especialidadId) {
      params = params.set("especialidadId", String(filtro.especialidadId));
    }
    if (filtro.consultorioId) {
      params = params.set("consultorioId", String(filtro.consultorioId));
    }
    return this.http.get<DataPackage<CalendarioMes>>(this.url, { params });
  }
}
