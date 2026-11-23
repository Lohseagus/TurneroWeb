import { describe, expect, it } from "vitest";
import {
  normalizarBusqueda,
  numeroParam,
  resolverBusquedaTexto,
  sugerenciasCatalogo,
  type OpcionCatalogo,
} from "./paciente-agenda-busqueda";

const catalogo: OpcionCatalogo[] = [
  { tipo: "especialidad", etiqueta: "Cardiología", clave: "esp:1", especialidad: "Cardiología" },
  { tipo: "especialidad", etiqueta: "Pediatría", clave: "esp:2", especialidad: "Pediatría" },
  {
    tipo: "medico",
    etiqueta: "Ana Pérez",
    clave: "medico:9",
    medicoId: 9,
    especialidad: "Cardiología",
  },
  {
    tipo: "medico",
    etiqueta: "Luis Gómez",
    clave: "staff:3",
    staffMedicoId: 3,
    especialidad: "Pediatría",
  },
];

describe("búsqueda de Pedir turno", () => {
  it("normaliza acentos, espacios y el prefijo doctor", () => {
    expect(normalizarBusqueda("  Dr. Cardiología  ")).toBe("cardiologia");
    expect(normalizarBusqueda("Doctora Ana")).toBe("ana");
    expect(numeroParam("4")).toBe(4);
    expect(numeroParam("0")).toBeNull();
    expect(numeroParam("no")).toBeNull();
  });

  it("elige la especialidad del catálogo aunque falte el acento", () => {
    expect(resolverBusquedaTexto("cardiologia", catalogo)).toEqual({
      modo: "catalogo",
      especialidad: "Cardiología",
      staffMedicoId: null,
      textoCliente: "",
    });
  });

  it("elige al médico con id de staff y, si no hay staff, deja el nombre para filtrar", () => {
    expect(resolverBusquedaTexto("luis gomez", catalogo).staffMedicoId).toBe(3);
    const ana = resolverBusquedaTexto("Ana Pérez", catalogo);
    expect(ana.modo).toBe("catalogo");
    expect(ana.staffMedicoId).toBeNull();
    expect(ana.especialidad).toBe("Cardiología");
    expect(ana.textoCliente).toBe("ana perez");
  });

  it("no llama a un filtro de catálogo si el texto no coincide o hay varias opciones", () => {
    expect(resolverBusquedaTexto("zzzz", catalogo).modo).toBe("cliente");
    expect(resolverBusquedaTexto("a", catalogo).modo).toBe("cliente");
    expect(resolverBusquedaTexto("", catalogo).modo).toBe("vacio");
    const varias: OpcionCatalogo[] = [
      { tipo: "medico", etiqueta: "Ana López", clave: "medico:1", medicoId: 1 },
      { tipo: "medico", etiqueta: "Ana Ruiz", clave: "medico:2", medicoId: 2 },
    ];
    expect(resolverBusquedaTexto("ana", varias).modo).toBe("ambiguo");
  });

  it("arma sugerencias a partir del catálogo", () => {
    expect(sugerenciasCatalogo("ped", catalogo).map((op) => op.etiqueta)).toEqual(["Pediatría"]);
    expect(sugerenciasCatalogo("", catalogo)).toEqual([]);
  });
});
