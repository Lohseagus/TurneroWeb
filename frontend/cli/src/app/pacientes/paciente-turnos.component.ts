import { Component, OnDestroy, OnInit, ChangeDetectionStrategy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { Subscription } from "rxjs";
import { PacienteDashboardComponent } from "./paciente-dashboard.component";
import { HistorialTurnosComponent } from "../turnos/historial-turnos.component";
import { PacienteListaEsperaComponent } from "./paciente-lista-espera.component";

@Component({
  selector: "app-paciente-turnos",
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    PacienteDashboardComponent,
    HistorialTurnosComponent,
    PacienteListaEsperaComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: "./paciente-turnos.component.html",
  styleUrl: "./paciente-portal.css",
})
export class PacienteTurnosComponent implements OnInit, OnDestroy {
  tab: "proximos" | "pasados" = "proximos";
  private sub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub = this.route.queryParamMap.subscribe((params) => {
      this.tab = params.get("tab") === "pasados" ? "pasados" : "proximos";
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  ir(tab: "proximos" | "pasados"): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: "merge",
    });
  }
}
