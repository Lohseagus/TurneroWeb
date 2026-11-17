import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { DataPackage } from '../data.package';
import { ModalService } from '../modal/modal.service';
import { AuthService } from '../inicio-sesion/auth.service';
import { ToastService } from '../services/toast.service';
import { Paciente } from './paciente';
import { PacienteDetailComponent } from './paciente-detail.component';
import { PacienteService } from './paciente.service';

class PacienteFalso {
  fichas: { id: number; subject: Subject<DataPackage<Paciente>> }[] = [];
  propias: Subject<DataPackage<Paciente>>[] = [];
  private obras = new Subject<DataPackage<{ id: number; nombre: string; codigo: string }[]>>();

  getObrasSociales() {
    return this.obras.asObservable();
  }

  get(id: number) {
    const subject = new Subject<DataPackage<Paciente>>();
    this.fichas.push({ id, subject });
    return subject.asObservable();
  }

  mio() {
    const subject = new Subject<DataPackage<Paciente>>();
    this.propias.push(subject);
    return subject.asObservable();
  }

  get ficha(): Subject<DataPackage<Paciente>> {
    const ultima = this.fichas[this.fichas.length - 1];
    if (!ultima) {
      throw new Error('Todavía no se pidió la ficha');
    }
    return ultima.subject;
  }

  get propia(): Subject<DataPackage<Paciente>> {
    const ultima = this.propias[this.propias.length - 1];
    if (!ultima) {
      throw new Error('Todavía no se pidió la ficha propia');
    }
    return ultima;
  }
}

class AuthFalso {
  pacienteId: number | null = 12;
  role = 'OPERADOR';
  logoutLlamado = false;

  getCurrentPatientId(): number | null {
    return this.pacienteId;
  }

  getUserRole(): string {
    return this.role;
  }

  logout(): void {
    this.logoutLlamado = true;
  }
}

function paquete<T>(data: T): DataPackage<T> {
  return { data, message: '', status: 200, status_text: 'ok', status_code: 200 };
}

function paciente(parcial: Partial<Paciente> = {}): Paciente {
  return {
    id: 12,
    nombre: 'Ana',
    apellido: 'López',
    email: 'ana@turnero.com',
    telefono: '2991111111',
    dni: 30111222,
    fechaNacimiento: '1990-01-01',
    ...parcial
  };
}

