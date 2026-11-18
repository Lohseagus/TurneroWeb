import { Component, OnInit, ViewChild, ChangeDetectionStrategy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule, NgForm } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { PacienteService } from "./paciente.service";
import { Paciente } from "./paciente";
import { DataPackage } from "../data.package";
import { ModalService } from "../modal/modal.service";
import { AuthService } from "../inicio-sesion/auth.service";
import { ToastService } from "../services/toast.service";
import { timeout } from "rxjs/operators";

@Component({
  selector: "app-paciente-detail",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./paciente-detail.component.html", 
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: "./paciente-detail.component.css",
})
export class PacienteDetailComponent implements OnInit {
  @ViewChild("form", { static: false }) form!: NgForm;

  paciente: Paciente = {
    id: 0,
    nombre: "",
    apellido: "",
    email: "",
    telefono: "",
    dni: null,
    fechaNacimiento: "",
  };
  modoEdicion = false;
  /** Alta explícita (ruta pacientes/new). No se infiere de un id vacío mientras carga. */
  alta = false;
  cargando = true;
  sinFicha = false;
  errorCarga = "";
  obrasSociales: { id: number; nombre: string; codigo: string }[] = [];
  private pacienteCargado = false;
  private quiereEditar = false;
  private cargaToken = 0;
  private readonly mensajeSinFicha =
    "Tu usuario puede ver el panel de paciente, pero no hay una ficha de paciente vinculada. Pedile a un administrador del centro que te asocie.";

  constructor(
    private pacienteService: PacienteService,
    private route: ActivatedRoute,
    private router: Router,
    private modalService: ModalService,
    private authService: AuthService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.pacienteService
      .getObrasSociales()
      .subscribe(
        (dp: DataPackage<{ id: number; nombre: string; codigo: string }[]>) => {
          this.obrasSociales = dp.data || [];
          this.vincularObraSocial();
        }
      );

    // El query param solo cambia el modo cuando la ficha ya está. Antes, un id
    // vacío hacía que la pantalla se viera como un alta.
    this.route.queryParamMap.subscribe((params) => {
      this.quiereEditar = params.get("edit") === "true";
      if (this.pacienteCargado) {
        this.modoEdicion = this.quiereEditar;
      }
    });

    const path = this.route.snapshot.routeConfig?.path;
    if (path === "pacientes/new") {
      this.alta = true;
      this.modoEdicion = true;
      this.cargando = false;
      return;
    }

    this.cargarFicha();
  }

  reintentar(): void {
    this.cargarFicha();
  }

  esNuevo(): boolean {
    return this.alta;
  }

  private esPerfil(): boolean {
    const path = this.route.snapshot.routeConfig?.path;
    return path === "paciente-perfil" || path === "perfil";
  }

  private cargarFicha(): void {
    const token = ++this.cargaToken;
    this.cargando = true;
    this.sinFicha = false;
    this.errorCarga = "";
    this.alta = false;
    this.modoEdicion = false;
    this.pacienteCargado = false;

    if (this.esPerfil()) {
      this.pacienteService.mio().pipe(timeout(20000)).subscribe({
        next: (dp: DataPackage<Paciente>) => this.recibirFicha(token, dp),
        error: (error) => this.falloCarga(token, error),
      });
      return;
    }

    const id = this.resolverId();
    if (id == null) {
      return;
    }

    this.pacienteService.get(id).pipe(timeout(20000)).subscribe({
      next: (dp: DataPackage<Paciente>) => this.recibirFicha(token, dp),
      error: (error) => this.falloCarga(token, error),
    });
  }

  private recibirFicha(token: number, dp: DataPackage<Paciente>): void {
    if (token !== this.cargaToken) {
      return;
    }
    if (!dp?.data?.id) {
      this.fichaAusente();
      return;
    }
    this.paciente = dp.data;
    this.vincularObraSocial();
    this.pacienteCargado = true;
    this.modoEdicion = this.quiereEditar;
    this.cargando = false;
  }

  private falloCarga(token: number, error: unknown): void {
    if (token !== this.cargaToken) {
      return;
    }
    this.cargando = false;
    if (this.esPerfil() && this.esNoEncontrado(error)) {
      this.mostrarSinFicha(this.textoConFicha(error));
      return;
    }
    if (this.esNoEncontrado(error)) {
      this.errorCarga = this.textoBackend(error) || "No se encontró el paciente.";
      return;
    }
    this.errorCarga = this.mensajeDe(error);
  }

  /** null si el id de la ruta no sirve: el estado de pantalla ya quedó resuelto. */
  private resolverId(): number | null {
    const raw = this.route.snapshot.paramMap.get("id");
    const id = raw == null ? NaN : Number(raw);
    if (!Number.isFinite(id) || id <= 0) {
      this.cargando = false;
      this.errorCarga = "No se pudo identificar al paciente.";
      return null;
    }
    return id;
  }

