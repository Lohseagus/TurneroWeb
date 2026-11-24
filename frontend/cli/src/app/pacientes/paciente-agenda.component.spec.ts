import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { convertToParamMap, provideRouter, ActivatedRoute, Router } from "@angular/router";
import { Observable, of, throwError } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CommonModule } from "@angular/common";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { RouterModule } from "@angular/router";
import { PacienteAgendaComponent } from "./paciente-agenda.component";
import { AgendaService } from "../agenda/agenda.service";
import { EspecialidadService } from "../especialidades/especialidad.service";
import { CentroAtencionService } from "../centrosAtencion/centroAtencion.service";
import { MedicoService } from "../medicos/medico.service";
import { TurnoService } from "../turnos/turno.service";
import { DiasExcepcionalesService } from "../agenda/dias-excepcionales.service";
import { DeepLinkService } from "../services/deep-link.service";
import { AuthService, Role } from "../inicio-sesion/auth.service";
import { UserContextService } from "../services/user-context.service";
import { ToastService } from "../services/toast.service";
import { ModalService } from "../modal/modal.service";
import { SaasService } from "../saas/saas.service";
import { PacienteSesionService } from "./paciente-sesion.service";

@Component({ selector: "app-lista-espera-form", standalone: true, template: "<p>Lista de espera</p>" })
class ListaEsperaStub {
  @Input() especialidadNombre: string | null = null;
  @Input() centroId: number | null = null;
}

@Component({ selector: "app-centros-mapa-modal", standalone: true, template: "" })
class MapaStub {
  @Input() centros: unknown;
  @Input() especialidades: unknown;
  @Input() slotsDisponibles: unknown;
  @Input() especialidadSeleccionadaInicial: unknown;
  @Output() centroSeleccionado = new EventEmitter<unknown>();
  @Output() modalCerrado = new EventEmitter<void>();
}

const params: Record<string, string> = {};

function consulta() {
  return convertToParamMap({ ...params });
}

const routeStub = {
  snapshot: {
    get queryParamMap() {
      return consulta();
    },
    get queryParams() {
      return { ...params };
    },
  },
  get queryParamMap() {
    return of(consulta());
  },
  get queryParams() {
    return of({ ...params });
  },
  params: of({}),
  paramMap: of(convertToParamMap({})),
  fragment: of(null),
  data: of({}),
  url: of([]),
};

const agenda = {
  obtenerTodosLosEventos: vi.fn((..._args: unknown[]): Observable<unknown> => of([])),
  getAgendaPublica: vi.fn((..._args: unknown[]): Observable<unknown> => of({ data: [] })),
};

let autenticado = true;
let roles: Role[] = [Role.PACIENTE];

