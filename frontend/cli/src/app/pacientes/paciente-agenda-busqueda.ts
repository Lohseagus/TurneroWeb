export interface OpcionCatalogo {
  tipo: "especialidad" | "medico";
  etiqueta: string;
  clave: string;
  especialidad?: string;
  staffMedicoId?: number | null;
  medicoId?: number | null;
  centroId?: number | null;
}

export interface ResolucionBusqueda {
  modo: "vacio" | "catalogo" | "cliente" | "ambiguo";
  especialidad?: string;
  staffMedicoId?: number | null;
  textoCliente?: string;
}

export function normalizarBusqueda(valor: string | null | undefined): string {
  return (valor || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^(dra|dr|doctora|doctor)\.?\s+/, "");
}

export function numeroParam(valor: string | null | undefined): number | null {
  if (valor == null || valor === "") return null;
  const n = Number(valor);
  return Number.isInteger(n) && n > 0 ? n : null;
}

export function resolverBusquedaTexto(texto: string, catalogo: OpcionCatalogo[]): ResolucionBusqueda {
  const q = normalizarBusqueda(texto);
  if (!q) return { modo: "vacio" };

  const exactas = catalogo.filter((op) => normalizarBusqueda(op.etiqueta) === q);
  const exacta = elegirUnica(exactas);
  if (exacta) return desdeOpcion(exacta);
  if (exactas.length > 1) return { modo: "ambiguo", textoCliente: q };

  if (q.length >= 3) {
    const contiene = catalogo.filter((op) => normalizarBusqueda(op.etiqueta).includes(q));
    const unica = elegirUnica(contiene);
    if (unica) return desdeOpcion(unica);
    if (contiene.length > 1) return { modo: "ambiguo", textoCliente: q };
  }

  return { modo: "cliente", textoCliente: q };
}

export function sugerenciasCatalogo(texto: string, catalogo: OpcionCatalogo[], limite = 8): OpcionCatalogo[] {
  const q = normalizarBusqueda(texto);
  if (!q) return [];
  const vistos = new Set<string>();
  const salida: OpcionCatalogo[] = [];
  for (const op of catalogo) {
    if (!normalizarBusqueda(op.etiqueta).includes(q)) continue;
    if (vistos.has(op.clave)) continue;
    vistos.add(op.clave);
    salida.push(op);
    if (salida.length >= limite) break;
  }
  return salida;
}

function elegirUnica(opciones: OpcionCatalogo[]): OpcionCatalogo | null {
  if (opciones.length === 1) return opciones[0];
  if (opciones.length === 0) return null;
  const especialidades = opciones.filter((op) => op.tipo === "especialidad");
  const conStaff = opciones.filter((op) => op.tipo === "medico" && op.staffMedicoId);
  if (especialidades.length === 1 && conStaff.length === 0) return especialidades[0];
  if (conStaff.length === 1 && especialidades.length === 0) return conStaff[0];
  return null;
}

function desdeOpcion(op: OpcionCatalogo): ResolucionBusqueda {
  if (op.tipo === "especialidad") {
    return { modo: "catalogo", especialidad: op.especialidad || op.etiqueta, staffMedicoId: null, textoCliente: "" };
  }
  if (op.staffMedicoId) {
    return { modo: "catalogo", staffMedicoId: op.staffMedicoId, textoCliente: "" };
  }
  return {
    modo: "catalogo",
    especialidad: op.especialidad,
    staffMedicoId: null,
    textoCliente: normalizarBusqueda(op.etiqueta),
  };
}
