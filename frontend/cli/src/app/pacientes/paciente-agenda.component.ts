import { Component, OnInit, OnDestroy, ChangeDetectorRef, LOCALE_ID, ChangeDetectionStrategy, inject, DestroyRef } from "@angular/core";
import { CommonModule, registerLocaleData } from "@angular/common";
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from "@angular/forms";
import { Router, ActivatedRoute, RouterModule } from "@angular/router";
import { EMPTY, Observable, Subject } from "rxjs";
import { catchError, debounceTime, switchMap, tap, timeout } from "rxjs/operators";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

import { TurnoService } from "../turnos/turno.service";
import { EspecialidadService } from "../especialidades/especialidad.service";
import { CentroAtencionService } from "../centrosAtencion/centroAtencion.service";
import { AgendaService } from "../agenda/agenda.service";
import { DiasExcepcionalesService } from "../agenda/dias-excepcionales.service";
import { DeepLinkService } from "../services/deep-link.service";
import { AuthService, Role } from "../inicio-sesion/auth.service";
import { UserContextService } from "../services/user-context.service";
import { ToastService } from "../services/toast.service";
import { ModalService } from "../modal/modal.service";
import { SaasService } from "../saas/saas.service";
import { PacienteSesionService } from "./paciente-sesion.service";
import { mensajeDeCarga } from "./mensaje-carga";
import { CentrosMapaModalComponent } from "../modal/centros-mapa-modal.component";
import { ListaEsperaFormComponent } from "../lista-espera/lista-espera-form.component";
import { Especialidad } from "../especialidades/especialidad";
import { StaffMedico } from "../staffMedicos/staffMedico";
import { CentroAtencion } from "../centrosAtencion/centroAtencion";
import { Medico } from "../medicos/medico";
import { MedicoService } from "../medicos/medico.service";
import { DataPackage } from "../data.package";
import localeEsAr from "@angular/common/locales/es-AR";
import {
  OpcionCatalogo,
  ResolucionBusqueda,
  normalizarBusqueda,
  numeroParam,
  resolverBusquedaTexto,
  sugerenciasCatalogo,
} from "./paciente-agenda-busqueda";

interface SlotDisponible {
  id: number;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  staffMedicoId: number;
  staffMedicoNombre: string;
  staffMedicoApellido: string;
  especialidadStaffMedico: string;
  consultorioId: number;
  consultorioNombre: string;
  centroId: number;
  nombreCentro: string;
  ocupado?: boolean;
  esSlot?: boolean;
  enMantenimiento?: boolean;
  titulo?: string;
}

interface FiltrosAgenda {
  centro: number | null;
  especialidad: string;
  medico: number | null;
  preferencia: boolean;
}

registerLocaleData(localeEsAr);

@Component({
  selector: "app-paciente-agenda",
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule, CentrosMapaModalComponent, ListaEsperaFormComponent],
  templateUrl: "./paciente-agenda.component.html",
  styleUrl: "./paciente-agenda.component.css",
  changeDetection: ChangeDetectionStrategy.Eager,
  providers: [{ provide: LOCALE_ID, useValue: "es-AR" }],
})
export class PacienteAgendaComponent implements OnInit, OnDestroy {
  private destroyRef = inject(DestroyRef);
  private readonly demoraBusquedaMs = 350;

  filtrosForm!: FormGroup;

  estaAutenticado = false;
  isLoadingTurnos = false;
  errorCarga = "";
  avisoBusqueda = "";
  busquedaHecha = false;
  isLoadingEspecialidades = false;
  isLoadingCentros = false;

  especialidadesCompletas: Especialidad[] = [];
  especialidades: Especialidad[] = [];
  staffMedicosCompletos: StaffMedico[] = [];
  staffMedicos: StaffMedico[] = [];
  centrosAtencionCompletos: CentroAtencion[] = [];
  centrosAtencion: CentroAtencion[] = [];
  medicosCatalogo: Medico[] = [];
  catalogo: OpcionCatalogo[] = [];

  textoBusqueda = "";
  textoCliente = "";
  sugerencias: OpcionCatalogo[] = [];
  sugerenciasAbiertas = false;
  masFiltrosAbierto = false;
  medicoFiltroClave = "";

  slotsOriginales: SlotDisponible[] = [];
  slotsDisponibles: SlotDisponible[] = [];
  turnosDisponibles: SlotDisponible[] = [];
  slotsPorFecha: { [fecha: string]: SlotDisponible[] } = {};
  fechasOrdenadas: string[] = [];
  semanas = 4;

  showBookingModal = false;
  slotSeleccionado: SlotDisponible | null = null;
  mensajeReservaBloqueada = "";
  selectedTurnoDisponible: any = null;
  isBooking = false;
  showMapaModal = false;
  mostrarListaEspera = false;

  medicosSlotsAgrupados: Map<string, SlotDisponible[]> = new Map();
  private metaMedicos = new Map<string, { id: number; nombre: string; apellido: string }>();
  medicosExpandidos: Set<string> = new Set();
  diasExpandidos: Map<string, Set<string>> = new Map();

  paginaActual = 1;
  medicosPorPagina = 5;
  medicosPaginadosCache: Array<{ key: string; value: SlotDisponible[] }> = [];
  totalPaginasCache = 0;
  rangoPaginacionCache = "";

  modalPosition = { top: 0, left: 0 };
  private resizeListener?: () => void;
  private readonly disparar$ = new Subject<void>();
  private readonly pedidos$ = new Subject<void>();
  private origenTexto = false;
  private aplicandoTexto = false;
  private silencio = false;
  private fotoFiltros: FiltrosAgenda = { centro: null, especialidad: "", medico: null, preferencia: false };
  private puestoPorTexto: { especialidad?: string; staffMedicoId?: number | null } = {};
  private ultimaFirmaCargada = "";
  private cargaVigente = false;

