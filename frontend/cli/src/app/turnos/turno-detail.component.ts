import { HceService } from '../historia-clinica/hce.service';
import { Component, ChangeDetectionStrategy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { TurnoService } from "./turno.service";
import { Turno, AuditLog } from "./turno";
import { DataPackage } from "../data.package";
import { PacienteService } from "../pacientes/paciente.service";
import { StaffMedicoService } from "../staffMedicos/staffMedico.service";
import { ConsultorioService } from "../consultorios/consultorio.service";
import { Paciente } from "../pacientes/paciente";
import { StaffMedico } from "../staffMedicos/staffMedico";
import { Consultorio } from "../consultorios/consultorio";
import { ModalService } from "../modal/modal.service";
import { AuthService } from "../inicio-sesion/auth.service";
import { MedicoSesionService, mensajeMedico, staffComoLista } from "../medicos/medico-sesion.service";

@Component({
  selector: "app-turno-detail",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./turno-detail.component.html",
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: "./turno-detail.component.css",
})
export class TurnoDetailComponent {
  turno: Turno = {
    id: 0,
    fecha: "",
    horaInicio: "",
    horaFin: "",
    estado: "PROGRAMADO",
    pacienteId: 0,
    staffMedicoId: 0,
    consultorioId: 0,
    observaciones: "",
  };

  modoEdicion = false;
  esSobreturno = false; // Indica si es un sobreturno (creación manual)
  pacientes: Paciente[] = [];
  staffMedicos: StaffMedico[] = [];
  consultorios: Consultorio[] = [];
  errorStaff = "";
  cargandoStaff = false;

  // Advertencias de solapamiento
  advertenciaSolapamiento = false;
  mensajeAdvertencia = "";

  // === PROPIEDADES DE AUDITORÍA ===
  auditHistory: AuditLog[] = [];
  showAuditPanel = false;
  loadingAudit = false;
  auditIntegrityValid = true;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private turnoService: TurnoService,
    private pacienteService: PacienteService,
    private staffMedicoService: StaffMedicoService,
    private consultorioService: ConsultorioService,
    private modalService: ModalService,
    private authService: AuthService,
    private medicoSesion: MedicoSesionService,
    private hceService: HceService
  ) { }

  // Importar lazymente el componente de modal con tabla (evita problemas de bundling)
  // Nota: La importación real se hace dinámicamente en tiempo de ejecución
  private async getConflictModalComponent() {
    const mod = await import('../modal/conflict-modal.component');
    return mod.ConflictModalComponent;
  }

  ngOnInit(): void {
    this.loadDropdownData();
    this.get();
  }

  get(): void {
    const path = this.route.snapshot.routeConfig?.path;

    if (path === "turnos/new") {
      // Nuevo turno - SOBRETURNO
      this.modoEdicion = true;
      this.esSobreturno = true; // Marcar como sobreturno
      this.turno = {
        id: 0,
        fecha: "",
        horaInicio: "",
        horaFin: "",
        estado: "CONFIRMADO", // Sobreturnos se crean directamente como CONFIRMADOS
        pacienteId: 0,
        staffMedicoId: 0,
        consultorioId: 0,
        observaciones: "",
      } as Turno;
      // Desde "Mi semana" un turno libre llega con el horario ya elegido.
      const q = this.route.snapshot.queryParamMap;
      this.turno.fecha = q.get("fecha") || "";
      this.turno.horaInicio = q.get("horaInicio") || "";
      this.turno.horaFin = q.get("horaFin") || "";
      this.turno.staffMedicoId = Number(q.get("staffMedicoId")) || 0;
      this.turno.consultorioId = Number(q.get("consultorioId")) || 0;
    } else if (path === "turnos/:id") {
      // Detalle o edición
      this.modoEdicion =
        this.route.snapshot.queryParamMap.get("edit") === "true";
      const idParam = this.route.snapshot.paramMap.get("id");
      if (!idParam) return;

      const id = Number(idParam);
      if (isNaN(id)) {
        console.error("El ID proporcionado no es un número válido.");
        return;
      }

      this.turnoService.get(id).subscribe({
        next: (dataPackage) => {
          this.turno = <Turno>dataPackage.data;
        },
        error: (err) => {
          console.error("Error al obtener el turno:", err);
        },
      });
    }
  }

  loadDropdownData(): void {
    // Cargar pacientes
    // El médico no tiene permiso sobre el padrón completo: elige entre sus pacientes.
    this.pacienteService.all().subscribe({
      next: (dataPackage: DataPackage<Paciente[]>) => {
        this.pacientes = dataPackage.data || [];
      },
      error: () => {
        this.hceService.misPacientes().subscribe({
          next: res => { this.pacientes = (res.data || []) as unknown as Paciente[]; },
          error: () => { this.pacientes = []; }
        });
      }
    });

    this.cargarStaff();

    // Cargar consultorios
    this.consultorioService.getAll().subscribe({
      next: (dataPackage: DataPackage<Consultorio[]>) => {
        this.consultorios = dataPackage.data || [];
      },
      error: () => {
        // Sin permiso sobre el catálogo: queda el consultorio del horario elegido.
        const q = this.route.snapshot.queryParamMap;
        const id = Number(q.get("consultorioId"));
        this.consultorios = id
          ? [{ id, nombre: q.get("consultorioNombre") || "Consultorio" } as unknown as Consultorio]
          : [];
      }
    });
  }

  reintentarStaff(): void {
    this.medicoSesion.invalidar();
    this.cargarStaff();
  }

  private cargarStaff(): void {
    this.cargandoStaff = true;
    this.errorStaff = "";
    const esMedico = (this.authService.getUserRole() || "").toUpperCase() === "MEDICO";
    this.staffMedicoService.all().subscribe({
      next: (dataPackage: DataPackage<StaffMedico[]>) => {
        this.staffMedicos = dataPackage.data || [];
        this.completarStaffDelMedico(esMedico);
      },
      error: () => {
        this.staffMedicos = [];
        this.completarStaffDelMedico(true);
      }
    });
  }

  private completarStaffDelMedico(usarSesion: boolean): void {
    if (!usarSesion) {
      this.cargandoStaff = false;
      return;
    }
    this.medicoSesion.resolver().subscribe({
      next: sesion => {
        const propios = staffComoLista(sesion) as unknown as StaffMedico[];
        if (this.staffMedicos.length === 0) {
          this.staffMedicos = propios;
        }
        if (this.esSobreturno && !this.turno.staffMedicoId && sesion.staffMedicoId) {
          this.turno.staffMedicoId = sesion.staffMedicoId;
        }
        this.cargandoStaff = false;
        if (!this.staffMedicos.length) {
          this.errorStaff = "No hay un staff médico vinculado a tu usuario en un centro. Pedile a un administrador que te asocie.";
        }
      },
      error: err => {
        this.cargandoStaff = false;
        if (!this.staffMedicos.length) {
          this.errorStaff = mensajeMedico(err);
        }
      }
    });
  }

  save(): void {
    console.log("Valores antes de guardar:", {
      pacienteId: this.turno.pacienteId,
      staffMedicoId: this.turno.staffMedicoId,
      consultorioId: this.turno.consultorioId,
    });

    // Validaciones básicas
    if (
      !this.isValidId(this.turno.pacienteId) ||
      !this.isValidId(this.turno.staffMedicoId) ||
      !this.isValidId(this.turno.consultorioId)
    ) {
      this.modalService.alert(
        "Error",
        "Debe completar todos los campos obligatorios."
      );
      return;
    }

    // Validar horarios
    if (!this.turno.fecha || !this.turno.horaInicio || !this.turno.horaFin) {
      this.modalService.alert(
        "Error",
        "Debe especificar fecha, hora de inicio y hora de fin."
      );
      return;
    }

    // Si es sobreturno, verificar solapamiento y mostrar advertencia (con tabla en modal)
    if (this.esSobreturno && !this.turno.id) {
      this.verificarSolapamiento().then(async haySolapamiento => {
        if (haySolapamiento) {
          try {
            const comp = await this.getConflictModalComponent();
            const modalRef = this.modalService.open(comp, { size: 'lg' });
            modalRef.componentInstance.title = '⚠️ Advertencia de Solapamiento';
            modalRef.componentInstance.headerMessage = this.mensajeAdvertencia;
            modalRef.componentInstance.conflicts = this.lastConflicts || [];
            modalRef.componentInstance.confirmLabel = 'Crear sobreturno';

            modalRef.result.then(() => {
              this.guardarSobreturno();
            }).catch(() => {
              console.log('Creación de sobreturno cancelada por el usuario');
            });
          } catch (e) {
            // Fallback al modal simple si algo falla
            this.modalService.confirm(
              '⚠️ Advertencia de Solapamiento',
              this.mensajeAdvertencia,
              '¿Desea continuar y crear el sobreturno de todas formas?'
            ).then(() => {
              this.guardarSobreturno();
            }).catch(() => {
              console.log('Creación de sobreturno cancelada por el usuario');
            });
          }
        } else {
          this.guardarSobreturno();
        }
      });
    } else {
      // Edición normal
      this.guardarTurno();
    }
  }

  private guardarSobreturno(): void {
    // Agregar metadata de sobreturno
    console.log('🔶 Creando SOBRETURNO manual (fuera de agenda)');
    // No esperaremos la promesa aquí para mantener comportamiento síncrono desde la UI
    void this.guardarTurno();
  }

  private async guardarTurno(): Promise<void> {
    const op = this.turno.id
      ? this.turnoService.update(this.turno.id, this.turno)
      : this.turnoService.create(this.turno);

    op.subscribe({
      next: async (response: DataPackage<Turno>) => {
        // El backend usa siempre HTTP 200 para respuestas, y coloca el código real
        // en `status_code`. Aquí manejamos respuestas de negocio como CONFLICT (409).
        if (response.status_code === 409 && response.data) {
          const conflicts = response.data as unknown as any[];


          const formattedMessage = `${response.status_text}\n\n. Si acepta, el sobreturno se creará igualmente y podría causar conflictos en la agenda del médico.`;

          // Abrir modal de conflictos con tabla (uso import dinámico para evitar dependencias estáticas)
          try {
            const comp = await this.getConflictModalComponent();
            const modalRef = this.modalService.open(comp, { size: 'lg' });
            modalRef.componentInstance.title = " Conflicto de Solapamiento";
            modalRef.componentInstance.headerMessage = formattedMessage;
            modalRef.componentInstance.conflicts = conflicts;
            modalRef.componentInstance.confirmLabel = "Crear sobreturno";

            modalRef.result.then(() => {
              // Setear flag para forzar la creación y reintentar
              this.turno.permitirSolapamiento = true;
              void this.guardarTurno();
            }).catch(() => {
              console.log("Creación cancelada por el usuario");
            });
          } catch (e) {
            // Fallback a modal simple
            this.modalService.confirm(
              "⚠️ Conflicto de Solapamiento",
              formattedMessage,
              "Crear sobreturno"
            ).then(() => {
              this.turno.permitirSolapamiento = true;
              void this.guardarTurno();
            }).catch(() => {
              console.log("Creación cancelada por el usuario");
            });
          }

          return;
        }

        if (response.status_code !== 200) {
          this.modalService.alert("Error", response.status_text || "No se pudo guardar el turno.");
          return;
        }

        this.modalService.alert(
          "Éxito",
          this.esSobreturno
            ? "Sobreturno creado exitosamente"
            : "Turno guardado exitosamente"
        );
        this.router.navigate(["/turnos"]);
      },
      error: (error) => {
        // Fallback por si la petición falla a nivel de red o servidor
        console.error("Error al guardar el turno:", error);
        const mensaje = error?.error?.status_text || error?.error?.message || "No se pudo guardar el turno.";
        this.modalService.alert("Error", mensaje);
      },
    });
  }

  private lastConflicts: any[] = [];

  private async verificarSolapamiento(): Promise<boolean> {
    const timeToMinutes = (t: string | undefined | null): number => {
      if (!t) return NaN;
      const parts = t.split(':').map(p => Number(p));
      const h = parts[0] || 0;
      const m = parts[1] || 0;
      return h * 60 + m;
    };

    // Verificar solapamientos reales consultando turnos existentes
    return new Promise((resolve) => {
      // Obtener turnos del mismo médico en la misma fecha
      this.turnoService.all().subscribe({
        next: (response: DataPackage<Turno[]>) => {
          if (response.status_code !== 200) {
            resolve(false);
            return;
          }

          const turnosExistentes = response.data || [];
          const turnosMismoMedicoFecha = turnosExistentes.filter(turno =>
            turno.staffMedicoId === this.turno.staffMedicoId &&
            // Normalizar formato de fecha (ISO yyyy-MM-dd)
            (turno.fecha || '') === (this.turno.fecha || '') &&
            turno.id !== this.turno.id // Excluir el turno actual si es edición
          );

          // Verificar si hay solapamiento de horarios (comparando minutos)
          const inicioNuevo = timeToMinutes(this.turno.horaInicio);
          const finNuevo = timeToMinutes(this.turno.horaFin);

          const haySolapamiento = turnosMismoMedicoFecha.some(turno => {
            const inicioExistente = timeToMinutes(turno.horaInicio);
            const finExistente = timeToMinutes(turno.horaFin);

            if (isNaN(inicioExistente) || isNaN(finExistente) || isNaN(inicioNuevo) || isNaN(finNuevo)) {
              return false; // no podemos comparar correctamente
            }

            return inicioNuevo < finExistente && finNuevo > inicioExistente;
          });

          if (haySolapamiento) {
            this.advertenciaSolapamiento = true;

            // Construir listado de conflictos detallado para usar en el modal con tabla
            this.lastConflicts = turnosMismoMedicoFecha.map(t => ({
              horaInicio: t.horaInicio,
              horaFin: t.horaFin,
              paciente: t.nombrePaciente || `${t.nombrePaciente || ''} ${t.apellidoPaciente || ''}` || 'N/A'
            }));

            // Generar lista legible de conflictos (hasta 5) para mostrar en la advertencia
            const conflictsPreview = turnosMismoMedicoFecha.slice(0, 5).map((t, i) =>
              `${i + 1}. ${t.horaInicio} - ${t.horaFin}  (Paciente: ${t.nombrePaciente || t.pacienteId || 'N/A'})`
            ).join('\n');

            this.mensajeAdvertencia = `Se detectó(s) ${turnosMismoMedicoFecha.length} turno(s) que se solapan con el horario seleccionado:\n\n${conflictsPreview}\n\n¿Desea continuar y crear el sobreturno de todas formas?`;
          } else {
            this.lastConflicts = [];
          }

          resolve(haySolapamiento);
        },
        error: (error: any) => {
          console.error("Error al verificar solapamientos:", error);
          resolve(false); // En caso de error, asumir no hay solapamiento
        }
      });
    });
  }
  private isValidId(id: any): boolean {
    // Un ID es válido si no es null, undefined, string vacío, pero SÍ acepta 0
    return id != null && id !== "";
  }
  goBack(): void {
    this.router.navigate(["/turnos"]);
  }

  cancelar(): void {
    if (this.turno.id) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {},
        queryParamsHandling: "merge",
      });
      this.modoEdicion = false;
    } else {
      this.goBack();
    }
  }

  activarEdicion(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { edit: true },
      queryParamsHandling: "merge",
    });
    this.modoEdicion = true;
  }

  getEstadoBadgeClass(estado: string): string {
    switch (estado?.toUpperCase()) {
      case "PROGRAMADO":
        return "estado-display estado-programado";
      case "CONFIRMADO":
        return "estado-display estado-confirmado";
      case "REAGENDADO":
        return "estado-display estado-reagendado";
      case "CANCELADO":
        return "estado-display estado-cancelado";
      default:
        return "estado-display bg-secondary";
    }
  }

  remove(turno: Turno): void {
    if (turno.id === undefined) {
      this.modalService.alert(
        "Error",
        "No se puede eliminar: el turno no tiene ID."
      );
      return;
    }
    this.modalService
      .confirm(
        "Eliminar Turno",
        "¿Está seguro que desea eliminar este turno?",
        "Si elimina el turno no lo podrá utilizar luego"
      )
      .then(() => {
        this.turnoService.remove(turno.id!).subscribe({
          next: () => {
            this.goBack();
          },
          error: (err) => {
            console.error("Error al eliminar el turno:", err);
            this.modalService.alert(
              "Error",
              "No se pudo eliminar el turno. Intente nuevamente."
            );
          },
        });
      });
  }

  allFieldsEmpty(): boolean {
    return (
      !this.turno?.pacienteId &&
      !this.turno?.staffMedicoId &&
      !this.turno?.consultorioId
    );
  }

  // === MÉTODOS DE AUDITORÍA ===

  /** Carga el historial de auditoría del turno */
  loadAuditHistory(): void {
    if (!this.turno.id) return;

    this.loadingAudit = true;
    this.turnoService.getAuditHistory(this.turno.id).subscribe({
      next: (response: DataPackage<AuditLog[]>) => {
        if (response.status === 1) {
          this.auditHistory = response.data || [];
        }
        this.loadingAudit = false;
      },
      error: (error) => {
        console.error("Error al cargar historial:", error);
        this.loadingAudit = false;
      },
    });
  }

  /** Muestra/oculta el panel de auditoría */
  toggleAuditPanel(): void {
    this.showAuditPanel = !this.showAuditPanel;
    if (this.showAuditPanel && this.auditHistory.length === 0) {
      this.loadAuditHistory();
    }
  }

  /** Verifica la integridad del historial de auditoría */
  verifyAuditIntegrity(): void {
    if (!this.turno.id) return;

    this.turnoService.verifyAuditIntegrity(this.turno.id).subscribe({
      next: (response: DataPackage<{ isValid: boolean }>) => {
        if (response.status === 1) {
          this.auditIntegrityValid = response.data.isValid;
          const message = this.auditIntegrityValid
            ? "El historial de auditoría es íntegro y válido"
            : "Se detectaron inconsistencias en el historial de auditoría";
          this.modalService.alert("Verificación de Integridad", message);
        }
      },
      error: (error) => {
        console.error("Error al verificar integridad:", error);
        this.modalService.alert(
          "Error",
          "No se pudo verificar la integridad del historial"
        );
      },
    });
  }

  /** Formatea una fecha y hora para mostrar */
  formatDateTime(dateTimeString: string): string {
    if (!dateTimeString) return "";
    const date = new Date(dateTimeString);
    return date.toLocaleString("es-ES");
  }

  /** Obtiene la clase CSS para el tipo de acción de auditoría */
  getActionClass(action: string): string {
    const classes: any = {
      CREATED: "badge bg-info",
      STATUS_CHANGED: "badge bg-primary",
      CANCELED: "badge bg-danger",
      CONFIRMED: "badge bg-success",
      RESCHEDULED: "badge bg-warning",
      DELETED: "badge bg-dark",
    };
    return classes[action] || "badge bg-secondary";
  }

  /** Obtiene el icono para el tipo de acción */
  getActionIcon(action: string): string {
    const icons: any = {
      CREATED: "fas fa-plus-circle",
      STATUS_CHANGED: "fas fa-edit",
      CANCELED: "fas fa-times-circle",
      CONFIRMED: "fas fa-check-circle",
      RESCHEDULED: "fas fa-calendar-alt",
      DELETED: "fas fa-trash",
    };
    return icons[action] || "fas fa-question-circle";
  }

  /** Obtiene el icono Material Symbol para el tipo de acción */
  getActionIconMaterial(action: string): string {
    const icons: any = {
      CREATED: "add_circle",
      STATUS_CHANGED: "edit",
      CANCELED: "cancel",
      CONFIRMED: "check_circle",
      RESCHEDULED: "event_repeat",
      DELETED: "delete",
    };
    return icons[action] || "help";
  }
}
