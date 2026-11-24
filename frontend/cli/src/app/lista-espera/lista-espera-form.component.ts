import { Component, EventEmitter, Input, OnInit, Output, ChangeDetectionStrategy } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule, FormControl } from '@angular/forms';
import { ListaEspera } from './lista-espera.model';
import { PacienteService } from '../pacientes/paciente.service';
import { EspecialidadService } from '../especialidades/especialidad.service';
import { CentroAtencionService } from '../centrosAtencion/centroAtencion.service';
import { MedicoService } from '../medicos/medico.service';
import { DataPackage } from '../data.package';
import { AuthService, Role } from '../inicio-sesion/auth.service';
import { UserContextService } from '../services/user-context.service';
import { ListaEsperaService } from './lista-espera.service';
import { Router } from '@angular/router';
import { ModalService } from '../modal/modal.service';
import { ToastService } from '../services/toast.service';
import { PacienteSesionService } from '../pacientes/paciente-sesion.service';


@Component({
  selector: 'app-lista-espera-form',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule],
  templateUrl: './lista-espera-form.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./lista-espera-form.component.css']
})
export class ListaEsperaFormComponent implements OnInit {
  @Input() data?: ListaEspera | null;
  @Input() operatorContext: boolean = false;
  @Input() especialidadNombre: string | null = null;
  @Input() centroId: number | null = null;

  @Output() save = new EventEmitter<ListaEspera>();
  @Output() cancel = new EventEmitter<void>();

  form: FormGroup;
  pacientes: any[] = [];
  especialidades: any[] = [];
  centros: any[] = [];
  medicos: any[] = [];
  limite: number = 0;
  
  /**
   * 🔐 isPatientMode: Determina si el campo paciente debe estar bloqueado y autocompleto.
   * 
   * Se calcula en base a dos factores:
   * 
   * - TRUE (campo bloqueado) cuando:
   *   1. NO está en contexto de operador (operatorContext === false) Y
   *   2. El usuario tiene paciente asociado (getCurrentPatientId() !== null) Y
   *   3. NO se está editando una solicitud existente (data === null)
   * 
   * - FALSE (campo de búsqueda) cuando:
   *   1. Está en contexto de operador (operatorContext === true) O
   *   2. El usuario NO tiene paciente asociado O
   *   3. Se está editando una solicitud existente
   * 
   * Casos de uso:
   * - Panel Paciente + Paciente asociado → Bloqueado
   * - Panel Paciente + Admin/Médico con paciente → Bloqueado
   * - Panel Operador (cualquier usuario) → Búsqueda habilitada
   */
  isPatientMode: boolean = false;
  
  nivelesUrgencia = [
    { value: 'BAJA', label: 'Baja' },
    { value: 'MEDIA', label: 'Media' },
    { value: 'ALTA', label: 'Alta' },
    { value: 'URGENTE', label: 'Urgente' }
  ];

  // Búsqueda de pacientes
  searchTerm: string = '';
  pacientesBuscados: any[] = [];
  buscandoPacientes: boolean = false;
  pacienteSeleccionado: any = null;
  showSearchResults: boolean = false;

  constructor(
    private fb: FormBuilder,
    private pacienteService: PacienteService,
    private especialidadService: EspecialidadService,
    private centroService: CentroAtencionService,
    private medicoService: MedicoService,
    private authService: AuthService, // Inyectar AuthService
    private userContextService: UserContextService, // Inyectar UserContextService para acceder al primaryRole
    private listaEsperaService: ListaEsperaService, // Inyectar para manejar create/update directamente en modo paciente
    private router: Router, // Inyectar para redirigir después de guardar
    private modalService: ModalService, // Inyectar si el form se usa en modal (opcional, si aplica)
    private toastService: ToastService, // Servicio de notificaciones toast
    private sesion: PacienteSesionService
  ) {
    this.form = this.fb.group({
      pacienteId: ['', Validators.required],
      especialidadId: ['', Validators.required],
      centroAtencionId: ['', Validators.required],
      medicoId: [''],
      fechaDeseadaDesde: [''],
      fechaDeseadaHasta: [''],
      urgenciaMedica: ['BAJA', Validators.required] // Valor por defecto: BAJA
    });
  }

  private pacienteIdResuelto: number | null = null;

  ngOnInit() {
    this.aplicarModo(this.authService.getCurrentPatientId());
    // El storage del login no existe cuando un médico entra a la vista de paciente.
    if (!this.operatorContext && !this.data && this.pacienteIdResuelto == null) {
      this.sesion.id().subscribe({
        next: (id) => {
          if (id) {
            this.aplicarModo(id);
          }
        },
        error: () => {
          // El formulario sigue usable para el personal que busca un paciente.
        }
      });
    }
  }