  constructor(
    private fb: FormBuilder,
    private turnoService: TurnoService,
    private especialidadService: EspecialidadService,
    private centroAtencionService: CentroAtencionService,
    private medicoService: MedicoService,
    private agendaService: AgendaService,
    private diasExcepcionalesService: DiasExcepcionalesService,
    private deepLinkService: DeepLinkService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
    private authService: AuthService,
    private userContextService: UserContextService,
    private toastService: ToastService,
    private modalService: ModalService,
    private saasService: SaasService,
    private sesion: PacienteSesionService
  ) {}

  get especialidadSeleccionada(): string {
    return this.filtrosForm?.get("especialidad")?.value || "";
  }

  get staffMedicoSeleccionado(): number | null {
    const valor = this.filtrosForm?.get("medico")?.value;
    return this.comoId(valor);
  }

  get centroAtencionSeleccionado(): number | null {
    return this.comoId(this.filtrosForm?.get("centroAtencion")?.value);
  }

  get prefiereHorarios(): boolean {
    return !!this.filtrosForm?.get("filtrarPorPreferencia")?.value;
  }

  get hayFiltrosActivos(): boolean {
    return !!(
      this.textoBusqueda.trim() ||
      this.centroAtencionSeleccionado ||
      this.especialidadSeleccionada ||
      this.staffMedicoSeleccionado ||
      this.prefiereHorarios
    );
  }

  get medicosParaFiltro(): OpcionCatalogo[] {
    const centro = this.centroAtencionSeleccionado;
    return this.catalogo.filter((op) => {
      if (op.tipo !== "medico") return false;
      if (!centro || !op.centroId) return true;
      return Number(op.centroId) === Number(centro);
    });
  }

  /**
   * Operador (y quien hereda ese rol, como administración) no usa la lista de espera.
   * Un médico que entra a Pedir turno recorre la misma pantalla que un paciente.
   */
  get esOperador(): boolean {
    return this.userContextService.hasAnyRole([Role.OPERADOR]);
  }

