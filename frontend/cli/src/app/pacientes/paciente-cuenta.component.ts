import { Component } from "@angular/core";
import { RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";

@Component({
  selector: "app-paciente-cuenta",
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: "./paciente-cuenta.component.html",
  styleUrl: "./paciente-portal.css",
})
export class PacienteCuentaComponent {}
