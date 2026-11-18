import { Component, OnInit, ChangeDetectionStrategy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { PacienteService } from "./paciente.service";
import { ListaEspera } from "../lista-espera/lista-espera.model";
import { ModalService } from "../modal/modal.service";
import { timeout } from "rxjs/operators";
import { mensajeDeCarga } from "./mensaje-carga";

@Component({
  selector: "app-paciente-lista-espera",
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: "./paciente-lista-espera.component.html",
  styleUrl: "./paciente-portal.css",
})
export class PacienteListaEsperaComponent implements OnInit {
  solicitudes: ListaEspera[] = [];
  cargando = false;
  error = "";
  cancelandoId: number | null = null;

  constructor(
    private pacienteService: PacienteService,
    private modalService: ModalService
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  get pendientes(): ListaEspera[] {
    return this.solicitudes.filter((solicitud) => solicitud.estado === "PENDIENTE");
  }

  cargar(): void {
    this.cargando = true;
    this.error = "";
    this.pacienteService.misSolicitudesEspera().pipe(timeout(20000)).subscribe({
      next: (paquete) => {
        this.solicitudes = paquete.data || [];
        this.cargando = false;
      },
      error: (error) => {
        this.cargando = false;
        this.error = mensajeDeCarga(error, "No se pudo cargar la lista de espera.");
      },
    });
  }

  cancelar(solicitud: ListaEspera): void {
    this.modalService
      .confirm(
        "Cancelar lista de espera",
        "Cancelar solicitud",
        `¿Cancelás la solicitud de ${solicitud.especialidadNombre || "esta especialidad"}?`
      )
      .then(() => {
        this.cancelandoId = solicitud.id;
        this.pacienteService.cancelarSolicitudEspera(solicitud.id).subscribe({
          next: () => {
            this.cancelandoId = null;
            this.cargar();
          },
          error: (err) => {
            this.cancelandoId = null;
            this.error = err?.error?.status_text || "No se pudo cancelar la solicitud";
          },
        });
      })
      .catch(() => undefined);
  }
}
