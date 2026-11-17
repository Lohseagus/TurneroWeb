package unpsjb.labprog.backend.model.hce;

/**
 * Listas II a IV de psicofármacos y estupefacientes.
 * NINGUNA es un medicamento de receta común.
 * La clasificación de semilla es una lista de seguridad local, no el padrón oficial de ANMAT.
 */
public enum ListaControlada {
    NINGUNA,
    II,
    III,
    IV
}
