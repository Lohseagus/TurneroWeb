package unpsjb.labprog.backend.business.service;

import java.time.LocalDateTime;
import java.util.Comparator;

import unpsjb.labprog.backend.model.ListaEspera;
import unpsjb.labprog.backend.model.Paciente;

/**
 * Único orden de la lista de espera, en los listados y al reasignar un hueco:
 * urgencia (mayor primero), fecha de ingreso (más antigua primero) y, si empatan,
 * reputación del paciente (mayor primero). La reputación nula cuenta como 0.
 */
public final class OrdenListaEspera {

    public static final Comparator<ListaEspera> COMPARADOR = OrdenListaEspera::comparar;

    private OrdenListaEspera() {
    }

    public static int comparar(ListaEspera a, ListaEspera b) {
        int porUrgencia = Integer.compare(prioridad(b), prioridad(a));
        if (porUrgencia != 0) {
            return porUrgencia;
        }
        int porIngreso = ingreso(a).compareTo(ingreso(b));
        if (porIngreso != 0) {
            return porIngreso;
        }
        return Integer.compare(reputacion(b), reputacion(a));
    }

    public static int prioridad(ListaEspera solicitud) {
        if (solicitud == null || solicitud.getUrgenciaMedica() == null) {
            return 0;
        }
        return switch (solicitud.getUrgenciaMedica()) {
            case URGENTE -> 4;
            case ALTA -> 3;
            case MEDIA -> 2;
            case BAJA -> 1;
        };
    }

    public static int reputacion(ListaEspera solicitud) {
        if (solicitud == null) {
            return 0;
        }
        return reputacion(solicitud.getPaciente());
    }

    public static int reputacion(Paciente paciente) {
        if (paciente == null || paciente.getReputacion() == null) {
            return 0;
        }
        return paciente.getReputacion();
    }

    private static LocalDateTime ingreso(ListaEspera solicitud) {
        if (solicitud == null || solicitud.getFechaSolicitud() == null) {
            return LocalDateTime.MAX;
        }
        return solicitud.getFechaSolicitud();
    }
}
