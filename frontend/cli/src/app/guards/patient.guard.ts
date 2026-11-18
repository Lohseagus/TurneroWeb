import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService, Role } from '../inicio-sesion/auth.service';
import { UserContextService } from '../services/user-context.service';

@Injectable({
  providedIn: 'root'
})
export class PatientGuard implements CanActivate {
  constructor(
    private router: Router,
    private authService: AuthService,
    private userContextService: UserContextService
  ) {}

  canActivate(): boolean {

     //DESARROLLO: acceso libre a todas las rutas
    //return true; // quitar esta línea para activar la protección de rutas


    //PRODUCCION: rutas protegidas
    
    // Verificar si está autenticado
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/ingresar']);
      return false;
    }

    // El staff hereda PACIENTE, así que la ruta abre. PacientePortalComponent
    // mira GET /api/pacientes/me y, si no hay ficha, muestra el vacío sin montar
    // las pantallas (no hay toast de permiso ni un Cargando que no termina).
    return this.userContextService.hasRole(Role.PACIENTE);
  }
}
