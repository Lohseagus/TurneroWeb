import { redirigirPortal } from "./paciente-portal";

describe("redirecciones del portal de paciente", () => {
  it("manda el historial a turnos pasados y la historia clínica a la línea de tiempo", () => {
    expect(redirigirPortal("paciente-historial")({ queryParams: {} })).toBe("paciente-turnos?tab=pasados");
    expect(redirigirPortal("paciente-historia-clinica")({ queryParams: {} })).toBe("paciente-salud?tab=timeline");
    expect(redirigirPortal("paciente-recetas")({ queryParams: {} })).toBe("paciente-salud?tab=recetas");
  });

  it("manda perfil, horarios y notificaciones a Mi cuenta, conservando la consulta", () => {
    expect(redirigirPortal("paciente-perfil")({ queryParams: { edit: "true" } })).toBe(
      "paciente-cuenta/perfil?edit=true"
    );
    expect(redirigirPortal("paciente-preferencias")({ queryParams: {} })).toBe("paciente-cuenta/horarios");
    expect(redirigirPortal("paciente-notificaciones")({ queryParams: {} })).toBe(
      "paciente-cuenta/notificaciones"
    );
  });

  it("abre Pedir turno con la lista de espera", () => {
    expect(redirigirPortal("lista-espera-form")({ queryParams: { centroId: "4" } })).toBe(
      "paciente-agenda?centroId=4&espera=1"
    );
  });
});
