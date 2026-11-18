import { Component, OnDestroy, OnInit, ChangeDetectionStrategy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router } from "@angular/router";
import { Subscription } from "rxjs";
import { PacienteHceComponent } from "../historia-clinica/paciente-hce.component";
import { PacienteRecetasComponent } from "../historia-clinica/paciente-recetas.component";

type TabSalud = "timeline" | "recetas" | "perfil" | "pdf";

@Component({
  selector: "app-paciente-salud",
  standalone: true,
  imports: [CommonModule, PacienteHceComponent, PacienteRecetasComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: "./paciente-salud.component.html",
  styleUrl: "./paciente-portal.css",
})
export class PacienteSaludComponent implements OnInit, OnDestroy {
  tab: TabSalud = "timeline";
  private sub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub = this.route.queryParamMap.subscribe((params) => {
      const pedido = params.get("tab");
      this.tab = pedido === "recetas" || pedido === "perfil" || pedido === "pdf" ? pedido : "timeline";
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  ir(tab: TabSalud): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: "merge",
    });
  }

  get seccionHce(): "timeline" | "perfil" | "pdf" {
    if (this.tab === "perfil" || this.tab === "pdf") return this.tab;
    return "timeline";
  }
}