  private aplicarModo(pacienteId: number | null): void {
    this.pacienteIdResuelto = pacienteId;
    this.isPatientMode = (
      !this.operatorContext &&
      pacienteId !== null &&
      !this.data
    );
    this.initForm();
    this.cargarDatos();
    if (this.data) {
      const patch = { ...this.data } as any;
      if (patch.fechaDeseadaDesde) patch.fechaDeseadaDesde = this.normalizeDate(patch.fechaDeseadaDesde);
      if (patch.fechaDeseadaHasta) patch.fechaDeseadaHasta = this.normalizeDate(patch.fechaDeseadaHasta);
      if (patch.medicoPreferidoId !== undefined) {
        patch.medicoId = patch.medicoPreferidoId;
      }
      this.form.patchValue(patch);
    } else if (this.isPatientMode) {
      this.precargarPacienteActual();
    }
  }
  /** ✅ Inicializa el formulario con el estado correcto (sin usar [disabled] en el HTML) */
  private initForm() {
    this.form = this.fb.group({
      pacienteId: new FormControl(
        { value: '', disabled: this.isPatientMode }, // 👈 Controla el “disabled” desde aquí
        Validators.required
      ),
      especialidadId: ['', Validators.required],
      centroAtencionId: ['', Validators.required],
      medicoId: [''],
      fechaDeseadaDesde: [''],
      fechaDeseadaHasta: [''],
      urgenciaMedica: ['BAJA', Validators.required] // Valor por defecto: BAJA
    });
  }


  cargarDatos() {
    // Pacientes: NO cargar todos, solo buscar bajo demanda
    if (!this.isPatientMode) {
      this.pacientes = []; // Inicialmente vacío, se llenarán al buscar
    } else {
      this.pacientes = [];
    }

    // Especialidades
    this.especialidadService.all().subscribe({
      next: (res: DataPackage<any[]>) => {
        this.especialidades = res && res.data ? res.data : [];
        this.precargarFiltros();
      },
      error: (err) => {
        console.error('Error cargando especialidades:', err);
        this.especialidades = [];
      }
    });

    // Centros de Atención
    this.centroService.all().subscribe({
      next: (res: DataPackage<any[]>) => {
        this.centros = res && res.data ? res.data : [];
        this.precargarFiltros();
      },
      error: (err) => {
        console.error('Error cargando centros:', err);
        try {
          (this.centroService as any).getAll()?.subscribe?.((r: any) => {
            this.centros = r && r.data ? r.data : r || [];
          });
        } catch (e) {
          this.centros = [];
        }
      }
    });

    // Médicos
    this.medicoService.getAll().subscribe({
      next: (res: DataPackage<any[]>) => {
        this.medicos = res && res.data ? res.data : [];
      },
      error: (err) => {
        console.error('Error cargando medicos:', err);
        this.medicos = [];
      }
    });
  }

  /** Precarga el paciente actual en el formulario en modo paciente */
  private precargarPacienteActual() {
    const currentUserId = this.pacienteIdResuelto;
    if (!currentUserId) {
      console.error('No se pudo obtener el ID del paciente actual');
      return;
    }

    this.pacienteService.mio().subscribe({
      next: (res: DataPackage<any>) => {
        const pacienteActual = res.data;
        if (!pacienteActual) {
          console.error('No se encontró la ficha del paciente');
          return;
        }

        this.pacientes = [pacienteActual];
        this.form.patchValue({
          pacienteId: pacienteActual.id
        });
      },
      error: (err) => console.error('Error cargando la ficha del paciente:', err)
    });
  }




  guardar() {
    if (this.form.valid) {
      // 🔧 FIX CRÍTICO: Usar getRawValue() en lugar de value
      // getRawValue() incluye campos deshabilitados (como pacienteId en modo paciente)
      const formValue = this.form.getRawValue();

      console.log('📝 Form Value (con campos deshabilitados):', formValue);

      const solicitud: ListaEspera = {
        ...(this.data || {}),
        ...formValue,
        id: this.data?.id || undefined,
        medicoPreferidoId: formValue.medicoId || null,
        medicoPreferidoNombre: formValue.medicoId ?
          this.medicos.find(m => m.id === parseInt(formValue.medicoId))?.nombre + ' ' +
          this.medicos.find(m => m.id === parseInt(formValue.medicoId))?.apellido :
          null
      } as ListaEspera;


      if (this.isPatientMode) {
        this.listaEsperaService.create(solicitud).subscribe({
          next: (resp) => {
            if (resp.status_code === 200) {
              const puesto = resp.data?.posicion ? ` Quedaste en el puesto ${resp.data.posicion}.` : '';
              this.toastService.success(
                `Te anotamos en la lista de espera.${puesto} La urgencia la define el centro.`
              );
              this.router.navigate(['/paciente-dashboard']);
            } else {
              this.toastService.error(resp.status_text || 'No se pudo anotar en la lista de espera.');
            }
          },
          error: (err) => {
            this.toastService.error(err?.error?.status_text || 'No se pudo anotar en la lista de espera. Intentá de nuevo.');
          }
        });
      } else {
        // Modo operador/admin: emitir evento para que el padre lo maneje
        this.save.emit(solicitud);
      }
    } else {
      this.toastService.error('Completá especialidad y centro para anotarte.');

      // Mostrar qué campos están inválidos
      Object.keys(this.form.controls).forEach(key => {
        const control = this.form.get(key);
        if (control?.invalid) {
          console.warn(`  - Campo inválido: ${key}`, control.errors);
        }
      });
    }
  }

