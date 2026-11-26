import { describe, expect, it } from "vitest";
import {
  contarFiltros,
  esOrigenSistema,
  etiquetaDe,
  etiquetaOrigen,
  ROLES_AUDITORIA,
} from "./audit-ui";

describe("textos de auditoría", () => {
  it("traduce el rol y distingue sistema de usuario", () => {
    expect(etiquetaDe(ROLES_AUDITORIA, "MEDICO")).toBe("Médico");
    expect(etiquetaOrigen("SISTEMA")).toBe("Sistema");
    expect(etiquetaOrigen(null, "SISTEMA")).toBe("Sistema");
    expect(etiquetaOrigen(null, "ana@turnero.com")).toBe("Usuario");
    expect(etiquetaOrigen("USUARIO", "ana@turnero.com")).toBe("Usuario");
    expect(esOrigenSistema("SISTEMA")).toBe(true);
    expect(esOrigenSistema("USUARIO")).toBe(false);
  });

  it("cuenta solo los filtros con valor", () => {
    expect(contarFiltros({})).toBe(0);
    expect(contarFiltros({
      rol: "OPERADOR",
      usuario: "  ",
      entidad: "TURNO",
      accion: "",
      origen: "SISTEMA",
      desde: "2026-01-01",
      hasta: "",
    })).toBe(4);
  });
});