  private fichaAusente(): void {
    this.cargando = false;
    if (this.esPerfil()) {
      this.mostrarSinFicha();
      return;
    }
    this.errorCarga = "No se encontró el paciente.";
  }

  private mostrarSinFicha(mensaje?: string): void {
    this.cargando = false;
    this.sinFicha = true;
    this.alta = false;
    this.modoEdicion = false;
    this.pacienteCargado = false;
    this.errorCarga = mensaje?.trim() || this.mensajeSinFicha;
  }

  private vincularObraSocial(): void {
    if (!this.paciente.obraSocial || !this.obrasSociales.length) {
      return;
    }
    const obraSocial = this.obrasSociales.find(
      (os) => os.id === this.paciente.obraSocial?.id
    );
    if (obraSocial) {
      this.paciente.obraSocial = obraSocial;
    }
  }

  private esNoEncontrado(error: unknown): boolean {
    const http = error as {
      status?: number;
      error?: { status_code?: number };
    };
    return http?.status === 404 || http?.error?.status_code === 404;
  }

  private textoConFicha(error: unknown): string | undefined {
    const texto = this.textoBackend(error);
    return texto && /ficha/i.test(texto) ? texto : undefined;
  }

  private mensajeDe(error: unknown): string {
    const texto = this.textoBackend(error);
    if (texto) {
      return texto;
    }
    const status = (error as { status?: number })?.status;
    if (status === 0) {
      return "No hay conexión con el servidor. Reintentá.";
    }
    return "No se pudo cargar la ficha del paciente. Reintentá.";
  }

  private textoBackend(error: unknown): string {
    const http = error as {
      error?: { status_text?: unknown; message?: unknown };
      message?: unknown;
    };
    const crudo = http?.error?.status_text ?? http?.error?.message;
    return typeof crudo === "string" ? crudo.trim() : "";
  }

  isInvalidField(field: any): boolean {
    return field.invalid && (field.dirty || field.touched) && !field.disabled;
  }

  /**
   * Detecta si el usuario actual es administrador basándose en la ruta y el rol
   */
  private isAdmin(): boolean {
    const path = this.route.snapshot.routeConfig?.path;
    const userRole = this.authService.getUserRole();

    // Las rutas de admin son pacientes/new y pacientes/:id.
    // La ficha propia es paciente-perfil (vieja) o perfil, dentro de Mi cuenta.
    const isAdminRoute = path !== "paciente-perfil" && path !== "perfil";

    // Verificar por rol: ADMINISTRADOR u OPERADOR pueden crear pacientes
    const isAdminRole = userRole === "ADMINISTRADOR" || userRole === "OPERADOR";

    // Solo es admin si tanto la ruta como el rol indican que es admin/operador
    return isAdminRoute && isAdminRole;
  }

  /**
   * Detecta si el usuario actual es operador
   */
  private isOperador(): boolean {
    const userRole = this.authService.getUserRole();
    return userRole === "OPERADOR";
  }

