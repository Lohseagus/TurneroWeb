import { describe, expect, it } from "vitest";
import {
  DIAS_CORTO,
  accionesDe,
  armarGrilla,
  etiquetaDia,
  normalizarFeriado,
  normalizarTurno,
  rangoMesVisible,
  tituloMes,
  type FeriadoGrilla,
  type TurnoGrilla,
} from "./calendario-mes";

function turno(fecha: string, hora: string, estado: string, id: number): TurnoGrilla {
  return {
    id,
    fecha,
    horaInicio: hora,
    estado,
    pacienteNombre: "Ana",
    pacienteApellido: "Paz",
    pacienteId: 4,
  };
}

describe("grilla mensual", () => {
  it("arranca en lunes, termina en domingo y usa etiquetas en español", () => {
    expect(DIAS_CORTO).toEqual(["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"]);
    expect(tituloMes(2026, 11)).toBe("noviembre 2026");
    expect(etiquetaDia("2026-11-02")).toBe("Lun 2 de noviembre");

    const noviembre = rangoMesVisible(2026, 11);
    expect(noviembre).toEqual({ desde: "2026-10-26", hasta: "2026-12-06" });

    const febrero = rangoMesVisible(2026, 2);
    expect(febrero.desde).toBe("2026-01-26");
    expect(febrero.hasta).toBe("2026-03-01");
  });

  it("cuenta el día, muestra los primeros turnos y marca feriados de un día o de un tramo", () => {
    const turnos = [
      turno("2026-11-02", "11:00", "AUSENTE", 3),
      turno("2026-11-02", "09:00", "PROGRAMADO", 1),
      turno("2026-11-02", "10:00", "CONFIRMADO", 2),
      turno("2026-11-02", "12:00", "CANCELADO", 4),
    ];
    const feriados: FeriadoGrilla[] = [
      { id: 1, fecha: "2026-11-20", fechaFin: "2026-11-21", descripcion: "Puente", centroAtencionId: null },
      { id: 2, fecha: "2026-11-02", fechaFin: null, descripcion: "Feriado del centro", centroAtencionId: 5 },
    ];

    const dias = armarGrilla(2026, 11, "2026-11-24", turnos, feriados);

    expect(dias).toHaveLength(42);
    expect(dias[0].fecha).toBe("2026-10-26");
    expect(dias[0].esMesActual).toBe(false);
    expect(dias[6].fecha).toBe("2026-11-01");
    expect(dias[6].esMesActual).toBe(true);
    expect(dias.find((dia) => dia.fecha === "2026-11-24")?.esHoy).toBe(true);

    const dos = dias.find((dia) => dia.fecha === "2026-11-02");
    expect(dos?.total).toBe(4);
    expect(dos?.primeros.map((item) => item.id)).toEqual([1, 2, 3]);
    expect(dos?.feriado).toBe(true);
    expect(dos?.feriadoTexto).toBe("Feriado del centro");

    expect(dias.find((dia) => dia.fecha === "2026-11-20")?.feriado).toBe(true);
    expect(dias.find((dia) => dia.fecha === "2026-11-21")?.feriadoTexto).toBe("Puente");
    expect(dias.find((dia) => dia.fecha === "2026-11-22")?.feriado).toBe(false);
    expect(dias.find((dia) => dia.fecha === "2026-11-03")?.total).toBe(0);
    expect(dias.find((dia) => dia.fecha === "2026-11-02")?.turnos).toHaveLength(4);
  });

  it("entiende la fecha dd-MM-yyyy que devuelve el backend", () => {
    const turno = normalizarTurno({
      id: 8,
      fecha: "02-11-2026",
      horaInicio: "09:30:00",
      estado: "COMPLETO",
    });
    const feriado = normalizarFeriado({
      id: 1,
      fecha: "20-11-2026",
      fechaFin: "21-11-2026",
      descripcion: "Puente",
    });
    expect(turno.fecha).toBe("2026-11-02");
    expect(turno.horaInicio).toBe("09:30");
    const dias = armarGrilla(2026, 11, "2026-11-24", [turno], [feriado]);
    expect(dias.find((dia) => dia.fecha === "2026-11-02")?.total).toBe(1);
    expect(dias.find((dia) => dia.fecha === "2026-11-21")?.feriado).toBe(true);
  });

  it("ofrece las acciones que el rol ya tiene y deja al paciente en solo lectura", () => {
    expect(accionesDe("paciente", "PROGRAMADO", "2026-11-02", "2026-11-24", 4)).toEqual({
      confirmar: false,
      reagendar: false,
      cancelar: false,
      ver: false,
      consulta: false,
      historia: false,
      asistencia: false,
    });

    expect(accionesDe("staff", "PROGRAMADO", "2026-11-02", "2026-11-24", 4)).toMatchObject({
      confirmar: true,
      reagendar: true,
      cancelar: true,
      ver: true,
      consulta: false,
    });
    expect(accionesDe("staff", "CONFIRMADO", "2026-11-02", "2026-11-24", 4).confirmar).toBe(false);
    expect(accionesDe("staff", "CANCELADO", "2026-11-02", "2026-11-24", 4).cancelar).toBe(false);
    expect(accionesDe("staff", "COMPLETO", "2026-11-02", "2026-11-24", 4).reagendar).toBe(false);

    const medico = accionesDe("medico", "CONFIRMADO", "2026-11-24", "2026-11-24", 4);
    expect(medico.consulta).toBe(true);
    expect(medico.historia).toBe(true);
    expect(medico.asistencia).toBe(true);
    expect(medico.confirmar).toBe(false);
    expect(accionesDe("medico", "CONFIRMADO", "2026-11-25", "2026-11-24", 4).asistencia).toBe(false);
    expect(accionesDe("medico", "CANCELADO", "2026-11-24", "2026-11-24", null).consulta).toBe(false);
    expect(accionesDe("medico", "REAGENDADO", "2026-11-24", "2026-11-24", 4).asistencia).toBe(false);
  });
});