  ngOnInit() {
    this.filtrosForm = this.fb.group({
      centroAtencion: [null],
      especialidad: [""],
      medico: [null],
      filtrarPorPreferencia: [false],
    });

    this.disparar$
      .pipe(debounceTime(this.demoraBusquedaMs), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.origenTexto) {
          this.origenTexto = false;
          this.aplicarTextoEnFormulario();
        }
        this.lanzarCarga();
      });

    this.pedidos$
      .pipe(
        switchMap(() => this.pedirTurnos$()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();

    this.filtrosForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.silencio || this.aplicandoTexto) return;
      this.alCambiarFiltroManual();
      this.programar(false);
    });

    this.estaAutenticado = this.authService.isAuthenticated();
    this.leerParametrosIniciales();
    this.cargarEspecialidades();
    this.cargarCentrosAtencion();
    this.cargarMedicosCatalogo();
    this.aplicarContextoDeepLink();

    this.resizeListener = () => {
      if (this.showBookingModal) {
        this.modalPosition = {
          top: window.innerWidth <= 768 ? window.innerHeight / 2 - 200 : (window.innerHeight - 400) / 2,
          left: window.innerWidth <= 768 ? window.innerWidth / 2 - 200 : (window.innerWidth - 500) / 2,
        };
      }
    };
    window.addEventListener("resize", this.resizeListener);
  }

  ngOnDestroy() {
    if (this.resizeListener) {
      window.removeEventListener("resize", this.resizeListener);
    }
  }

  onTextoBusqueda(valor: string) {
    this.textoBusqueda = valor ?? "";
    this.sugerencias = sugerenciasCatalogo(this.textoBusqueda, this.catalogo);
    this.sugerenciasAbiertas = this.sugerencias.length > 0;
    this.programar(true);
  }

  onTextoBlur() {
    setTimeout(() => {
      this.sugerenciasAbiertas = false;
      this.cdr.markForCheck();
    }, 160);
  }

  onTextoKeydown(event: KeyboardEvent) {
    if (event.key === "Escape") this.sugerenciasAbiertas = false;
  }

  elegirSugerencia(op: OpcionCatalogo) {
    this.textoBusqueda = op.etiqueta;
    this.sugerencias = [];
    this.sugerenciasAbiertas = false;
    this.origenTexto = false;
    this.aplicarTextoEnFormulario();
    this.lanzarCarga();
  }

  elegirMedico(clave: string) {
    this.medicoFiltroClave = clave || "";
    if (!clave) {
      this.filtrosForm.patchValue({ medico: null });
      return;
    }
    if (clave.startsWith("staff:")) {
      this.filtrosForm.patchValue({ medico: Number(clave.slice(6)) });
      return;
    }
    const op = this.catalogo.find((item) => item.clave === clave);
    if (!op) return;
    this.textoBusqueda = op.etiqueta;
    this.sugerenciasAbiertas = false;
    this.origenTexto = false;
    this.aplicarTextoEnFormulario();
    this.lanzarCarga();
  }

  alternarHorarios() {
    this.filtrosForm.patchValue({ filtrarPorPreferencia: !this.prefiereHorarios });
  }

  reintentarCarga() {
    this.ultimaFirmaCargada = "";
    this.cargaVigente = false;
    this.lanzarCarga();
  }

  limpiarTodosFiltros() {
    this.silencio = true;
    this.filtrosForm.reset(
      { centroAtencion: null, especialidad: "", medico: null, filtrarPorPreferencia: false },
      { emitEvent: false }
    );
    this.silencio = false;
    this.textoBusqueda = "";
    this.textoCliente = "";
    this.sugerencias = [];
    this.sugerenciasAbiertas = false;
    this.medicoFiltroClave = "";
    this.puestoPorTexto = {};
    this.avisoBusqueda = "";
    this.errorCarga = "";
    this.busquedaHecha = false;
    this.cargaVigente = false;
    this.ultimaFirmaCargada = "";
    this.slotsOriginales = [];
    this.publicarSlots([]);
    this.fotoFiltros = this.leerFiltros();
    this.sincronizarUrl();
    this.cdr.detectChanges();
  }

  goBack() {
    this.router.navigate(["/paciente-dashboard"]);
  }

  abrirListaEspera(): void {
    this.mostrarListaEspera = true;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { espera: "1" },
      queryParamsHandling: "merge",
    });
  }

  mostrarMapaCentros() {
    this.showMapaModal = true;
    this.cdr.detectChanges();
  }

  cerrarMapaModal() {
    this.showMapaModal = false;
    this.cdr.detectChanges();
  }

  onCentroSeleccionadoDelMapa(centro: CentroAtencion) {
    if (!centro?.id) return;
    this.filtrosForm.patchValue({ centroAtencion: centro.id });
    this.cerrarMapaModal();
    this.toastService.success(`Buscando turnos en "${centro.nombre}".`);
  }

  seleccionarTurno(turno: { id?: number } | null) {
    if (turno?.id) {
      localStorage.setItem("turnoSeleccionadoId", turno.id.toString());
    }
    this.router.navigate(["/ingresar"], {
      queryParams: { returnUrl: "/paciente-agenda" },
    });
  }

  seleccionarSlot(slot: SlotDisponible, event?: MouseEvent) {
    if (slot.ocupado) {
      this.toastService.error("Este turno ya está ocupado. Por favor, selecciona otro horario disponible.");
      return;
    }

    if (this.slotAfectadoPorExcepcion(slot)) {
      const excepcionesDelDia = this.diasExcepcionalesService.getExcepcionesDelDia(slot.fecha);
      const excepcionAfectante = excepcionesDelDia?.find((exc) => {
        if (exc.tipo === "FERIADO") return true;
        if ((exc.tipo === "MANTENIMIENTO" || exc.tipo === "ATENCION_ESPECIAL") && exc.horaInicio && exc.horaFin) {
          const inicioSlot = this.convertirHoraAMinutos(slot.horaInicio);
          const finSlot = this.convertirHoraAMinutos(slot.horaFin);
          const inicioExc = this.convertirHoraAMinutos(exc.horaInicio);
          const finExc = this.convertirHoraAMinutos(exc.horaFin);
          return inicioSlot < finExc && finSlot > inicioExc;
        }
        return false;
      });

      if (excepcionAfectante) {
        const tipoLabel =
          excepcionAfectante.tipo === "FERIADO"
            ? "Feriado"
            : excepcionAfectante.tipo === "MANTENIMIENTO"
              ? "Mantenimiento"
              : "Atención Especial";
        this.toastService.warning(`Este horario no está disponible por ${tipoLabel}. Por favor, selecciona otro horario.`);
      } else {
        this.toastService.warning("Este horario no está disponible. Por favor, selecciona otro horario.");
      }
      return;
    }

    if (event) this.calculateModalPosition(event);

    this.slotSeleccionado = slot;
    this.selectedTurnoDisponible = {
      start: new Date(`${slot.fecha}T${slot.horaInicio}`),
      end: new Date(`${slot.fecha}T${slot.horaFin}`),
      meta: {
        id: slot.id,
        especialidad: slot.especialidadStaffMedico,
        medico: `${slot.staffMedicoNombre} ${slot.staffMedicoApellido}`,
        centro: slot.nombreCentro,
        consultorio: slot.consultorioNombre,
        staffMedicoId: slot.staffMedicoId,
        staffMedicoNombre: slot.staffMedicoNombre,
        staffMedicoApellido: slot.staffMedicoApellido,
        especialidadStaffMedico: slot.especialidadStaffMedico,
        consultorioId: slot.consultorioId,
        consultorioNombre: slot.consultorioNombre,
        centroId: slot.centroId,
        centroAtencionNombre: slot.nombreCentro,
      },
    };

    this.mensajeReservaBloqueada = "";
    this.showBookingModal = true;
    if (this.estaAutenticado && slot.centroId) {
      const centroId = slot.centroId;
      this.saasService.aviso(centroId).subscribe({
        next: (resp) => {
          if (this.slotSeleccionado?.centroId !== centroId) return;
          if (resp.status_code === 200 && resp.data?.enforcementEnabled && resp.data.soloLectura) {
            this.mensajeReservaBloqueada =
              resp.data.mensaje || "Este centro no está tomando turnos. Podés ver lo que ya tenías, pero no reservar.";
            this.cdr.markForCheck();
          }
        },
      });
    }
  }

  confirmarReservaTurno() {
    if (!this.selectedTurnoDisponible || !this.slotSeleccionado) return;

    this.sesion.id().subscribe({
      next: (pacienteId) => {
        if (!pacienteId) {
          this.modalService.alert(
            "Error de Autenticación",
            "No encontramos tu ficha de paciente. Si recién la creaste, recargá la página."
          );
          return;
        }
        this.reservarTurno(pacienteId);
      },
      error: (error) => {
        this.modalService.alert(
          "Error de Autenticación",
          mensajeDeCarga(error, "No se pudo leer tu ficha de paciente. Reintentá.")
        );
      },
    });
  }

  closeBookingModal() {
    this.showBookingModal = false;
    this.selectedTurnoDisponible = null;
    this.slotSeleccionado = null;
    this.mensajeReservaBloqueada = "";
    this.isBooking = false;
  }

  formatearHora(hora: string): string {
    return (hora || "").slice(0, 5);
  }

  tituloSlot(slot: SlotDisponible): string {
    const estado = slot.ocupado || this.slotAfectadoPorExcepcion(slot) ? "No disponible" : "Disponible";
    return `${this.formatearHora(slot.horaInicio)} a ${this.formatearHora(slot.horaFin)} · ${slot.consultorioNombre || "Consultorio"} · ${slot.nombreCentro || "Centro"} · ${estado}`;
  }

  slotAfectadoPorExcepcion(slot: SlotDisponible): boolean {
    return this.diasExcepcionalesService.slotAfectadoPorExcepcion(slot);
  }

  toggleMedicoExpansion(medicoKey: string) {
    if (this.medicosExpandidos.has(medicoKey)) this.medicosExpandidos.delete(medicoKey);
    else this.medicosExpandidos.add(medicoKey);
  }

  isMedicoExpandido(medicoKey: string): boolean {
    return this.medicosExpandidos.has(medicoKey);
  }

  getMedicoInfo(medicoKey: string): { nombre: string; apellido: string; id: number } {
    return this.metaMedicos.get(medicoKey) ?? { id: 0, nombre: "", apellido: "" };
  }

  getTurnosDisponiblesPorMedico(slots: SlotDisponible[]): number {
    return slots.filter((slot) => !slot.ocupado && !this.slotAfectadoPorExcepcion(slot)).length;
  }

  getCentrosUnicos(slots: SlotDisponible[]): string[] {
    return Array.from(new Set(slots.map((slot) => slot.nombreCentro).filter(Boolean)));
  }

  getEspecialidadesUnicas(slots: SlotDisponible[]): string[] {
    return Array.from(new Set(slots.map((slot) => slot.especialidadStaffMedico).filter(Boolean)));
  }

  textoCentros(slots: SlotDisponible[]): string {
    const nombres = this.getCentrosUnicos(slots);
    if (nombres.length <= 2) return nombres.join(" · ");
    return `${nombres.slice(0, 2).join(" · ")} +${nombres.length - 2}`;
  }

  getFechasOrdenadas(slots: SlotDisponible[]): string[] {
    return Array.from(new Set(slots.map((slot) => slot.fecha))).sort();
  }

  toggleDiaExpansion(medicoKey: string, fecha: string) {
    if (!this.diasExpandidos.has(medicoKey)) this.diasExpandidos.set(medicoKey, new Set());
    const diasSet = this.diasExpandidos.get(medicoKey)!;
    if (diasSet.has(fecha)) diasSet.delete(fecha);
    else diasSet.add(fecha);
  }

  isDiaExpandido(medicoKey: string, fecha: string): boolean {
    return !!this.diasExpandidos.get(medicoKey)?.has(fecha);
  }

  getSlotsPorDia(slots: SlotDisponible[], fecha: string): SlotDisponible[] {
    return slots.filter((slot) => slot.fecha === fecha).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
  }

  getTurnosDisponiblesPorDia(slots: SlotDisponible[]): number {
    return slots.filter((slot) => !slot.ocupado && !this.slotAfectadoPorExcepcion(slot)).length;
  }

  getTotalPaginas(): number {
    return this.totalPaginasCache;
  }

  getMedicosPaginados(): Array<{ key: string; value: SlotDisponible[] }> {
    return this.medicosPaginadosCache;
  }

  getRangoPaginacion(): string {
    return this.rangoPaginacionCache;
  }

  getNumeroPaginas(): number[] {
    return Array.from({ length: this.getTotalPaginas() }, (_, i) => i + 1);
  }

  cambiarPagina(nuevaPagina: number) {
    if (nuevaPagina < 1 || nuevaPagina > this.totalPaginasCache) return;
    this.paginaActual = nuevaPagina;
    this.actualizarCachePaginacion();
    setTimeout(() => {
      document.querySelector(".medicos-lista")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 100);
  }

  getStaffMedicoNombre(id: number | null): string {
    if (!id) return "";
    const staff = this.staffMedicosCompletos.find((item) => Number(item.id) === Number(id));
    if (staff?.medico) return `${staff.medico.nombre} ${staff.medico.apellido}`.trim();
    const slot = this.slotsOriginales.find((item) => Number(item.staffMedicoId) === Number(id));
    if (slot) return `${slot.staffMedicoNombre} ${slot.staffMedicoApellido}`.trim();
    return "";
  }

  getCentroAtencionNombre(id: number | null): string {
    if (!id) return "";
    return this.centrosAtencion.find((centro) => Number(centro.id) === Number(id))?.nombre || "";
  }

  private programar(desdeTexto: boolean) {
    this.origenTexto = desdeTexto;
    this.disparar$.next();
  }

  private lanzarCarga() {
    this.pedidos$.next();
  }

  private leerParametrosIniciales() {
    const params = this.route.snapshot.queryParamMap;
    this.mostrarListaEspera = params.get("espera") === "1";
    const centro = numeroParam(params.get("centroId"));
    const medico = numeroParam(params.get("staffMedicoId"));
    const especialidad = (params.get("especialidad") || "").trim();
    if (!centro && !medico && !especialidad) {
      this.fotoFiltros = this.leerFiltros();
      return;
    }
    this.silencio = true;
    this.filtrosForm.patchValue(
      {
        centroAtencion: centro,
        especialidad,
        medico,
      },
      { emitEvent: false }
    );
    this.medicoFiltroClave = medico ? `staff:${medico}` : "";
    this.silencio = false;
    this.fotoFiltros = this.leerFiltros();
    this.lanzarCarga();
  }

  private alCambiarFiltroManual() {
    const ahora = this.leerFiltros();
    const cambioEsp = ahora.especialidad !== this.fotoFiltros.especialidad;
    const cambioMed = ahora.medico !== this.fotoFiltros.medico;
    if (cambioEsp || cambioMed) {
      this.textoBusqueda = "";
      this.textoCliente = "";
      this.sugerencias = [];
      this.sugerenciasAbiertas = false;
      this.puestoPorTexto = {};
      this.medicoFiltroClave = ahora.medico ? `staff:${ahora.medico}` : "";
    } else {
      this.textoCliente = this.textoClienteResidual();
    }
    this.fotoFiltros = this.leerFiltros();
    this.refrescarMedicosVisibles();
  }

  private aplicarTextoEnFormulario() {
    this.aplicandoTexto = true;
    try {
      this.limpiarFiltrosDeTexto();
      const res = resolverBusquedaTexto(this.textoBusqueda, this.catalogo);
      this.sugerencias = sugerenciasCatalogo(this.textoBusqueda, this.catalogo);
      this.aplicarResolucion(res);
    } finally {
      this.aplicandoTexto = false;
      this.fotoFiltros = this.leerFiltros();
      this.refrescarMedicosVisibles();
    }
  }

  private aplicarResolucion(res: ResolucionBusqueda) {
    if (res.modo === "vacio") {
      this.textoCliente = "";
      this.avisoBusqueda = "";
      return;
    }
    if (res.modo === "catalogo" && res.especialidad && !res.staffMedicoId && !res.textoCliente) {
      this.filtrosForm.patchValue({ especialidad: res.especialidad, medico: null }, { emitEvent: false });
      this.medicoFiltroClave = "";
      this.puestoPorTexto = { especialidad: res.especialidad };
      this.textoCliente = "";
      this.avisoBusqueda = "";
      return;
    }
    if (res.modo === "catalogo" && res.staffMedicoId) {
      this.filtrosForm.patchValue({ medico: res.staffMedicoId }, { emitEvent: false });
      this.medicoFiltroClave = `staff:${res.staffMedicoId}`;
      this.puestoPorTexto = { staffMedicoId: res.staffMedicoId };
      this.textoCliente = "";
      this.avisoBusqueda = "";
      return;
    }
    if (res.modo === "catalogo" && res.textoCliente) {
      const patch: { medico: null; especialidad?: string } = { medico: null };
      const puesto: { especialidad?: string } = {};
      if (res.especialidad && !this.especialidadSeleccionada) {
        patch.especialidad = res.especialidad;
        puesto.especialidad = res.especialidad;
      }
      this.filtrosForm.patchValue(patch, { emitEvent: false });
      const op = this.catalogo.find(
        (item) => item.tipo === "medico" && !item.staffMedicoId && normalizarBusqueda(item.etiqueta) === res.textoCliente
      );
      this.medicoFiltroClave = op?.clave || "";
      this.puestoPorTexto = puesto;
      this.textoCliente = res.textoCliente;
      this.avisoBusqueda = "";
      return;
    }
    this.textoCliente = res.textoCliente || normalizarBusqueda(this.textoBusqueda);
    this.avisoBusqueda = "";
  }

  private limpiarFiltrosDeTexto() {
    const patch: { especialidad?: string; medico?: null } = {};
    if (this.puestoPorTexto.especialidad && this.especialidadSeleccionada === this.puestoPorTexto.especialidad) {
      patch.especialidad = "";
    }
    if (this.puestoPorTexto.staffMedicoId && this.staffMedicoSeleccionado === this.puestoPorTexto.staffMedicoId) {
      patch.medico = null;
      this.medicoFiltroClave = "";
    }
    this.puestoPorTexto = {};
    if (Object.keys(patch).length) this.filtrosForm.patchValue(patch, { emitEvent: false });
  }

  private textoClienteResidual(): string {
    const q = normalizarBusqueda(this.textoBusqueda);
    if (!q) return "";
    if (this.especialidadSeleccionada && normalizarBusqueda(this.especialidadSeleccionada) === q) return "";
    const nombre = normalizarBusqueda(this.getStaffMedicoNombre(this.staffMedicoSeleccionado));
    if (nombre && nombre === q) return "";
    const res = resolverBusquedaTexto(this.textoBusqueda, this.catalogo);
    if (res.modo === "catalogo" && !res.textoCliente) return "";
    return res.textoCliente || q;
  }

  private pedirTurnos$(): Observable<unknown> {
    this.sincronizarUrl();
    if (!this.hayFiltroServidor()) {
      this.resolverSinServidor();
      return EMPTY;
    }

    const firma = this.firmaActual();
    if (firma === this.ultimaFirmaCargada && this.cargaVigente) {
      this.publicarSlots(this.filtrarCliente(this.slotsOriginales));
      this.busquedaHecha = true;
      this.isLoadingTurnos = false;
      this.errorCarga = "";
      this.avisoBusqueda = "";
      this.cdr.detectChanges();
      return EMPTY;
    }

    this.isLoadingTurnos = true;
    this.errorCarga = "";
    this.avisoBusqueda = "";
    this.cdr.detectChanges();

    return this.fuente().pipe(
      timeout(20000),
      tap((respuesta) => this.procesarEventosRecibidos(this.normalizarEventos(respuesta), firma)),
      catchError((err) => {
        this.manejarErrorCargaTurnos(err);
        return EMPTY;
      })
    );
  }

  private fuente(): Observable<unknown> {
    const filtros = this.leerFiltros();
    if (!this.estaAutenticado) {
      return this.agendaService.getAgendaPublica(
        filtros.centro || undefined,
        filtros.especialidad || undefined,
        filtros.medico || undefined
      );
    }
    return this.agendaService.obtenerTodosLosEventos(this.semanas, {
      centroId: filtros.centro || undefined,
      especialidad: filtros.especialidad || undefined,
      staffMedicoId: filtros.medico || undefined,
      filtrarPorPreferencia: filtros.preferencia ? true : undefined,
    });
  }

  private resolverSinServidor() {
    this.isLoadingTurnos = false;
    this.errorCarga = "";
    this.ultimaFirmaCargada = "";
    this.cargaVigente = false;
    if (this.textoCliente && this.slotsOriginales.length) {
      this.publicarSlots(this.filtrarCliente(this.slotsOriginales));
      this.busquedaHecha = true;
      this.avisoBusqueda = "";
    } else {
      this.slotsOriginales = this.textoCliente ? this.slotsOriginales : [];
      this.publicarSlots([]);
      this.busquedaHecha = false;
      const escrito = this.textoBusqueda.trim();
      if (!escrito) this.avisoBusqueda = "";
      else if (resolverBusquedaTexto(escrito, this.catalogo).modo === "ambiguo") {
        this.avisoBusqueda = "Hay varias coincidencias. Elegí una de la lista.";
      } else {
        this.avisoBusqueda = `«${escrito}» no está en el listado. Elegí una sugerencia o un centro.`;
      }
    }
    this.cdr.detectChanges();
  }

  private procesarEventosRecibidos(eventos: any[], firma: string) {
    this.slotsOriginales = this.mapEventosToSlots(eventos);
    this.incorporarMedicosDeSlots(this.slotsOriginales);
    this.ultimaFirmaCargada = firma;
    this.cargaVigente = true;
    this.publicarSlots(this.filtrarCliente(this.slotsOriginales));
    this.busquedaHecha = true;
    this.isLoadingTurnos = false;
    this.errorCarga = "";
    this.cdr.detectChanges();
  }

  private manejarErrorCargaTurnos(error?: unknown) {
    this.isLoadingTurnos = false;
    this.cargaVigente = false;
    this.slotsOriginales = [];
    this.publicarSlots([]);
    this.busquedaHecha = false;
    this.errorCarga = mensajeDeCarga(error, "No se pudieron cargar los turnos disponibles. Reintentá.");
    this.cdr.detectChanges();
  }

  private filtrarCliente(slots: SlotDisponible[]): SlotDisponible[] {
    const q = this.textoCliente;
    if (!q) return [...slots];
    return slots.filter((slot) => {
      const nombre = normalizarBusqueda(`${slot.staffMedicoNombre || ""} ${slot.staffMedicoApellido || ""}`);
      const especialidad = normalizarBusqueda(slot.especialidadStaffMedico || "");
      return nombre.includes(q) || especialidad.includes(q);
    });
  }

  private publicarSlots(slots: SlotDisponible[]) {
    this.slotsDisponibles = slots;
    this.turnosDisponibles = slots;
    this.agruparSlotsPorFecha();
  }

  private hayFiltroServidor(): boolean {
    const filtros = this.leerFiltros();
    return !!(filtros.centro || filtros.especialidad || filtros.medico || (filtros.preferencia && this.estaAutenticado));
  }

  private firmaActual(): string {
    const filtros = this.leerFiltros();
    return JSON.stringify({
      c: filtros.centro,
      e: filtros.especialidad,
      m: filtros.medico,
      p: !!(filtros.preferencia && this.estaAutenticado),
      a: this.estaAutenticado,
    });
  }

  private leerFiltros(): FiltrosAgenda {
    const valor = this.filtrosForm?.getRawValue() ?? {};
    return {
      centro: this.comoId(valor.centroAtencion),
      especialidad: String(valor.especialidad || "").trim(),
      medico: this.comoId(valor.medico),
      preferencia: !!valor.filtrarPorPreferencia,
    };
  }

  private comoId(valor: unknown): number | null {
    if (valor === null || valor === undefined || valor === "" || valor === "null") return null;
    const n = Number(valor);
    return Number.isFinite(n) && n > 0 ? n : null;
  }

  private sincronizarUrl() {
    const filtros = this.leerFiltros();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        centroId: filtros.centro,
        especialidad: filtros.especialidad || null,
        staffMedicoId: filtros.medico,
        espera: this.mostrarListaEspera ? "1" : null,
      },
      queryParamsHandling: "merge",
      replaceUrl: true,
    });
  }

  private cargarEspecialidades() {
    this.isLoadingEspecialidades = true;
    this.especialidadService.all().subscribe({
      next: (dataPackage: DataPackage<Especialidad[]>) => {
        this.especialidadesCompletas = Array.isArray(dataPackage?.data) ? dataPackage.data : [];
        this.especialidades = [...this.especialidadesCompletas];
        this.isLoadingEspecialidades = false;
        this.armarCatalogo(true);
      },
      error: () => {
        this.isLoadingEspecialidades = false;
        this.armarCatalogo(true);
      },
    });
  }

  private cargarCentrosAtencion() {
    this.isLoadingCentros = true;
    this.centroAtencionService.all().subscribe({
      next: (dataPackage: DataPackage<CentroAtencion[]>) => {
        this.centrosAtencionCompletos = Array.isArray(dataPackage?.data) ? dataPackage.data : [];
        this.centrosAtencion = [...this.centrosAtencionCompletos];
        this.isLoadingCentros = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingCentros = false;
      },
    });
  }

  private cargarMedicosCatalogo() {
    this.medicoService.getAll().subscribe({
      next: (dataPackage: DataPackage<Medico[]>) => {
        this.medicosCatalogo = Array.isArray(dataPackage?.data) ? dataPackage.data : [];
        this.armarCatalogo(true);
      },
      error: () => {
        this.medicosCatalogo = [];
        this.armarCatalogo(true);
      },
    });
  }

  private armarCatalogo(buscarDeNuevo = false) {
    const opciones: OpcionCatalogo[] = [];
    for (const especialidad of this.especialidadesCompletas) {
      if (!especialidad?.nombre) continue;
      opciones.push({
        tipo: "especialidad",
        etiqueta: especialidad.nombre,
        especialidad: especialidad.nombre,
        clave: `esp:${especialidad.id || especialidad.nombre}`,
      });
    }

    const nombresConStaff = new Set<string>();
    for (const staff of this.staffMedicosCompletos) {
      const etiqueta = `${staff.medico?.nombre || ""} ${staff.medico?.apellido || ""}`.trim();
      if (!etiqueta || !staff.id) continue;
      nombresConStaff.add(normalizarBusqueda(etiqueta));
      opciones.push({
        tipo: "medico",
        etiqueta,
        especialidad: staff.especialidad?.nombre,
        staffMedicoId: staff.id,
        medicoId: staff.medico?.id ?? null,
        centroId: staff.centro?.id ?? null,
        clave: `staff:${staff.id}`,
      });
    }

    for (const medico of this.medicosCatalogo) {
      const etiqueta = `${medico.nombre || ""} ${medico.apellido || ""}`.trim();
      if (!etiqueta || nombresConStaff.has(normalizarBusqueda(etiqueta))) continue;
      const especialidad = medico.especialidades?.[0]?.nombre || medico.especialidad?.nombre;
      opciones.push({
        tipo: "medico",
        etiqueta,
        especialidad,
        medicoId: medico.id,
        clave: `medico:${medico.id}`,
      });
    }

    this.catalogo = opciones;
    this.refrescarMedicosVisibles();
    if (this.textoBusqueda.trim()) {
      this.sugerencias = sugerenciasCatalogo(this.textoBusqueda, this.catalogo);
      if (buscarDeNuevo) this.programar(true);
    }
  }

  private refrescarMedicosVisibles() {
    const centro = this.centroAtencionSeleccionado;
    const especialidad = this.especialidadSeleccionada;
    this.staffMedicos = this.staffMedicosCompletos.filter((staff) => {
      if (centro && staff.centro?.id && Number(staff.centro.id) !== Number(centro)) return false;
      if (especialidad && staff.especialidad?.nombre && staff.especialidad.nombre !== especialidad) return false;
      return true;
    });
  }

  private incorporarMedicosDeSlots(slots: SlotDisponible[]) {
    const porId = new Map(this.staffMedicosCompletos.map((staff) => [staff.id, staff]));
    for (const slot of slots) {
      if (!slot.staffMedicoId || porId.has(slot.staffMedicoId)) continue;
      porId.set(slot.staffMedicoId, {
        id: slot.staffMedicoId,
        medico: {
          id: 0,
          nombre: slot.staffMedicoNombre || "",
          apellido: slot.staffMedicoApellido || "",
          dni: "",
          matricula: "",
        },
        centro: slot.centroId ? { id: slot.centroId, nombre: slot.nombreCentro || "Sin centro" } : undefined,
        especialidad: slot.especialidadStaffMedico ? { id: 0, nombre: slot.especialidadStaffMedico } : undefined,
      });
    }
    this.staffMedicosCompletos = Array.from(porId.values());
    this.armarCatalogo();
  }

  private normalizarEventos(respuesta: unknown): any[] {
    if (Array.isArray(respuesta)) return respuesta;
    const data = (respuesta as { data?: unknown })?.data;
    return Array.isArray(data) ? data : [];
  }

  private mapEventosToSlots(eventosBackend: any[]): SlotDisponible[] {
    const slots: SlotDisponible[] = [];
    eventosBackend.forEach((evento) => {
      if (!evento?.fecha || !evento.horaInicio || !evento.horaFin || !evento.esSlot) return;
      slots.push({
        id: evento.id,
        fecha: evento.fecha,
        horaInicio: evento.horaInicio,
        horaFin: evento.horaFin,
        staffMedicoId: evento.staffMedicoId,
        staffMedicoNombre: evento.staffMedicoNombre,
        staffMedicoApellido: evento.staffMedicoApellido,
        especialidadStaffMedico: evento.especialidadStaffMedico,
        consultorioId: evento.consultorioId,
        consultorioNombre: evento.consultorioNombre,
        centroId: evento.centroId,
        nombreCentro: evento.nombreCentro,
        ocupado: evento.ocupado || false,
        esSlot: true,
      });
    });
    return slots;
  }

  private agruparSlotsPorFecha() {
    this.slotsPorFecha = {};
    this.medicosSlotsAgrupados = new Map();
    this.metaMedicos = new Map();
    this.paginaActual = 1;

    this.slotsDisponibles.forEach((slot) => {
      if (!this.slotsPorFecha[slot.fecha]) this.slotsPorFecha[slot.fecha] = [];
      this.slotsPorFecha[slot.fecha].push(slot);
      const medicoKey = String(slot.staffMedicoId);
      if (!this.medicosSlotsAgrupados.has(medicoKey)) {
        this.medicosSlotsAgrupados.set(medicoKey, []);
        this.metaMedicos.set(medicoKey, {
          id: slot.staffMedicoId,
          nombre: slot.staffMedicoNombre || "",
          apellido: slot.staffMedicoApellido || "",
        });
      }
      this.medicosSlotsAgrupados.get(medicoKey)!.push(slot);
    });

    this.fechasOrdenadas = Object.keys(this.slotsPorFecha).sort();
    this.medicosSlotsAgrupados.forEach((slots) => {
      slots.sort((a, b) => (a.fecha === b.fecha ? a.horaInicio.localeCompare(b.horaInicio) : a.fecha.localeCompare(b.fecha)));
    });
    this.actualizarCachePaginacion();
  }

  private actualizarCachePaginacion() {
    const totalMedicos = this.medicosSlotsAgrupados.size;
    this.totalPaginasCache = Math.ceil(totalMedicos / this.medicosPorPagina);
    const todos = Array.from(this.medicosSlotsAgrupados.entries())
      .map(([key, value]) => ({ key, value }))
      .sort((a, b) => {
        const izq = this.getMedicoInfo(a.key);
        const der = this.getMedicoInfo(b.key);
        return `${izq.apellido} ${izq.nombre}`.localeCompare(`${der.apellido} ${der.nombre}`, "es");
      });
    const inicio = (this.paginaActual - 1) * this.medicosPorPagina;
    this.medicosPaginadosCache = todos.slice(inicio, inicio + this.medicosPorPagina);
    const inicioRango = totalMedicos === 0 ? 0 : inicio + 1;
    const finRango = Math.min(this.paginaActual * this.medicosPorPagina, totalMedicos);
    this.rangoPaginacionCache = `${inicioRango}-${finRango} de ${totalMedicos}`;
  }

  private calculateModalPosition(event: MouseEvent) {
    if (window.innerWidth <= 768) {
      this.modalPosition = { top: window.innerHeight / 2 - 200, left: window.innerWidth / 2 - 200 };
      return;
    }
    const target = event.target as HTMLElement;
    const slotCard = target.closest(".slot-chip") as HTMLElement;
    if (!slotCard) {
      this.modalPosition = { top: (window.innerHeight - 400) / 2, left: (window.innerWidth - 500) / 2 };
      return;
    }
    const rect = slotCard.getBoundingClientRect();
    const modalWidth = 500;
    const modalHeight = 400;
    const offset = 10;
    let left = rect.right + offset;
    let top = rect.top;
    if (left + modalWidth > window.innerWidth) left = rect.left - modalWidth - offset;
    if (left < 0) left = (window.innerWidth - modalWidth) / 2;
    if (top + modalHeight > window.innerHeight) top = window.innerHeight - modalHeight - offset;
    if (top < 0) top = offset;
    this.modalPosition = { top, left };
  }

  private convertirHoraAMinutos(hora: string): number {
    const [horas, minutos] = hora.split(":").map(Number);
    return horas * 60 + minutos;
  }

  private actualizarSlotReservado(slotId: number) {
    const slot = this.slotsDisponibles.find((item) => item.id === slotId);
    if (!slot) return;
    slot.ocupado = true;
    this.agruparSlotsPorFecha();
    this.cdr.detectChanges();
  }

  private reservarTurno(pacienteId: number) {
    if (!this.slotSeleccionado) return;
    this.isBooking = true;
    const turnoDTO = {
      id: this.slotSeleccionado.id,
      fecha: this.slotSeleccionado.fecha,
      horaInicio: this.slotSeleccionado.horaInicio,
      horaFin: this.slotSeleccionado.horaFin,
      pacienteId,
      staffMedicoId: this.slotSeleccionado.staffMedicoId,
      staffMedicoNombre: this.slotSeleccionado.staffMedicoNombre,
      staffMedicoApellido: this.slotSeleccionado.staffMedicoApellido,
      especialidadStaffMedico: this.slotSeleccionado.especialidadStaffMedico,
      consultorioId: this.slotSeleccionado.consultorioId,
      consultorioNombre: this.slotSeleccionado.consultorioNombre,
      centroId: this.slotSeleccionado.centroId,
      nombreCentro: this.slotSeleccionado.nombreCentro,
      estado: "PROGRAMADO",
    };

    this.turnoService.asignarTurno(turnoDTO).subscribe({
      next: () => {
        this.modalService.alert("¡Reserva Exitosa!", "¡Turno reservado exitosamente!");
        this.actualizarSlotReservado(this.slotSeleccionado!.id);
        this.closeBookingModal();
        setTimeout(() => {
          this.ultimaFirmaCargada = "";
          this.cargaVigente = false;
          this.lanzarCarga();
        }, 500);
      },
      error: (err: any) => {
        const errorMessage = err?.error?.status_text || "No se pudo reservar el turno. Intente nuevamente.";
        this.modalService.alert("Error al Reservar", errorMessage);
        this.isBooking = false;
      },
    });
  }

  private aplicarContextoDeepLink(): void {
    const turnoConfirmadoStr = sessionStorage.getItem("turno_confirmado");
    if (turnoConfirmadoStr) {
      try {
        const turnoConfirmado = JSON.parse(turnoConfirmadoStr);
        this.mostrarModalConfirmacionExitosa(turnoConfirmado.mensaje);
        sessionStorage.removeItem("turno_confirmado");
      } catch {
        sessionStorage.removeItem("turno_confirmado");
      }
    }

    const context = this.deepLinkService.getContext();
    if (!context) return;
    this.deepLinkService.clearContext();
  }

  private mostrarModalConfirmacionExitosa(mensaje: string): void {
    const modalHtml = `
      <div class="modal fade" id="confirmacionExitosaModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header bg-success text-white">
              <h5 class="modal-title">
                <i class="fas fa-check-circle me-2"></i>
                ¡Turno Confirmado!
              </h5>
              <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Cerrar"></button>
            </div>
            <div class="modal-body text-center py-4">
              <i class="fas fa-calendar-check text-success mb-3" style="font-size: 4rem;"></i>
              <p class="fs-5 mb-3">${mensaje}</p>
              <p class="text-muted">Podés seguir buscando un horario en esta página.</p>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-success" data-bs-dismiss="modal">Entendido</button>
            </div>
          </div>
        </div>
      </div>
    `;
    const modalDiv = document.createElement("div");
    modalDiv.innerHTML = modalHtml;
    document.body.appendChild(modalDiv.firstElementChild!);
    const modalElement = document.getElementById("confirmacionExitosaModal");
    if (!modalElement) return;
    // @ts-ignore Bootstrap está disponible en la página
    const modal = new bootstrap.Modal(modalElement);
    modal.show();
    modalElement.addEventListener("hidden.bs.modal", () => modalElement.remove());
  }
}