  save(): void {
    if (this.form.invalid) {
      Object.keys(this.form.controls).forEach((key) => {
        const control = this.form.controls[key];
        if (!control.disabled) {
          control.markAsTouched();
        }
      });
      return;
    }

    const userRole = this.authService.getUserRole();
    let op;

    if (this.paciente.id && this.esPacienteVendoSuPerfil()) {
      op = this.pacienteService.actualizarMio(this.paciente);
    } else if (this.paciente.id) {
      op = this.pacienteService.update(this.paciente.id, this.paciente);
    } else {
      // Para creaciones, usar el endpoint correcto según el tipo de usuario
      const userEmail = this.authService.getUserEmail();
      const userData = this.authService.getUserData();
      
      // Preparar el paciente con la información de auditoría  
      const pacienteToCreate = {
        ...this.paciente,
        performedBy: userEmail || userData?.email || userRole || "UNKNOWN"
      };

      if (userRole === "ADMINISTRADOR") {
        op = this.pacienteService.createByAdmin(pacienteToCreate);
      } else if (userRole === "OPERADOR") {
        op = this.pacienteService.createByOperator(pacienteToCreate);
      } else {
        this.modalService.alert(
          "Error",
          "No tienes permisos para crear pacientes. Solo administradores y operadores pueden crear pacientes."
        );
        return;
      }
    }

    op.subscribe({
      next: (response) => {
        console.log("Respuesta recibida en next:", response);
        // Verificar si la respuesta indica un error (status_code diferente de 200)
        if (response.status_code && response.status_code !== 200) {
          const errorMessage = response.status_text || "Error al guardar el paciente.";
          this.modalService.alert("Error", errorMessage);
          console.log("Error detectado en respuesta exitosa:", errorMessage);
          return;
        }

        // Si llegamos aquí, es una respuesta exitosa
        const successMessage = this.esNuevo() 
          ? "Paciente creado con éxito"
          : "Paciente actualizado correctamente";

        // Mostrar toast en lugar de modal
        this.toastService.success(successMessage);
        
        // Verificar si hay returnUrl en query params
        this.route.queryParams.subscribe(params => {
          const returnUrl = params['returnUrl'];
          
          if (returnUrl) {
            // Si hay returnUrl y se creó un paciente, redirigir con el ID
            if (this.esNuevo() && response.data?.id) {
              this.router.navigate([returnUrl], {
                queryParams: { pacienteId: response.data.id }
              });
            } else {
              this.router.navigate([returnUrl]);
            }
          } else {
            // Comportamiento normal
            if (this.esPerfil()) {
              this.router.navigate(["/paciente-dashboard"]);
            } else {
              this.router.navigate(["/pacientes"]);
            }
          }
        });
      },
      error: (err) => {
        console.log("Respuesta recibida en error:", err);
        let errorMessage = "Error al guardar el paciente.";
        
        if (err?.error?.status_text) {
          errorMessage = err.error.status_text;
        } else if (err?.error?.message) {
          errorMessage = err.error.message;
        } else if (err?.message) {
          errorMessage = err.message;
        }
        
        this.modalService.alert("Error", errorMessage);
        console.error("Error al guardar paciente:", err);
      },
    });
  }

  goBack(): void {
    const path = this.route.snapshot.routeConfig?.path;
    if (path === "paciente-perfil" || path === "perfil") {
      this.router.navigate(["/paciente-dashboard"]);
    } else {
      // Si es un admin, volver a la lista de pacientes
      this.router.navigate(["/pacientes"]);
    }
  }

  cancelar(): void {
    if (this.paciente.id) {
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

  remove(): void {
    if (!this.paciente.id) return;

    this.pacienteService.remove(this.paciente.id).subscribe({
      next: () => {
        this.modalService.alert("Éxito", "Paciente eliminado correctamente");
        this.goBack();
      },
      error: (err) => {
        const msg = err?.error?.message || "Error al eliminar el paciente.";
        this.modalService.alert("Error", msg);
        console.error("Error al eliminar paciente:", err);
      },
    });
  }

  confirmDelete(): void {
    this.modalService
      .confirm(
        "Eliminar paciente",
        "Confirmar eliminación",
        `¿Está seguro que desea eliminar el paciente ${this.paciente.nombre} ${this.paciente.apellido}?`
      )
      .then(() => this.remove())
      .catch(() => {});
  }

  esPacienteVendoSuPerfil(): boolean {
    return this.esPerfil();
  }

  /**
   * Verifica si el usuario actual puede cambiar contraseña
   * Los roles PACIENTE, OPERADOR y ADMINISTRADOR pueden cambiar su contraseña
   */
  puedeCambiarContrasena(): boolean {
    const userRole = this.authService.getUserRole();
    return userRole === "PACIENTE" || userRole === "OPERADOR" || userRole === "ADMINISTRADOR";
  }

  changePasswordForm = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  };

  changePassword() {
    if (!this.changePasswordForm.currentPassword ||
        !this.changePasswordForm.newPassword ||
        !this.changePasswordForm.confirmPassword) {
      this.modalService.alert('Error', 'Todos los campos de contraseña son obligatorios');
      return;
    }

    if (this.changePasswordForm.newPassword !== this.changePasswordForm.confirmPassword) {
      this.modalService.alert('Error', 'Las contraseñas no coinciden');
      return;
    }

    if (this.changePasswordForm.newPassword.length < 6) {
      this.modalService.alert('Error', 'La nueva contraseña debe tener al menos 6 caracteres');
      return;
    }

    const request = {
      currentPassword: this.changePasswordForm.currentPassword,
      newPassword: this.changePasswordForm.newPassword,
      confirmPassword: this.changePasswordForm.confirmPassword
    };

    this.authService.changePassword(request).subscribe({
      next: (response) => {
        if (response.status_code === 200) {
          this.modalService.alert('Éxito', 'Contraseña cambiada correctamente');
          this.changePasswordForm = {
            currentPassword: '',
            newPassword: '',
            confirmPassword: ''
          };
        } else {
          this.modalService.alert('Error', response.status_text || 'Error al cambiar la contraseña');
        }
      },
      error: (error) => {
        const message = error?.error?.status_text || 'Error al cambiar la contraseña';
        this.modalService.alert('Error', message);
      }
    });
  }
}
