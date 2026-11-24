import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../inicio-sesion/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * Interceptor HTTP que maneja automáticamente errores de autenticación
 * y token expirado en todas las peticiones HTTP
 */
export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const toast = inject(ToastService);

  // Los endpoints de autenticación manejan sus propios errores (login/refresh).
  // No deben disparar el modal global de "Sesión Expirada" (ej. al abrir la app
  // con tokens viejos en storage o sin sesión activa).
  const url = req.url || '';
  const isAuthEndpoint =
    url.includes('/api/auth/refresh') ||
    url.includes('/api/auth/login') ||
    url.includes('/api/auth/google');

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (!isAuthEndpoint && error.status === 401) {
        const customMessage = error.error?.message?.includes('expired') || error.error?.message?.includes('invalid')
          ? 'Su sesión ha expirado. Por favor, inicie sesión nuevamente.'
          : 'Credenciales inválidas. Por favor, verifique sus datos e inicie sesión nuevamente.';
        authService.handleAuthError(error, customMessage);
      }

      if (error.status === 403 && error.error?.code === 'SUSCRIPCION_SOLO_LECTURA') {
        const texto = error.error?.status_text || 'La cuenta es de solo lectura.';
        toast.error(texto, 8000);
        return throwError(() => error);
      }

      // 403 es falta de permiso, no una sesión vencida. No se cierra la sesión
      // ni se muestra el modal. Sin token, la página pública sigue en silencio.
      if (!isAuthEndpoint && error.status === 403) {
        const teniaSesion = !!authService.getToken() || !!authService.getRefreshToken();
        if (teniaSesion) {
          const texto = typeof error.error?.status_text === 'string' && error.error.status_text
            ? error.error.status_text
            : 'No tenés permiso para esa acción.';
          toast.warning(texto);
        }
      }

      return throwError(() => error);
    })
  );
};