describe("PacienteAgendaComponent", () => {
  let fixture: ComponentFixture<PacienteAgendaComponent>;

  beforeEach(() => {
    autenticado = true;
    roles = [Role.PACIENTE];
    for (const clave of Object.keys(params)) delete params[clave];
    sessionStorage.clear();
    agenda.obtenerTodosLosEventos.mockReset();
    agenda.getAgendaPublica.mockReset();
    agenda.obtenerTodosLosEventos.mockImplementation(() => of([]));
    agenda.getAgendaPublica.mockImplementation(() => of({ data: [] }));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function montar(): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PacienteAgendaComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: routeStub },
        { provide: AgendaService, useValue: agenda },
        {
          provide: EspecialidadService,
          useValue: { all: () => of({ data: [{ id: 1, nombre: "Cardiología", descripcion: "" }] }) },
        },
        {
          provide: CentroAtencionService,
          useValue: { all: () => of({ data: [{ id: 4, nombre: "Clínica Norte" }] }) },
        },
        {
          provide: MedicoService,
          useValue: {
            getAll: () =>
              of({
                data: [{ id: 9, nombre: "Ana", apellido: "Pérez", especialidades: [{ id: 1, nombre: "Cardiología" }] }],
              }),
          },
        },
        { provide: TurnoService, useValue: { asignarTurno: () => of({}) } },
        {
          provide: DiasExcepcionalesService,
          useValue: { slotAfectadoPorExcepcion: () => false, getExcepcionesDelDia: () => [] },
        },
        { provide: DeepLinkService, useValue: { getContext: () => null, clearContext: () => undefined } },
        { provide: AuthService, useValue: { isAuthenticated: () => autenticado } },
        { provide: UserContextService, useValue: { hasAnyRole: (pedidos: Role[]) => pedidos.some((rol) => roles.includes(rol)) } },
        { provide: ToastService, useValue: { success() {}, error() {}, warning() {} } },
        { provide: ModalService, useValue: { alert() {}, confirm: () => Promise.resolve() } },
        { provide: SaasService, useValue: { aviso: () => of({ status_code: 200, data: {} }) } },
        { provide: PacienteSesionService, useValue: { id: () => of(1) } },
      ],
    })
      .overrideComponent(PacienteAgendaComponent, {
        set: {
          imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule, ListaEsperaStub, MapaStub],
        },
      })
      .compileComponents();

    vi.spyOn(TestBed.inject(Router), "navigate").mockResolvedValue(true);
    fixture = TestBed.createComponent(PacienteAgendaComponent);
    fixture.detectChanges();
  }

  async function esperarBusqueda(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 450));
    fixture.detectChanges();
  }

  it("no pide turnos al abrir y muestra el vacío", async () => {
    await montar();

    expect(agenda.obtenerTodosLosEventos).not.toHaveBeenCalled();
    expect(agenda.getAgendaPublica).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      "Elegí una especialidad, médico o centro para ver turnos disponibles"
    );
    expect(fixture.nativeElement.textContent).not.toContain("Aplicar Filtros");
    expect(fixture.nativeElement.textContent).not.toContain("Filtros Avanzados");
    expect(fixture.nativeElement.textContent).not.toContain("Búsqueda Rápida");
    expect(fixture.nativeElement.textContent).not.toContain("Mis Horarios Preferidos");
    expect(fixture.nativeElement.querySelector("[data-testid='mis-horarios']")).not.toBeNull();
  });

  it("un médico en esta pantalla no se trata como operador", async () => {
    roles = [Role.MEDICO, Role.PACIENTE];
    await montar();
    expect(fixture.componentInstance.esOperador).toBe(false);
  });

  it("un operador sigue sin la lista de espera del paciente", async () => {
    roles = [Role.OPERADOR, Role.PACIENTE];
    await montar();
    expect(fixture.componentInstance.esOperador).toBe(true);
  });

  it("abre la lista de espera con ?espera=1 sin cargar turnos", async () => {
    params["espera"] = "1";
    await montar();
    expect(fixture.componentInstance.mostrarListaEspera).toBe(true);
    expect(fixture.nativeElement.textContent).toContain("Lista de espera");
    expect(agenda.obtenerTodosLosEventos).not.toHaveBeenCalled();
  });

  it("respeta el centro del enlace y consulta la agenda privada", async () => {
    params["centroId"] = "4";
    await montar();
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledWith(4, expect.objectContaining({ centroId: 4 }));
    expect(agenda.getAgendaPublica).not.toHaveBeenCalled();
  });

  it("busca solo la especialidad cuando el texto coincide con el catálogo", async () => {
    await montar();
    fixture.componentInstance.onTextoBusqueda("cardiologia");
    await esperarBusqueda();
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledTimes(1);
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledWith(
      4,
      expect.objectContaining({ especialidad: "Cardiología" })
    );
  });

  it("no llama a la API si el texto no está en el catálogo y todavía no hay turnos", async () => {
    await montar();
    fixture.componentInstance.onTextoBusqueda("zzzz");
    await esperarBusqueda();
    expect(agenda.obtenerTodosLosEventos).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain("no está en el listado");
  });

  it("filtra en el cliente los turnos ya cargados si el texto no está en el catálogo", async () => {
    agenda.obtenerTodosLosEventos.mockReturnValue(of([slot(1, "Ana", "Pérez"), slot(2, "Luis", "Gómez")]));
    await montar();
    fixture.componentInstance.onTextoBusqueda("cardiologia");
    await esperarBusqueda();
    expect(fixture.nativeElement.textContent).toContain("Ana Pérez");
    expect(fixture.nativeElement.textContent).toContain("Luis Gómez");

    fixture.componentInstance.onTextoBusqueda("zz");
    await esperarBusqueda();
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).not.toContain("Ana Pérez");
    expect(fixture.nativeElement.textContent).toContain("No hay turnos disponibles");
  });

  it("si el texto coincide con un médico de los turnos ya vistos, lo pide al servidor", async () => {
    agenda.obtenerTodosLosEventos.mockReturnValue(of([slot(1, "Ana", "Pérez"), slot(2, "Luis", "Gómez")]));
    await montar();
    fixture.componentInstance.onTextoBusqueda("cardiologia");
    await esperarBusqueda();
    fixture.componentInstance.onTextoBusqueda("gomez");
    await esperarBusqueda();
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledTimes(2);
    expect(agenda.obtenerTodosLosEventos).toHaveBeenLastCalledWith(4, expect.objectContaining({ staffMedicoId: 2 }));
  });

  it("una visita anónima usa la agenda pública", async () => {
    autenticado = false;
    await montar();
    expect(fixture.nativeElement.querySelector("[data-testid='mis-horarios']")).toBeNull();
    fixture.componentInstance.filtrosForm.patchValue({ especialidad: "Cardiología" });
    await esperarBusqueda();
    expect(agenda.getAgendaPublica).toHaveBeenCalledWith(undefined, "Cardiología", undefined);
    expect(agenda.obtenerTodosLosEventos).not.toHaveBeenCalled();
  });

  it("muestra el error y Reintentar vuelve a pedir los turnos", async () => {
    agenda.obtenerTodosLosEventos.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    await montar();
    fixture.componentInstance.filtrosForm.patchValue({ especialidad: "Cardiología" });
    await esperarBusqueda();

    expect(fixture.nativeElement.textContent).toContain("No hay conexión con el servidor");
    agenda.obtenerTodosLosEventos.mockReturnValue(of([]));
    fixture.nativeElement.querySelector("[data-testid='reintentar-turnos']").click();
    fixture.detectChanges();
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledTimes(2);
  });

  it("el mapa se abre desde el botón y Limpiar borra la búsqueda sin volver a pedir todo", async () => {
    await montar();
    fixture.nativeElement.querySelector("[data-testid='mapa-centros']").click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector("app-centros-mapa-modal")).not.toBeNull();

    fixture.componentInstance.filtrosForm.patchValue({ centroAtencion: 4 });
    await esperarBusqueda();
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledTimes(1);
    fixture.nativeElement.querySelector("[data-testid='filtro-limpiar']").click();
    fixture.detectChanges();
    expect(agenda.obtenerTodosLosEventos).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain(
      "Elegí una especialidad, médico o centro para ver turnos disponibles"
    );
  });
});

function slot(id: number, nombre: string, apellido: string) {
  return {
    id,
    fecha: "2026-12-01",
    horaInicio: "09:00",
    horaFin: "09:30",
    esSlot: true,
    staffMedicoId: id,
    staffMedicoNombre: nombre,
    staffMedicoApellido: apellido,
    especialidadStaffMedico: "Cardiología",
    consultorioId: 1,
    consultorioNombre: "1",
    centroId: 4,
    nombreCentro: "Clínica Norte",
    ocupado: false,
  };
}
