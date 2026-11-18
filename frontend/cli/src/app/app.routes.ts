import { Route, Routes } from "@angular/router";
import { Type } from "@angular/core";
import { HomeComponent } from "./home/home.component";
import { InicioSesionComponent } from "./inicio-sesion/inicio-sesion.component";
import { RegistroUsuarioComponent } from "./registro-usuario/registro-usuario.component";
import { RecuperarContrasenaComponent } from "./recuperacionContrasena/recuperacionContrasena.component";
import { ActivacionCuentaComponent } from "./activacion-cuenta/activacion-cuenta.component";
import { DeepLinkBridgeComponent } from "./deep-link/deep-link-bridge.component";
import { TurnosComponent } from "./turnos/turnos.component";
import { TurnoDetailComponent } from "./turnos/turno-detail.component";
import { AdminAgendaComponent } from "./agenda/admin-agenda.component";
import { DiasExcepcionalesComponent } from "./agenda/dias-excepcionales.component";
import { PacientesComponent } from "./pacientes/pacientes.component";
import { PacienteDetailComponent } from "./pacientes/paciente-detail.component";
import { EspecialidadesComponent } from "./especialidades/especialidades.component";
import { EspecialidadDetailComponent } from "./especialidades/especialidad-detail.component";
import { CentrosAtencionComponent } from "./centrosAtencion/centrosAtencion.component";
import { CentroAtencionDetailRefactoredComponent } from "./centrosAtencion/centroAtencion-detail-refactored.component";
import { ConsultoriosComponent } from "./consultorios/consultorios.component";
import { ConsultorioDetailComponent } from "./consultorios/consultorio-detail.component";
import { MedicosComponent } from "./medicos/medicos.component";
import { StaffMedicosComponent } from "./staffMedicos/staffMedicos.component";
import { StaffMedicoDetailComponent } from "./staffMedicos/staffMedico-detail.component";
import { MedicoDetailComponent } from "./medicos/medico-detail.component";
import { DisponibilidadMedicoComponent } from "./disponibilidadMedicos/disponibilidadMedico.component";
import { DisponibilidadMedicoDetailComponent } from "./disponibilidadMedicos/disponibilidadMedico-detail.component";
import { EsquemaTurnoComponent } from "./esquemaTurno/esquemaTurno.component";
import { EsquemaTurnoDetailComponent } from "./esquemaTurno/esquemaTurno-detail.component";
import { ObraSocialComponent } from "./obraSocial/obraSocial.component";
import { ObraSocialDetailComponent } from "./obraSocial/obraSocial-detail.component";
import { PacienteDashboardComponent } from "./pacientes/paciente-dashboard.component";
import { PacientePortalComponent } from "./pacientes/paciente-portal.component";
import { PacienteTurnosComponent } from "./pacientes/paciente-turnos.component";
import { PacienteSaludComponent } from "./pacientes/paciente-salud.component";
import { PacienteCuentaComponent } from "./pacientes/paciente-cuenta.component";
import { ListaEsperaEntradaComponent, redirigirPortal } from "./pacientes/paciente-portal";
import { PacienteAgendaComponent } from "./pacientes/paciente-agenda.component";
import { PacienteReagendarTurnoComponent } from "./pacientes/paciente-reagendar-turno.component";
import { PacienteNotificacionesComponent } from "./pacientes/paciente-notificaciones.component";
import { EncuestaComponent } from "./pacientes/encuesta/encuesta.component";

// Medico components
import { MedicoDashboardComponent } from "./medicos/medico-dashboard.component";
import { MedicoHorariosComponent } from "./medicos/medico-horarios.component";
import { MedicoEstadisticasComponent } from "./medicos/medico-estadisticas.component";
import { MedicoPerfilComponent } from "./medicos/medico-perfil.component";

// Audit components
import { TurnoAdvancedSearchComponent } from "./turnos/turno-advanced-search.component";
import { AuditDashboardComponent } from "./turnos/audit-dashboard.component";

import { AdminGuard } from "./guards/admin.guard";
import { PatientGuard } from "./guards/patient.guard";
import { MedicoGuard } from "./guards/medico.guard";
import { OperadorGuard } from "./guards/operador.guard";
import { AdminOperadorGuard } from "./guards/admin-operador.guard";
import { AdminOperadorMedicoGuard } from "./guards/admin-operador-medico.guard";
import { SuperAdminGuard } from "./guards/super-admin.guard";
import { CentroAtencionAccessGuard } from "./guards/centro-atencion-access.guard";
import { MiCentroRedirectGuard } from "./guards/mi-centro-redirect.guard";