  cerrar() {
    this.cancel.emit();
  }

  private precargarFiltros(): void {
    if (this.data) {
      return;
    }
    const parche: { especialidadId?: number; centroAtencionId?: number } = {};
    if (this.centroId && !this.form.get('centroAtencionId')?.value) {
      parche.centroAtencionId = Number(this.centroId);
    }
    if (this.especialidadNombre && !this.form.get('especialidadId')?.value) {
      const buscada = this.especialidadNombre.trim().toLowerCase();
      const encontrada = this.especialidades.find((esp) => (esp.nombre || '').trim().toLowerCase() === buscada);
      if (encontrada) {
        parche.especialidadId = encontrada.id;
      }
    }
    if (Object.keys(parche).length) {
      this.form.patchValue(parche);
    }
  }

  // Utility: normaliza valores de fecha (Date o string) a yyyy-MM-dd (valor aceptado por input[type=date])
  private normalizeDate(value: any): string | null {
    if (!value) return null;
    if (value instanceof Date) {
      const d = value as Date;
      return d.toISOString().substring(0, 10);
    }
    // si viene como string en formato ISO u otro, intentar parsear
    const parsed = new Date(value);
    if (!isNaN(parsed.getTime())) {
      return parsed.toISOString().substring(0, 10);
    }
    return null;
  }


  /**
   * Busca pacientes por nombre, apellido o DNI con debounce
   */
  onSearchPaciente(event: any) {
    const term = event.target.value?.trim() || '';
    this.searchTerm = term;

    if (term.length < 2) {
      this.pacientesBuscados = [];
      this.showSearchResults = false;
      return;
    }

    this.buscandoPacientes = true;
    this.showSearchResults = true;

    // 🔍 Determinar si el término es un número (DNI) o texto (nombre/apellido)
    const esNumero = /^\d+$/.test(term);

    const filtros: any = {};

    if (esNumero) {
      // Si es número, buscar por documento (DNI)
      filtros.documento = term;
    } else {
      // Si es texto, buscar por nombre/apellido
      filtros.nombreApellido = term;
    }

    // Buscar usando el método byPageAdvanced del servicio
    this.pacienteService.byPageAdvanced(
      1, // página 1
      10, // máximo 10 resultados
      filtros,
      'apellido', // ordenar por apellido
      'asc'
    ).subscribe({
      next: (res: DataPackage<any>) => {
        this.buscandoPacientes = false;
        if (res && res.data && res.data.content) {
          this.pacientesBuscados = res.data.content;
        } else {
          this.pacientesBuscados = [];
        }
      },
      error: (err) => {
        console.error('Error buscando pacientes:', err);
        this.buscandoPacientes = false;
        this.pacientesBuscados = [];
      }
    });
  }
  /**
   * Selecciona un paciente de los resultados de búsqueda
   */
  seleccionarPaciente(paciente: any) {
    this.pacienteSeleccionado = paciente;
    this.searchTerm = `${paciente.nombre} ${paciente.apellido} - DNI: ${paciente.dni}`;
    this.showSearchResults = false;
    this.pacientesBuscados = [];

    // Actualizar el valor del formulario
    this.form.patchValue({
      pacienteId: paciente.id
    });
  }

  /**
   * Limpia la selección de paciente
   */
  limpiarSeleccionPaciente() {
    this.pacienteSeleccionado = null;
    this.searchTerm = '';
    this.pacientesBuscados = [];
    this.showSearchResults = false;

    this.form.patchValue({
      pacienteId: ''
    });
  }

  /**
   * Oculta los resultados cuando se pierde el foco
   */
  onBlurSearch() {
    // Delay para permitir clicks en los resultados
    setTimeout(() => {
      this.showSearchResults = false;
    }, 200);
  }

  /**
   * Muestra los resultados cuando se enfoca el input
   */
  onFocusSearch() {
    if (this.searchTerm.length >= 2 && this.pacientesBuscados.length > 0) {
      this.showSearchResults = true;
    }
  }
}