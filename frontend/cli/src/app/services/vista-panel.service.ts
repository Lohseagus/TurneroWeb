import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { puedeCambiarDeVista } from '../config/vista-panel';
import {
  VistaPanel,
  clearAllVistaPanel,
  readVistaPanel,
  writeVistaPanel
} from '../config/vista-panel-storage';
import { UserContextService } from './user-context.service';

/**
 * Vista activa del sidebar. Arranca en staff y se recuerda por usuario
 * hasta el logout, que borra la clave.
 */
@Injectable({ providedIn: 'root' })
export class VistaPanelService {
  private readonly subject = new BehaviorSubject<VistaPanel>('staff');
  readonly vista$ = this.subject.asObservable();
  private userKey: string | null = null;

  constructor(private usuarios: UserContextService) {
    this.usuarios.userContext$.subscribe(context => {
      if (!context.isAuthenticated || !puedeCambiarDeVista(context.primaryRole)) {
        this.userKey = null;
        this.subject.next('staff');
        return;
      }
      const key = this.keyOf(context.email, context.id);
      this.userKey = key;
      this.subject.next(readVistaPanel(this.storage(), key));
    });
  }

  actual(): VistaPanel {
    return this.subject.value;
  }

  activar(vista: VistaPanel): void {
    if (!this.userKey) {
      return;
    }
    writeVistaPanel(this.storage(), this.userKey, vista);
    this.subject.next(vista);
  }

  /** La llama el cierre de sesión, además de borrar el storage. */
  olvidar(): void {
    clearAllVistaPanel(this.storage());
    try {
      clearAllVistaPanel(localStorage);
    } catch {
      // localStorage puede no existir en el test.
    }
    this.userKey = null;
    this.subject.next('staff');
  }

  private keyOf(email: string | undefined, id: string | undefined): string {
    const limpio = email?.trim();
    if (limpio) {
      return limpio;
    }
    if (id) {
      return `id:${id}`;
    }
    return 'session';
  }

  private storage(): Storage | null {
    try {
      return typeof sessionStorage === 'undefined' ? null : sessionStorage;
    } catch {
      return null;
    }
  }
}
