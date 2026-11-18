import { Component, ChangeDetectionStrategy } from "@angular/core";
import { RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";

@Component({
  selector: "app-paciente-cuenta",
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: "./paciente-cuenta.component.html",
  styleUrl: "./paciente-portal.css",
})
export class PacienteCuentaComponent {}
