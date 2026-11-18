import { Component, OnInit } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";

/**
 * Rutas del portal de paciente unificado y a dónde caen las direcciones viejas
 * (mails, favoritos, Cucumber).
 */
export const PORTAL_PACIENTE = {
  inicio: "/paciente-dashboard",
  turnos: "/paciente-turnos",
  salud: "/paciente-salud",
  cuenta: "/paciente-cuenta",
  pedirTurno: "/paciente-agenda",
} as const;

const DESTINOS: Record<string, { base: string; forzar?: Record<string, string> }> = {
  "paciente-historial": { base: "paciente-turnos", forzar: { tab: "pasados" } },
  "paciente-historia-clinica": { base: "paciente-salud", forzar: { tab: "timeline" } },
  "paciente-recetas": { base: "paciente-salud", forzar: { tab: "recetas" } },
  "paciente-notificaciones": { base: "paciente-cuenta/notificaciones" },
  "paciente-perfil": { base: "paciente-cuenta/perfil" },
  "paciente-preferencias": { base: "paciente-cuenta/horarios" },
  "lista-espera-form": { base: "paciente-agenda", forzar: { espera: "1" } },
};

export function redirigirPortal(path: string, extra: Record<string, string> = {}) {
  return (datos: { queryParams?: Record<string, unknown> }) => {
    const destino = DESTINOS[path];
    const params = new URLSearchParams();
    const actuales = datos?.queryParams ?? {};
    for (const [clave, valor] of Object.entries(actuales)) {
      if (valor == null || valor === "") continue;
      params.set(clave, String(valor));
    }
    for (const [clave, valor] of Object.entries({ ...extra, ...(destino?.forzar ?? {}) })) {
      params.set(clave, valor);
    }
    const base = destino?.base ?? path;
    const consulta = params.toString();
    return consulta ? `${base}?${consulta}` : base;
  };
}

/**
 * La ruta vieja de lista de espera queda detrás del portal: sin ficha se ve
 * el vacío y no se llega a Pedir turno. Con ficha, cae en la agenda.
 */
@Component({
  selector: "app-lista-espera-entrada",
  standalone: true,
  template: "",
})
export class ListaEsperaEntradaComponent implements OnInit {
  constructor(
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.router.navigate(["/paciente-agenda"], {
      queryParams: { ...this.route.snapshot.queryParams, espera: "1" },
      replaceUrl: true,
    });
  }
}