import { OperadorDashboardComponent } from "./operador/operador-dashboard.component";
//import { OperadorAgendaComponent } from "./operador/operador-agenda.component";
import { OperadoresComponent } from "./operador/operadores.component";
import { OperadorDetailComponent } from "./operador/operador-detail.component";
import { OperadorAgendaComponent } from "./operador/operador-agenda.component";
import { OperadorPerfilComponent } from "./operador/operador-perfil.component";
import { AdminPerfilComponent } from "./admin/admin-perfil.component";
import { TokenStatusComponent } from "./components/token-status.component";
import { AdminDashboardComponent } from "./admin/admin-dashboard.component";
import { GestionEncuestasComponent } from "./admin/gestion-encuestas/gestion-encuestas.component";
import { AdminConfigComponent } from "./admin/admin-config.component";
import { AdminListComponent } from "./admin-management/admin-list.component";
import { AdminDetailComponent } from "./admin-management/admin-detail.component";
import { DashboardGestionComponent } from "./dashboard-gestion/dashboard-gestion.component";
import { ReporteAtencionComponent } from "./reporte-atencion/reporte-atencion.component";
import { ListaEsperaComponent } from "./lista-espera/lista-espera.component";
import { PreferenciasPacienteComponent } from "./pacientes/preferencias-paciente/preferencias-paciente.component";
import { ListaEsperaEstadisticasComponent } from "./lista-espera/lista-espera-estadisticas.component";
import { MedicoHceComponent } from "./historia-clinica/medico-hce.component";
import { MedicoConsultaComponent } from "./historia-clinica/medico-consulta.component";
import { VerificarPrescripcionComponent } from "./historia-clinica/verificar-prescripcion.component";
import { PlanesPublicosComponent } from "./saas/planes-publicos.component";
import { RegistroSaasComponent } from "./saas/registro-saas.component";
import { MiPlanComponent } from "./saas/mi-plan.component";
import { SaasAdminComponent } from "./saas/saas-admin.component";

/** El staff entra a la ruta, y el portal decide si hay ficha antes de montar la pantalla. */
function portalPaciente(path: string, component: Type<unknown>): Route {
  const partes = path.split("/");
  const ultima = partes[partes.length - 1];
  const conParametro = ultima.startsWith(":");
  return {
    path: conParametro ? partes.slice(0, -1).join("/") : path,
    component: PacientePortalComponent,
    canActivate: [PatientGuard],
    children: [{ path: conParametro ? ultima : "", component }],
  };
}

