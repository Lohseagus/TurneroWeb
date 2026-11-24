import { Component, Input, OnDestroy, OnInit, ChangeDetectionStrategy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { Observable, Subscription } from "rxjs";
import { CentroAtencionService } from "../centrosAtencion/centroAtencion.service";
import { ConsultorioService } from "../consultorios/consultorio.service";
import { EspecialidadService } from "../especialidades/especialidad.service";
import { UserContextService } from "../services/user-context.service";
import { StaffMedicoService } from "../staffMedicos/staffMedico.service";
import { TurnoService } from "../turnos/turno.service";
import { CalendarioService } from "./calendario.service";
import {
  DIAS_CORTO,
  DiaGrilla,
  TurnoGrilla,
  accionesDe,
  armarGrilla,
  claseEstado,
  etiquetaDia,
  etiquetaEstado,
  isoDe,
  normalizarFeriado,
  normalizarTurno,
  rangoMesVisible,
  rolDe,
  tituloMes,
  type RolCalendario,
} from "./calendario-mes";

interface Opcion {
  id: number;
  nombre: string;
}

@Component({
  selector: "app-calendario-mes",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./calendario-mes.component.html",
  styleUrl: "./calendario-mes.component.css",
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class CalendarioMesComponent implements OnInit, OnDestroy {
  @Input() embebido = false;

  readonly diasSemana = DIAS_CORTO;
  anio = new Date().getFullYear();
  mes = new Date().getMonth() + 1;
  grilla: DiaGrilla[] = [];
  rol: RolCalendario = "paciente";
  esSuperAdmin = false;
  cargando = false;
  error = "";
  mensaje = "";
  avisoCentro = false;
  diaSeleccionado: DiaGrilla | null = null;
  cancelandoId: number | null = null;
  motivo = "";
  accionEnCurso = false;

  centroId: number | null = null;
  medicoId: number | null = null;
  especialidadId: number | null = null;
  consultorioId: number | null = null;
  centros: Opcion[] = [];
  medicos: Opcion[] = [];
  especialidades: Opcion[] = [];
  consultorios: Opcion[] = [];

  private sub?: Subscription;

  constructor(
    private calendario: CalendarioService,
    private turnos: TurnoService,
    private contexto: UserContextService,
    private centrosService: CentroAtencionService,
    private staffService: StaffMedicoService,
    private especialidadService: EspecialidadService,
    private consultorioService: ConsultorioService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const hoy = new Date();
    this.anio = hoy.getFullYear();
    this.mes = hoy.getMonth() + 1;
    this.rol = rolDe(this.contexto.getPrimaryRole());
    this.esSuperAdmin = this.contexto.isSuperAdmin;
    if (this.rol === "staff" && !this.esSuperAdmin) {
      this.centroId = this.contexto.tenantId;
      this.cargarCatalogo();
    }
    if (this.esSuperAdmin) {
      this.centrosService.all().subscribe({
        next: (resp) => {
          this.centros = this.opciones(resp);
        },
        error: () => {
          this.centros = [];
        },
      });
    }
    this.cargar();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get titulo(): string {
    return tituloMes(this.anio, this.mes);
  }

  get hoyIso(): string {
    return isoDe(new Date());
  }

  get diasDelMes(): DiaGrilla[] {
    return this.grilla.filter((dia) => dia.esMesActual);
  }

  mover(delta: number): void {
    const fecha = new Date(this.anio, this.mes - 1 + delta, 1);
    this.anio = fecha.getFullYear();
    this.mes = fecha.getMonth() + 1;
    this.cerrarDia();
    this.cargar();
  }

  irHoy(): void {
    const hoy = new Date();
    this.anio = hoy.getFullYear();
    this.mes = hoy.getMonth() + 1;
    this.cerrarDia();
    this.cargar();
  }

  alCambiarCentro(centroId: number | null): void {
    this.centroId = this.numero(centroId);
    this.medicoId = null;
    this.especialidadId = null;
    this.consultorioId = null;
    this.cargarCatalogo();
    this.cerrarDia();
    this.cargar();
  }

  alFiltrar(): void {
    this.medicoId = this.numero(this.medicoId);
    this.especialidadId = this.numero(this.especialidadId);
    this.consultorioId = this.numero(this.consultorioId);
    this.cerrarDia();
    this.cargar();
  }

  elegir(dia: DiaGrilla): void {
    this.mensaje = "";
    this.cancelandoId = null;
    this.motivo = "";
    this.diaSeleccionado = this.diaSeleccionado?.fecha === dia.fecha ? null : dia;
  }

  etiqueta(iso: string): string {
    return etiquetaDia(iso);
  }

  estado(turno: TurnoGrilla): string {
    return etiquetaEstado(turno.estado);
  }

  clase(turno: TurnoGrilla): string {
    return claseEstado(turno.estado);
  }

  hora(turno: TurnoGrilla): string {
    return (turno.horaInicio || "").slice(0, 5);
  }

  tituloTurno(turno: TurnoGrilla): string {
    if (this.rol === "paciente") {
      return this.nombre(turno.medicoNombre, turno.medicoApellido) || "Turno";
    }
    return this.nombre(turno.pacienteNombre, turno.pacienteApellido) || "Turno";
  }

  detalleTurno(turno: TurnoGrilla): string {
    const partes = [turno.especialidadNombre, turno.consultorioNombre];
    if (this.rol === "medico" || this.rol === "paciente") {
      partes.push(turno.centroNombre);
    } else {
      partes.unshift(this.nombre(turno.medicoNombre, turno.medicoApellido));
    }
    return partes.filter((parte) => !!parte).join(" · ");
  }

  acciones(turno: TurnoGrilla) {
    return accionesDe(this.rol, turno.estado, turno.fecha, this.hoyIso, turno.pacienteId);
  }

  confirmar(turno: TurnoGrilla): void {
    this.ejecutar(this.turnos.confirmar(turno.id), "Turno confirmado");
  }

  empezarCancelar(turno: TurnoGrilla): void {
    this.cancelandoId = turno.id;
    this.motivo = "";
    this.mensaje = "";
  }

  cancelar(turno: TurnoGrilla): void {
    const motivo = this.motivo.trim();
    if (motivo.length < 5) {
      this.mensaje = "El motivo tiene que tener al menos 5 caracteres";
      return;
    }
    this.ejecutar(this.turnos.updateEstado(turno.id, "CANCELADO", motivo), "Turno cancelado");
  }

  reagendar(turno: TurnoGrilla): void {
    this.router.navigate(["/operador-agenda"], { queryParams: { turnoId: turno.id } });
  }

  ver(turno: TurnoGrilla): void {
    this.router.navigate(["/turnos", turno.id]);
  }

  consulta(turno: TurnoGrilla): void {
    this.router.navigate(["/medico-consulta/nueva"], { queryParams: { turnoId: turno.id } });
  }

  historia(turno: TurnoGrilla): void {
    if (turno.pacienteId) {
      this.router.navigate(["/medico-hce", turno.pacienteId]);
    }
  }

  asistencia(turno: TurnoGrilla, asistio: boolean): void {
    this.ejecutar(
      this.turnos.registrarAsistencia(turno.id, asistio),
      asistio ? "Asistencia registrada" : "Ausencia registrada"
    );
  }

  private cargar(): void {
    this.error = "";
    this.avisoCentro = this.esSuperAdmin && !this.centroId;
    const hoy = this.hoyIso;
    const rango = rangoMesVisible(this.anio, this.mes);
    if (this.avisoCentro) {
      this.grilla = armarGrilla(this.anio, this.mes, hoy, [], []);
      this.cargando = false;
      return;
    }
    this.cargando = true;
    this.sub?.unsubscribe();
    this.sub = this.calendario
      .mes({
        desde: rango.desde,
        hasta: rango.hasta,
        centroId: this.rol === "staff" ? this.centroId : null,
        medicoId: this.rol === "staff" ? this.medicoId : null,
        especialidadId: this.rol === "staff" ? this.especialidadId : null,
        consultorioId: this.rol === "staff" ? this.consultorioId : null,
      })
      .subscribe({
        next: (resp) => {
          this.cargando = false;
          if (resp?.status_code && resp.status_code !== 200) {
            this.error = resp.status_text || "No se pudo cargar el calendario";
            this.grilla = armarGrilla(this.anio, this.mes, hoy, [], []);
            return;
          }
          const data = resp?.data;
          this.grilla = armarGrilla(
            this.anio,
            this.mes,
            hoy,
            (data?.turnos || []).map(normalizarTurno),
            (data?.feriados || []).map(normalizarFeriado)
          );
          this.conservarDia();
        },
        error: (error) => {
          this.cargando = false;
          this.error = error?.error?.status_text || "No se pudo cargar el calendario";
          this.grilla = armarGrilla(this.anio, this.mes, hoy, [], []);
          this.conservarDia();
        },
      });
  }

  private cargarCatalogo(): void {
    if (!this.centroId) {
      this.medicos = [];
      this.especialidades = [];
      this.consultorios = [];
      return;
    }
    const centroId = this.centroId;
    this.staffService.getByCentroAtencion(centroId).subscribe({
      next: (resp) => {
        const vistos = new Set<number>();
        this.medicos = [];
        for (const item of this.opcionesCrudas(resp)) {
          const medico = item?.medico;
          const id = Number(medico?.id ?? item?.medicoId);
          if (!id || vistos.has(id)) {
            continue;
          }
          vistos.add(id);
          this.medicos.push({
            id,
            nombre: this.nombre(medico?.nombre, medico?.apellido) || `Médico ${id}`,
          });
        }
      },
      error: () => {
        this.medicos = [];
      },
    });
    this.especialidadService.getByCentroAtencion(centroId).subscribe({
      next: (resp) => {
        this.especialidades = this.opciones(resp);
      },
      error: () => {
        this.especialidades = [];
      },
    });
    this.consultorioService.getByCentroAtencion(centroId).subscribe({
      next: (resp) => {
        this.consultorios = this.opciones(resp);
      },
      error: () => {
        this.consultorios = [];
      },
    });
  }

  private ejecutar(
    pedido: Observable<{ status_code?: number; status_text?: string }>,
    ok: string
  ): void {
    this.accionEnCurso = true;
    this.mensaje = "";
    pedido.subscribe({
      next: (resp: { status_code?: number; status_text?: string }) => {
        this.accionEnCurso = false;
        if (resp?.status_code && resp.status_code !== 200) {
          this.mensaje = resp.status_text || "No se pudo completar la acción";
          return;
        }
        this.mensaje = ok;
        this.cancelandoId = null;
        this.motivo = "";
        this.cargar();
      },
      error: (error: { error?: { status_text?: string } }) => {
        this.accionEnCurso = false;
        this.mensaje = error?.error?.status_text || "No se pudo completar la acción";
      },
    });
  }

  private conservarDia(): void {
    if (!this.diaSeleccionado) {
      return;
    }
    this.diaSeleccionado = this.grilla.find((dia) => dia.fecha === this.diaSeleccionado?.fecha) ?? null;
  }

  private cerrarDia(): void {
    this.diaSeleccionado = null;
    this.cancelandoId = null;
    this.motivo = "";
  }

  private opciones(resp: unknown): Opcion[] {
    return this.opcionesCrudas(resp)
      .map((item) => ({ id: Number(item?.id), nombre: String(item?.nombre || "") }))
      .filter((item) => item.id && item.nombre);
  }

  private opcionesCrudas(resp: any): any[] {
    if (Array.isArray(resp)) {
      return resp;
    }
    if (Array.isArray(resp?.data)) {
      return resp.data;
    }
    return [];
  }

  private numero(valor: number | string | null): number | null {
    if (valor === null || valor === undefined || valor === "") {
      return null;
    }
    const numero = Number(valor);
    return Number.isFinite(numero) && numero > 0 ? numero : null;
  }

  private nombre(nombre?: string, apellido?: string): string {
    return [nombre, apellido].filter((parte) => !!parte).join(" ");
  }
}