describe('PacienteDetailComponent', () => {
  let fixture: ComponentFixture<PacienteDetailComponent>;
  let pacientes: PacienteFalso;
  let auth: AuthFalso;
  let ruta: { path: string; id: string | null; edit: boolean };

  function paramMap(): ParamMap {
    return convertToParamMap(ruta.id ? { id: ruta.id } : {});
  }

  async function montar(): Promise<void> {
    const query = convertToParamMap(ruta.edit ? { edit: 'true' } : {});
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PacienteDetailComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              routeConfig: { path: ruta.path },
              paramMap: paramMap(),
              queryParamMap: query
            },
            queryParamMap: of(query)
          }
        },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        { provide: PacienteService, useValue: pacientes },
        { provide: AuthService, useValue: auth },
        { provide: ModalService, useValue: { alert() {}, confirm: () => Promise.resolve() } },
        { provide: ToastService, useValue: { success() {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PacienteDetailComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    pacientes = new PacienteFalso();
    auth = new AuthFalso();
    ruta = { path: 'paciente-perfil', id: null, edit: false };
  });

  it('en el perfil no muestra el alta mientras la ficha está en camino', async () => {
    await montar();

    const texto = fixture.nativeElement.textContent as string;
    expect(fixture.componentInstance.cargando).toBe(true);
    expect(fixture.componentInstance.esNuevo()).toBe(false);
    expect(texto).toContain('Cargando ficha del paciente');
    expect(texto).not.toContain('Nuevo Paciente');
    expect(texto).not.toContain('Contraseña automática');
    expect(pacientes.fichas).toHaveLength(0);
    expect(pacientes.propias).toHaveLength(1);

    pacientes.propia.next(paquete(paciente()));
    fixture.detectChanges();

    const listo = fixture.nativeElement.textContent as string;
    expect(fixture.componentInstance.cargando).toBe(false);
    expect(fixture.componentInstance.esNuevo()).toBe(false);
    expect(listo).toContain('Ana López');
    expect(listo).toContain('Información del paciente');
    expect(listo).not.toContain('Nuevo Paciente');
    expect(listo).not.toContain('Contraseña automática');
    expect(auth.logoutLlamado).toBe(false);
  });

  it('con ?edit=true entra a edición recién cuando llega la ficha', async () => {
    ruta.edit = true;
    await montar();

    expect(fixture.nativeElement.textContent).not.toContain('Registro de nuevo paciente');

    pacientes.propia.next(paquete(paciente()));
    fixture.detectChanges();

    expect(fixture.componentInstance.modoEdicion).toBe(true);
    expect(fixture.componentInstance.esNuevo()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Editando información');
    expect(fixture.nativeElement.textContent).not.toContain('Contraseña automática');
  });

  it('un operador sin id local pide /me y el 404 no abre el alta', async () => {
    auth.pacienteId = null;
    await montar();

    expect(fixture.nativeElement.textContent).toContain('Cargando ficha del paciente');
    expect(fixture.nativeElement.textContent).not.toContain('Nuevo Paciente');
    expect(pacientes.fichas).toHaveLength(0);
    expect(pacientes.propias).toHaveLength(1);

    pacientes.propia.error(new HttpErrorResponse({
      status: 404,
      error: {
        status_text: 'Tu usuario puede ver el panel de paciente, pero no hay una ficha de paciente vinculada. Pedile a un administrador del centro que te asocie.'
      }
    }));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(fixture.componentInstance.sinFicha).toBe(true);
    expect(texto).toContain('Sin ficha de paciente');
    expect(texto).toContain('no hay una ficha de paciente vinculada');
    expect(texto).not.toContain('Nuevo Paciente');
    expect(texto).not.toContain('Contraseña automática');
    expect(auth.logoutLlamado).toBe(false);
  });

  it('un 404 del perfil propio se muestra como sin ficha y el reintento vuelve a pedirla', async () => {
    await montar();
    pacientes.propia.error(new HttpErrorResponse({
      status: 404,
      error: { status_code: 404, status_text: 'Paciente con id 12 no encontrado' }
    }));
    fixture.detectChanges();

    expect(fixture.componentInstance.sinFicha).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Sin ficha de paciente');
    expect(fixture.nativeElement.textContent).toContain('ficha de paciente vinculada');
    expect(fixture.nativeElement.textContent).not.toContain('Nuevo Paciente');

    fixture.componentInstance.reintentar();
    expect(fixture.componentInstance.cargando).toBe(true);
    expect(pacientes.propias.length).toBe(2);
    expect(pacientes.fichas).toHaveLength(0);

    pacientes.propia.next(paquete(paciente({ nombre: 'Luis', apellido: 'Pérez' })));
    fixture.detectChanges();

    expect(fixture.componentInstance.sinFicha).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Luis Pérez');
  });

  it('en /pacientes/:id espera la ficha y no abre el alta', async () => {
    ruta.path = 'pacientes/:id';
    ruta.id = '4';
    await montar();

    expect(fixture.nativeElement.textContent).toContain('Cargando ficha del paciente');
    expect(fixture.nativeElement.textContent).not.toContain('Nuevo Paciente');
    expect(pacientes.fichas.map(f => f.id)).toEqual([4]);

    pacientes.ficha.next(paquete(paciente({ id: 4, nombre: 'María', apellido: 'González' })));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('María González');
    expect(fixture.nativeElement.textContent).not.toContain('Registro de nuevo paciente');
  });

  it('un 404 en /pacientes/:id avisa que no está el paciente, sin abrir el alta', async () => {
    ruta.path = 'pacientes/:id';
    ruta.id = '9';
    await montar();

    pacientes.ficha.error(new HttpErrorResponse({ status: 404, error: {} }));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('No se encontró el paciente.');
    expect(texto).not.toContain('Nuevo Paciente');
    expect(texto).not.toContain('Sin ficha de paciente');
  });

  it('un error al abrir /pacientes/:id deja un mensaje y permite reintentar', async () => {
    ruta.path = 'pacientes/:id';
    ruta.id = '9';
    await montar();

    pacientes.ficha.error(new HttpErrorResponse({
      status: 500,
      error: { status_text: 'El servidor no pudo leer la ficha.' }
    }));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(fixture.componentInstance.cargando).toBe(false);
    expect(fixture.componentInstance.sinFicha).toBe(false);
    expect(texto).toContain('No se pudo cargar la ficha');
    expect(texto).toContain('El servidor no pudo leer la ficha.');
    expect(texto).toContain('Reintentar');
    expect(texto).not.toContain('Nuevo Paciente');

    fixture.componentInstance.reintentar();
    expect(pacientes.fichas.length).toBe(2);
  });

  it('la ruta de alta muestra el formulario sin esperar una ficha', async () => {
    ruta.path = 'pacientes/new';
    await montar();

    const texto = fixture.nativeElement.textContent as string;
    expect(fixture.componentInstance.cargando).toBe(false);
    expect(fixture.componentInstance.esNuevo()).toBe(true);
    expect(texto).toContain('Nuevo Paciente');
    expect(texto).toContain('Registro de nuevo paciente');
    expect(texto).toContain('Contraseña automática');
    expect(texto).not.toContain('Cargando ficha del paciente');
    expect(pacientes.fichas).toHaveLength(0);
  });
});