export const routes: Routes = [
  { path: "", component: HomeComponent },
  { path: "home", redirectTo: "", pathMatch: "full" },
  { path: "superadmin-dashboard", redirectTo: "saas", pathMatch: "full" },
  { path: "superadmin-auditoria", redirectTo: "turnos/audit-dashboard", pathMatch: "full" },
  { path: "superadmin-metricas", redirectTo: "dashboard-gestion", pathMatch: "full" },
  { path: "planes", component: PlanesPublicosComponent },
  { path: "registro-saas", component: RegistroSaasComponent },
  { path: "verificar-receta/:codigo", component: VerificarPrescripcionComponent },
  { path: "verificar-orden/:codigo", component: VerificarPrescripcionComponent, data: { tipo: "ORDEN" } },

  // Ruta puente para deep linking (enlimport { ListaEsperaFormComponent } from "./lista-espera/lista-espera-form.component";
  //aces desde email)
  {
    path: "link-verificacion",
    component: DeepLinkBridgeComponent
  },

  // Ruta de redirección dinámica a "Mi Centro" (OPERADOR/ADMINISTRADOR)
  {
    path: "mi-centro",
    canActivate: [MiCentroRedirectGuard],
    children: [] // No se necesita componente, el guard maneja la redirección
  },

  // Rutas de Operador (protegidas por OperadorGuard)
  {
    path: "operador-dashboard",
    component: OperadorDashboardComponent,
    canActivate: [OperadorGuard],
  },

  {
    path: "operador-agenda",
    component: OperadorAgendaComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "operadores",
    component: OperadoresComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "operadores/new",
    component: OperadorDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "operadores/:id",
    component: OperadorDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "operador-perfil",
    component: OperadorPerfilComponent,
    canActivate: [OperadorGuard],
  },
  {
    path: "admin-perfil",
    component: AdminPerfilComponent,
    canActivate: [AdminGuard],
  },

  // rutas de autenticación. login y registro quedaron como alias: el router
  // no tiene esas rutas y NG04002 dejaba la pantalla colgada.
  { path: "login", redirectTo: "ingresar", pathMatch: "full" },
  { path: "registro", redirectTo: "registro-usuario", pathMatch: "full" },
  { path: "ingresar", component: InicioSesionComponent },
  { path: "registro-usuario", component: RegistroUsuarioComponent },
  { path: "recuperar-contrasena", component: RecuperarContrasenaComponent },
  { path: "reset-password", component: RecuperarContrasenaComponent },
  { path: "activate-account", component: ActivacionCuentaComponent },

  // La agenda queda pública para el banner anónimo. El resto pasa por el portal:
  // el staff sin ficha ve el vacío y no se monta la pantalla. Las rutas viejas
  // redirigen a la sección nueva, que también está detrás de ese vacío.
  portalPaciente("paciente-dashboard", PacienteDashboardComponent),
  portalPaciente("paciente-turnos", PacienteTurnosComponent),
  portalPaciente("paciente-salud", PacienteSaludComponent),
  {
    path: "paciente-cuenta",
    component: PacientePortalComponent,
    canActivate: [PatientGuard],
    children: [
      {
        path: "",
        component: PacienteCuentaComponent,
        children: [
          { path: "", pathMatch: "full", redirectTo: "perfil" },
          { path: "perfil", component: PacienteDetailComponent, data: { embebido: true } },
          { path: "horarios", component: PreferenciasPacienteComponent, data: { embebido: true } },
          { path: "notificaciones", component: PacienteNotificacionesComponent, data: { embebido: true } },
        ],
      },
    ],
  },
  {
    path: "paciente-agenda",
    component: PacienteAgendaComponent,
  },
  { path: "paciente-notificaciones", redirectTo: redirigirPortal("paciente-notificaciones") },
  { path: "paciente-historial", redirectTo: redirigirPortal("paciente-historial") },
  { path: "paciente-historia-clinica", redirectTo: redirigirPortal("paciente-historia-clinica") },
  { path: "paciente-recetas", redirectTo: redirigirPortal("paciente-recetas") },
  { path: "paciente-preferencias", redirectTo: redirigirPortal("paciente-preferencias") },
  { path: "paciente-perfil", redirectTo: redirigirPortal("paciente-perfil") },
  portalPaciente("paciente/encuesta/:id", EncuestaComponent),
  portalPaciente("paciente-reagendar-turno/:id", PacienteReagendarTurnoComponent),
  {
    path: "paciente-reprogramar-ausencia",
    component: PacientePortalComponent,
    canActivate: [PatientGuard],
    children: [{
      path: ":id",
      component: PacienteReagendarTurnoComponent,
      data: { modo: "ausencia" },
    }],
  },

  // Medico Routes
  {
    path: "medico-dashboard",
    component: MedicoDashboardComponent,
    canActivate: [MedicoGuard],
  },
  {
    path: "medico-horarios",
    component: MedicoHorariosComponent,
    canActivate: [MedicoGuard],
  },
  {
    path: "medico-estadisticas",
    component: MedicoEstadisticasComponent,
    canActivate: [MedicoGuard],
  },
  {
    path: "medico-perfil",
    component: MedicoPerfilComponent,
    canActivate: [MedicoGuard],
  },
  {
    path: "medico-hce",
    component: MedicoHceComponent,
    canActivate: [MedicoGuard],
  },
  {
    path: "medico-hce/:pacienteId",
    component: MedicoHceComponent,
    canActivate: [MedicoGuard],
  },
  {
    path: "medico-consulta/nueva",
    component: MedicoConsultaComponent,
    canActivate: [MedicoGuard],
  },
  {
    path: "medico-consulta/:id",
    component: MedicoConsultaComponent,
    canActivate: [MedicoGuard],
  },

  { path: "debug/tokens", component: TokenStatusComponent }, // ruta para debug de tokens
  // Lista de Espera Routes
  {
    path: "lista-espera",
    component: ListaEsperaComponent,
    canActivate: [AdminOperadorGuard],
  },
  portalPaciente("lista-espera-form", ListaEsperaEntradaComponent),
  {
    path: "lista-espera-estadisticas",
    component: ListaEsperaEstadisticasComponent,
    canActivate: [AdminOperadorGuard],
  },

  // Admin Routes (protected)
  {
    path: "turnos",
    component: TurnosComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "turnos/new",
    component: TurnoDetailComponent,
    canActivate: [AdminOperadorMedicoGuard],
  },
  {
    path: "turnos/advanced-search",
    component: TurnoAdvancedSearchComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "turnos/audit-dashboard",
    component: AuditDashboardComponent,
    canActivate: [AdminGuard],
  },
  {
    path: "turnos/:id",
    component: TurnoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "turnos/:id/edit",
    component: TurnoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "agenda",
    component: AdminAgendaComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "agenda/dias-excepcionales",
    component: DiasExcepcionalesComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "pacientes",
    component: PacientesComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "pacientes/new",
    component: PacienteDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "pacientes/:id",
    component: PacienteDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "obraSocial",
    component: ObraSocialComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "obraSocial/new",
    component: ObraSocialDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "obraSocial/:id",
    component: ObraSocialDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "especialidades",
    component: EspecialidadesComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "especialidades/new",
    component: EspecialidadDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "especialidades/:id",
    component: EspecialidadDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "centrosAtencion",
    component: CentrosAtencionComponent,
    canActivate: [SuperAdminGuard], // Solo SUPERADMIN ve el listado completo
  },
  {
    path: "centrosAtencion/new",
    component: CentroAtencionDetailRefactoredComponent,
    canActivate: [CentroAtencionAccessGuard], // Solo SUPERADMIN crea centros (validado en guard)
  },
  {
    path: "centrosAtencion/:id",
    component: CentroAtencionDetailRefactoredComponent,
    canActivate: [CentroAtencionAccessGuard], // SUPERADMIN (cualquier centro), ADMIN/OPERADOR (solo su centro)
  },
  {
    path: "consultorios",
    component: ConsultoriosComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "consultorios/new",
    component: ConsultorioDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "consultorios/:id",
    component: ConsultorioDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "medicos",
    component: MedicosComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "medicos/new",
    component: MedicoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "medicos/:id",
    component: MedicoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "staffMedico",
    component: StaffMedicosComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "staffMedico/new",
    component: StaffMedicoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "staffMedico/:id",
    component: StaffMedicoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "disponibilidades-medico",
    component: DisponibilidadMedicoComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "disponibilidades-medico/new",
    component: DisponibilidadMedicoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "disponibilidades-medico/:id",
    component: DisponibilidadMedicoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "esquema-turno",
    component: EsquemaTurnoComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "esquema-turno/new",
    component: EsquemaTurnoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "esquema-turno/:id",
    component: EsquemaTurnoDetailComponent,
    canActivate: [AdminOperadorGuard],
  },
  {
    path: "admin-dashboard",
    component: AdminDashboardComponent,
    canActivate: [AdminGuard],
  },
  {
    path: "admin/gestion-encuestas",
    component: GestionEncuestasComponent,
    canActivate: [AdminGuard],
  },
  {
    path: "dashboard-gestion",
    component: DashboardGestionComponent,
    canActivate: [AdminGuard],
  },
  {
    path: "admin-reporte-atencion",
    component: ReporteAtencionComponent,
    canActivate: [AdminGuard],
  },
  // Admin management (admins list / create / edit)
  {
    path: "admin/users",
    component: AdminListComponent,
    canActivate: [AdminGuard]
  },
  {
    path: "admin/users/new",
    component: AdminDetailComponent,
    canActivate: [AdminGuard]
  },
  {
    path: "admin/users/:id",
    component: AdminDetailComponent,
    canActivate: [AdminGuard]
  },
  {
    path: "config",  // O "admin-config" si prefieres algo más descriptivo
    component: AdminConfigComponent,
    canActivate: [AdminOperadorGuard],  // Protege para admins y operadores
  },
  {
    path: "mi-plan",
    component: MiPlanComponent,
    canActivate: [AdminGuard],
  },
  {
    path: "saas",
    component: SaasAdminComponent,
    canActivate: [SuperAdminGuard],
    data: { vista: "dashboard" },
  },
  {
    path: "saas/suscripciones",
    component: SaasAdminComponent,
    canActivate: [SuperAdminGuard],
    data: { vista: "suscripciones" },
  },
  {
    path: "saas/suscripciones/:id",
    component: SaasAdminComponent,
    canActivate: [SuperAdminGuard],
    data: { vista: "detalle" },
  },
  {
    path: "saas/planes",
    component: SaasAdminComponent,
    canActivate: [SuperAdminGuard],
    data: { vista: "planes" },
  },
  {
    path: "saas/pagos",
    component: SaasAdminComponent,
    canActivate: [SuperAdminGuard],
    data: { vista: "pagos" },
  },
];
